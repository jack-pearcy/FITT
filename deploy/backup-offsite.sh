#!/usr/bin/env bash
# Dumps the database and copies the dump, encrypted, to storage off the server.
#
# The dumps a deploy takes sit on the same disk as the database, so losing the
# server loses both. This script is the copy that survives that.
#
# Runs ON THE SERVER, once a night, from the deploy user's crontab. Every deploy
# writes the current version of this file beside deploy.sh. In order:
#
#   1. refuse to run unless the destination is an encrypting rclone remote
#   2. dump the database, the same checked dump a deploy takes
#   3. upload it
#   4. confirm the upload is listed at the destination
#   5. only then remove remote copies older than KEEP_REMOTE_DAYS
#
# A failure at any step stops the script with a non-zero exit, and step 5 never
# runs after a failed upload, so old copies are never removed while new ones
# are not arriving.
#
# ENCRYPTION. The dump holds health-related data, so the storage provider must
# not be able to read it. rclone's "crypt" remote encrypts file contents and
# file names on this server before anything is sent. The script checks the
# remote's type and stops if it is anything else.
#
# ONE-TIME SETUP ON THE SERVER, by a person:
#
#   sudo apt install rclone
#   rclone config        # first a remote for the storage provider,
#                        # then a remote of type "crypt" that wraps it
#   crontab -e           # then add this line (03:17 UTC every night):
#
#   17 3 * * * cd /opt/fitt && BACKUP_REMOTE=<crypt remote>:nightly bash backup-offsite.sh >> backups/nightly.log 2>&1
#
# KEEP THE CRYPT PASSWORD SOMEWHERE THAT IS NOT THIS SERVER. It lives in the
# rclone config on this machine. If the server is lost and the password with
# it, the copies cannot be read by anyone.
#
# TO RESTORE, on any machine with rclone and the same crypt remote configured:
#
#   rclone lsf <crypt remote>:nightly
#   rclone copy <crypt remote>:nightly/<file>.sql.gz .
#
# then load the file as the header of deploy.sh describes.
#
# deploy/test-backup-offsite.sh exercises this against the local stack.
#
# This prints file names and sizes, never file contents and never a value from
# the rclone config or .env.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Loads backup_database and the settings it reads. deploy.sh only starts a
# deploy when it is run directly, so loading it here deploys nothing.
# shellcheck source=deploy/deploy.sh
source "$HERE/deploy.sh"

# Where the copies go, as an rclone path: "<crypt remote>:<folder>".
BACKUP_REMOTE="${BACKUP_REMOTE:-}"
# Nightly dumps kept on the server's own disk.
KEEP_NIGHTLY_LOCAL="${KEEP_NIGHTLY_LOCAL:-7}"
# Remote copies older than this many days are removed after a good upload.
KEEP_REMOTE_DAYS="${KEEP_REMOTE_DAYS:-30}"
RCLONE="${RCLONE:-rclone}"

# Stops unless the destination is a remote that encrypts before it sends.
require_encrypted_remote() {
    local name="${BACKUP_REMOTE%%:*}"
    if [[ -z "$BACKUP_REMOTE" || "$name" == "$BACKUP_REMOTE" || -z "$name" ]]; then
        echo "BACKUP_REMOTE must be an rclone path such as offsite:nightly." >&2
        return 2
    fi
    if ! command -v "$RCLONE" >/dev/null 2>&1; then
        echo "rclone is not installed, so nothing can be copied off the server." >&2
        return 1
    fi
    # "listremotes --long" prints one "name: type" line per remote and nothing
    # else from the config, so no stored secret passes through here.
    local kind
    kind="$("$RCLONE" listremotes --long | awk -v remote="$name:" '$1 == remote { print $2 }')"
    if [[ -z "$kind" ]]; then
        echo "rclone has no remote named $name." >&2
        return 1
    fi
    if [[ "$kind" != "crypt" ]]; then
        echo "The remote $name is of type $kind, not crypt. Refusing to upload an unencrypted dump." >&2
        return 1
    fi
}

# Uploads one file and confirms the destination lists it.
upload_dump() {
    local file="$1"
    local name
    name="$(basename "$file")"
    # rclone compares size and checksum after a transfer and fails if they differ.
    if ! "$RCLONE" copyto "$file" "$BACKUP_REMOTE/$name"; then
        echo "The upload of $name failed." >&2
        return 1
    fi
    if ! "$RCLONE" lsf --files-only "$BACKUP_REMOTE" | grep -Fxq "$name"; then
        echo "The upload of $name reported success, but the destination does not list it." >&2
        return 1
    fi
}

# Removes remote copies past their age. Only called after a good upload.
prune_remote() {
    "$RCLONE" delete --min-age "${KEEP_REMOTE_DAYS}d" "$BACKUP_REMOTE"
}

offsite_main() {
    cd "$DEPLOY_DIR"
    require_encrypted_remote

    local report file
    report="$(backup_database db nightly "$KEEP_NIGHTLY_LOCAL")"
    printf '%s\n' "$report"
    file="$(printf '%s\n' "$report" | sed -n 's/^Database dumped: \(.*\) (.*/\1/p')"
    if [[ -z "$file" ]]; then
        # backup_database treats a missing container as nothing to do, which is
        # right before a first deploy. For a nightly backup it is a failure.
        echo "No dump was written, so there is nothing to copy off the server." >&2
        return 1
    fi

    upload_dump "$file"
    prune_remote
    echo "Off-server copy done: $(basename "$file") at $(date -u +%Y-%m-%dT%H:%M:%SZ)"
}

# Lets a test load the functions without starting a backup.
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    offsite_main "$@"
fi
