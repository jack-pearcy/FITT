#!/usr/bin/env bash
# Exercises backup-offsite.sh against the LOCAL compose stack, with a folder on
# this machine standing in for the storage provider.
#
# Usage, from the repository root, with the local database up and healthy
# (docker compose up -d mysql):
#
#   bash deploy/test-backup-offsite.sh "$PWD" "$(mktemp -d)"
#
# rclone does not need to be installed: the test runs it from the official
# rclone image, through a small wrapper it writes into the work directory.
#
# Read the output: every case prints "ok" or "FAIL" with what it saw. It stops
# and restarts the local database once, and deletes nothing outside the work
# directory. Exit 0 when every case passed.
REPO="$1"
WORK="$(cd "$2" && pwd -P)"
cd "$REPO" || exit 2

export DEPLOY_DIR="$REPO" COMPOSE_FILE=docker-compose.yml BACKUP_DIR="$WORK/backups"
export KEEP_NIGHTLY_LOCAL=2 KEEP_REMOTE_DAYS=30
export RCLONE_CONFIG="$WORK/rclone.conf"
STORE="$WORK/provider"
mkdir -p "$BACKUP_DIR" "$STORE" "$WORK/bin" "$WORK/restore"

# The wrapper mounts the work directory at the same path inside the container,
# so every path the script passes means the same thing on both sides.
cat > "$WORK/bin/rclone" <<EOF
#!/usr/bin/env bash
exec docker run --rm --user "$(id -u):$(id -g)" -v "$WORK:$WORK" \\
    -e RCLONE_CONFIG="$RCLONE_CONFIG" rclone/rclone:latest "\$@"
EOF
chmod +x "$WORK/bin/rclone"
export PATH="$WORK/bin:$PATH"

# Two remotes over the same folder: "plain" sends files as they are, "sealed"
# encrypts first. The password is a throwaway made for this run.
: > "$RCLONE_CONFIG"
rclone config create plain alias remote="$STORE" >/dev/null
rclone config create sealed crypt remote="$STORE" \
    password="test-$(date +%s)-$RANDOM" --obscure >/dev/null

failed=0
ok()   { echo "ok    $1"; }
fail() { echo "FAIL  $1"; failed=1; }
expect_rc() {
    local name="$1" expected="$2" actual="$3"
    if [ "$actual" = "$expected" ]; then ok "$name (exit $actual)"; else fail "$name: expected exit $expected, got $actual"; fi
}
stored_count() { find "$STORE" -type f | wc -l | tr -d ' '; }

echo "--- 1. no destination given"
BACKUP_REMOTE="" bash deploy/backup-offsite.sh >/dev/null 2>&1
expect_rc "an empty BACKUP_REMOTE is a usage error" 2 $?

echo "--- 2. a destination that does not encrypt"
BACKUP_REMOTE="plain:nightly" bash deploy/backup-offsite.sh >/dev/null 2>&1
expect_rc "a plain remote is refused" 1 $?
[ "$(stored_count)" = "0" ] && ok "nothing reached the provider" || fail "the provider holds $(stored_count) files"
ls "$BACKUP_DIR"/nightly-* >/dev/null 2>&1 && fail "a dump was taken before the refusal" || ok "no dump was taken either"

echo "--- 3. an unknown remote"
BACKUP_REMOTE="missing:nightly" bash deploy/backup-offsite.sh >/dev/null 2>&1
expect_rc "an unknown remote is refused" 1 $?

echo "--- 4. a full run"
BACKUP_REMOTE="sealed:nightly" bash deploy/backup-offsite.sh > "$WORK/run.log" 2>&1
expect_rc "the run succeeds" 0 $?
local_file="$(ls -1t "$BACKUP_DIR"/nightly-*.sql.gz 2>/dev/null | head -n 1)"
[ -n "$local_file" ] && ok "a local dump exists" || fail "no local dump"
[ "$(stored_count)" = "1" ] && ok "one file reached the provider" || fail "the provider holds $(stored_count) files"
# What the provider sees: no readable name, and not a gzip file.
if find "$STORE" -type f | grep -q "nightly\|sql"; then fail "the stored name is readable"; else ok "the stored name is encrypted"; fi
stored="$(find "$STORE" -type f | head -n 1)"
if gzip -t "$stored" 2>/dev/null; then fail "the stored file is a readable gzip"; else ok "the stored file is not a readable gzip"; fi
# What a restore gets back: the same bytes, complete to the last line.
rclone copy "sealed:nightly/$(basename "$local_file")" "$WORK/restore"
restored="$WORK/restore/$(basename "$local_file")"
cmp -s "$local_file" "$restored" && ok "the restored file matches the dump byte for byte" || fail "the restored file differs"
gzip -dc "$restored" | tail -n 1 | grep -q 'Dump completed' && ok "the restored dump is complete" || fail "the restored dump is cut short"

echo "--- 5. old remote copies are removed, recent ones stay"
: > "$WORK/old.sql.gz"; : > "$WORK/recent.sql.gz"
rclone copyto "$WORK/old.sql.gz" "sealed:nightly/nightly-old.sql.gz"
rclone copyto "$WORK/recent.sql.gz" "sealed:nightly/nightly-recent.sql.gz"
rclone touch --timestamp "$(date -u -v-40d +%Y-%m-%dT%H:%M:%S 2>/dev/null || date -u -d '40 days ago' +%Y-%m-%dT%H:%M:%S)" "sealed:nightly/nightly-old.sql.gz"
rclone touch --timestamp "$(date -u -v-5d +%Y-%m-%dT%H:%M:%S 2>/dev/null || date -u -d '5 days ago' +%Y-%m-%dT%H:%M:%S)" "sealed:nightly/nightly-recent.sql.gz"
sleep 1
BACKUP_REMOTE="sealed:nightly" bash deploy/backup-offsite.sh >/dev/null 2>&1
expect_rc "the second run succeeds" 0 $?
listing="$(rclone lsf --files-only sealed:nightly)"
echo "$listing" | grep -q "nightly-old"    && fail "the 40 day old copy is still there" || ok "the 40 day old copy is gone"
echo "$listing" | grep -q "nightly-recent" && ok "the 5 day old copy stayed" || fail "the 5 day old copy was removed"

echo "--- 6. local nightly dumps are capped, deploy dumps are left alone"
touch "$BACKUP_DIR/pre-deploy-20260101T000000Z-keepme.sql.gz"
sleep 1
BACKUP_REMOTE="sealed:nightly" bash deploy/backup-offsite.sh >/dev/null 2>&1
count="$(ls -1 "$BACKUP_DIR"/nightly-*.sql.gz | wc -l | tr -d ' ')"
[ "$count" = "2" ] && ok "two nightly dumps kept after three runs" || fail "$count nightly dumps kept"
[ -f "$BACKUP_DIR/pre-deploy-20260101T000000Z-keepme.sql.gz" ] && ok "the deploy dump was not touched" || fail "the deploy dump was removed"

echo "--- 7. a failed upload removes nothing"
before="$(rclone lsf --files-only sealed:nightly | wc -l | tr -d ' ')"
chmod -R a-w "$STORE"
BACKUP_REMOTE="sealed:nightly" KEEP_REMOTE_DAYS=0 bash deploy/backup-offsite.sh >/dev/null 2>&1
rc=$?
chmod -R u+w "$STORE"
[ "$rc" != "0" ] && ok "the run fails when the provider refuses the file (exit $rc)" || fail "the run reported success"
after="$(rclone lsf --files-only sealed:nightly | wc -l | tr -d ' ')"
[ "$before" = "$after" ] && ok "no remote copy was removed ($after files)" || fail "remote copies went from $before to $after"

echo "--- 8. the database is stopped"
docker compose -f "$COMPOSE_FILE" stop mysql >/dev/null 2>&1
before="$(stored_count)"
BACKUP_REMOTE="sealed:nightly" bash deploy/backup-offsite.sh >/dev/null 2>&1
expect_rc "the run fails" 1 $?
[ "$(stored_count)" = "$before" ] && ok "nothing new reached the provider" || fail "a file was uploaded"
docker compose -f "$COMPOSE_FILE" start mysql >/dev/null 2>&1

echo "--- 9. the log of the full run shows names only"
if grep -qi "password\|CREATE TABLE\|INSERT INTO" "$WORK/run.log"; then fail "the log holds contents or a secret"; else ok "the log holds no contents and no secret"; fi
cat "$WORK/run.log"

exit "$failed"
