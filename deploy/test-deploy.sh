#!/usr/bin/env bash
# Exercises the functions of deploy.sh against the LOCAL compose stack. It cannot
# run a whole deploy, which needs the registry and the server, so it checks the
# parts that protect the data: the dump, the pruning, the health wait and the
# compose file check.
#
# Usage, from the repository root, with the local stack up and healthy
# (docker compose up -d mysql backend):
#
#   bash deploy/test-deploy.sh "$PWD" "$(mktemp -d)"
#
# Read the output: every case prints what it saw, and the expected values are in
# the comments beside it. It stops the local backend and database as it goes,
# and deletes nothing: everything it writes goes under the work directory.
REPO="$1"
WORK="$2"
cd "$REPO" || exit 2

export DEPLOY_DIR="$REPO" COMPOSE_FILE=docker-compose.yml BACKUP_DIR="$WORK/backups"
export KEEP_BACKUPS=3 HEALTH_TIMEOUT_SECONDS=30
mkdir -p "$BACKUP_DIR" "$WORK/compose"

source deploy/deploy.sh
# deploy.sh turns on exit-on-error. Here a failing case is the point.
set +e

echo "--- 1. dump of a running database"
# Expect rc=0, a path, mode 600, more than ten tables, and no .partial file.
out="$( backup_database abcdef0123456789 )"; echo "rc=$?"
file="$(printf '%s\n' "$out" | sed -n 's/^Database dumped: \(.*\) (.*/\1/p')"
echo "file: $(basename "$file")"
echo "mode: $(stat -c %a "$file" 2>/dev/null || stat -f %Lp "$file")"
echo "tables in dump: $(gzip -dc "$file" | grep -c '^CREATE TABLE')"
echo "partial files left: $(ls "$BACKUP_DIR" | grep -c partial)"
mv "$file" "$WORK/first-dump.sql.gz"

echo "--- 2. old dumps are pruned to KEEP_BACKUPS=3"
# Expect 3 files left, both real dumps among them, none of the four oldest.
for i in 1 2 3 4 5; do
    touch -t "2026010${i}0000" "$BACKUP_DIR/pre-deploy-2026010${i}T000000Z-old.sql.gz"
done
( backup_database first ) >/dev/null
sleep 1
( backup_database second ) >/dev/null
echo "files left: $(ls "$BACKUP_DIR" | wc -l | tr -d ' ')"
echo "newest two are the real dumps: $(ls -1t "$BACKUP_DIR" | head -2 | grep -c -E 'first|second')"
echo "oldest four fakes still there: $(ls "$BACKUP_DIR" | grep -c -E '0101T|0102T|0103T|0104T')"

echo "--- 3. health wait on a healthy backend"
# Expect rc=0.
( wait_for_backend ); echo "rc=$?"

echo "--- 4. health wait on a stopped backend"
# Expect rc=1, at once and not after the timeout.
docker compose stop backend >/dev/null 2>&1
( wait_for_backend ); echo "rc=$?"

echo "--- 5. dump when the database is stopped"
# Expect rc=1 and no new file: deploying over a database that cannot be dumped is refused.
before="$(ls "$BACKUP_DIR" | wc -l | tr -d ' ')"
docker compose stop mysql >/dev/null 2>&1
( backup_database x ); echo "rc=$?"
echo "new files written: $(( $(ls "$BACKUP_DIR" | wc -l | tr -d ' ') - before ))"

echo "--- 6. a broken incoming compose file is refused and the live one kept"
# Expect rc=1, the live file unchanged, and no previous copy.
cp docker-compose.prod.yml "$WORK/compose/docker-compose.prod.yml"
cp docker-compose.prod.yml "$WORK/compose/reference.yml"
printf 'services:\n  backend:\n    image: [not, valid\n' > "$WORK/compose/docker-compose.prod.yml.new"
( cd "$WORK/compose" && COMPOSE_FILE=docker-compose.prod.yml install_compose_file ) >/dev/null 2>&1; echo "rc=$?"
cmp -s "$WORK/compose/docker-compose.prod.yml" "$WORK/compose/reference.yml" && echo "live file unchanged"
[[ -f "$WORK/compose/docker-compose.prod.yml.previous" ]] || echo "no previous copy was made"

echo "--- 6b. a valid incoming compose file is installed and the old one kept"
# Expect rc=0 and all three lines.
{ cat docker-compose.prod.yml; echo; echo "# marker"; } > "$WORK/compose/docker-compose.prod.yml.new"
( cd "$WORK/compose" && COMPOSE_FILE=docker-compose.prod.yml IMAGE_OWNER=o IMAGE_TAG=t install_compose_file ) 2>/dev/null; echo "rc=$?"
grep -q '^# marker' "$WORK/compose/docker-compose.prod.yml" && echo "new file is live"
cmp -s "$WORK/compose/docker-compose.prod.yml.previous" "$WORK/compose/reference.yml" && echo "previous copy kept"
[[ -f "$WORK/compose/docker-compose.prod.yml.new" ]] || echo "incoming file consumed"

echo "--- 8. a dump that fails part way leaves no file behind"
# Expect rc=1 and no new file, twice.
docker compose start mysql >/dev/null 2>&1
for i in $(seq 1 30); do docker compose ps mysql --format '{{.Status}}' | grep -q '(healthy)' && break; sleep 2; done
before="$(ls "$BACKUP_DIR" | wc -l | tr -d ' ')"
(
    # Stands in for a mysqldump that dies half way: some output, then a failure.
    compose() { if [[ "$1" == exec ]]; then echo "-- partial"; return 3; else docker compose -f "$COMPOSE_FILE" "$@"; fi; }
    backup_database broken
); echo "rc=$?"
echo "new files written: $(( $(ls "$BACKUP_DIR" | wc -l | tr -d ' ') - before ))"
(
    # Stands in for a dump that exits 0 but was cut short before its last line.
    compose() { if [[ "$1" == exec ]]; then echo "-- no final line"; else docker compose -f "$COMPOSE_FILE" "$@"; fi; }
    backup_database short
); echo "rc=$?"
echo "new files written: $(( $(ls "$BACKUP_DIR" | wc -l | tr -d ' ') - before ))"

echo "--- 7. main refuses a tag that is not a commit SHA, and a missing token"
# Expect one refusal for each, and nothing else.
( IMAGE_TAG='latest; echo pwned' GHCR_TOKEN=x GHCR_ACTOR=x main ) 2>&1 | tail -1
( unset GHCR_TOKEN; IMAGE_TAG="$(printf 'a%.0s' $(seq 1 40))" GHCR_ACTOR=x main ) 2>&1 | tail -1 | sed 's/^.*GHCR_TOKEN: /GHCR_TOKEN: /'
