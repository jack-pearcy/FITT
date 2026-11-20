#!/usr/bin/env bash
# Rolls a published image tag onto the production server.
#
# Runs ON THE SERVER, started by the Deploy workflow, which writes this file and
# the new compose file into the deploy directory first. In order:
#
#   1. check the new compose file, then put it in place
#   2. pull the images
#   3. dump the database, because the new backend may migrate it on start
#   4. start the new containers
#   5. wait until the backend reports healthy, and fail the deploy if it does not
#
# Steps 1 to 3 change nothing that is running, so a failure there leaves the
# site as it was. Nothing here rolls back by itself: a migration may already
# have run by the time step 5 fails, and choosing between the old image and the
# dump is a decision for a person.
#
# To restore a dump by hand, on the server, with the backend stopped:
#
#   gzip -dc backups/<file>.sql.gz | docker compose -f docker-compose.prod.yml \
#       exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -u root "$MYSQL_DATABASE"'
#
# deploy/test-deploy.sh exercises the functions below against the local stack.
#
# The output of this script lands in a public Actions log. It prints file names
# and container states, never application logs and never a value from .env.
set -euo pipefail

DEPLOY_DIR="${DEPLOY_DIR:-/opt/fitt}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
BACKUP_DIR="${BACKUP_DIR:-$DEPLOY_DIR/backups}"
# Dumps taken before a deploy. Older ones are removed.
KEEP_BACKUPS="${KEEP_BACKUPS:-10}"
# The backend's own health check allows 60 seconds to start and then needs up to
# three failed checks 30 seconds apart to call itself unhealthy.
HEALTH_TIMEOUT_SECONDS="${HEALTH_TIMEOUT_SECONDS:-240}"

compose() {
    docker compose -f "$COMPOSE_FILE" "$@"
}

# The tag the server is running now, read before anything is changed.
current_tag() {
    sed -n 's/^IMAGE_TAG=//p' .env | head -n 1
}

# The workflow leaves the compose file from the commit being deployed beside the
# live one. It is checked with the new tag before it replaces anything, and the
# old one is kept so the previous deploy can be put back by hand.
install_compose_file() {
    local incoming="$COMPOSE_FILE.new"
    if [[ ! -f "$incoming" ]]; then
        echo "No new compose file was sent, keeping the one on the server."
        return 0
    fi
    # Checked by hand, not left to "set -e": a function called inside a condition
    # runs with that switched off, and this check must never be skipped.
    if ! docker compose -f "$incoming" config --quiet; then
        echo "The new compose file is not valid. The one on the server was kept." >&2
        return 1
    fi
    if [[ -f "$COMPOSE_FILE" ]]; then
        cp -p "$COMPOSE_FILE" "$COMPOSE_FILE.previous"
    fi
    mv "$incoming" "$COMPOSE_FILE"
    echo "Compose file updated."
}

# Writes a compressed dump of the database and prints its path on the last line.
# A dump is only kept once it has been read back to its final line, so a file in
# the backup directory is always a complete one.
backup_database() {
    local label="${1:-unknown}"
    local container
    container="$(compose ps --all --quiet mysql 2>/dev/null || true)"
    if [[ -z "$container" ]]; then
        echo "No database container exists yet, so there is nothing to dump."
        return 0
    fi
    if [[ "$(docker inspect --format '{{.State.Running}}' "$container")" != "true" ]]; then
        echo "The database container exists but is not running, so it cannot be dumped." >&2
        echo "Start it, or dump it by hand, before deploying over it." >&2
        return 1
    fi

    mkdir -p "$BACKUP_DIR"
    chmod 700 "$BACKUP_DIR"
    local file="$BACKUP_DIR/pre-deploy-$(date -u +%Y%m%dT%H%M%SZ)-${label:0:12}.sql.gz"

    # The password is read inside the container from its own environment, and
    # passed to mysqldump through MYSQL_PWD so it never appears in a process list.
    # --single-transaction gives a consistent snapshot without locking the tables.
    # pipefail (set at the top) makes a failed mysqldump fail the whole line,
    # even though gzip after it succeeds.
    if ! compose exec -T mysql sh -c \
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump --single-transaction --routines --triggers --no-tablespaces -u root "$MYSQL_DATABASE"' \
        | gzip > "$file.partial"; then
        echo "mysqldump failed, so the deploy stops here." >&2
        rm -f "$file.partial"
        return 1
    fi

    # mysqldump writes "Dump completed" as its last line only when it got to the end.
    if ! gzip -t "$file.partial" || ! gzip -dc "$file.partial" | tail -n 1 | grep -q 'Dump completed'; then
        echo "The dump did not finish, so the deploy stops here." >&2
        rm -f "$file.partial"
        return 1
    fi
    mv "$file.partial" "$file"
    chmod 600 "$file"

    # Newest first; everything past the ones to keep goes.
    local old
    ls -1t "$BACKUP_DIR"/pre-deploy-*.sql.gz | tail -n +"$((KEEP_BACKUPS + 1))" | while IFS= read -r old; do
        rm -f "$old"
    done

    echo "Database dumped: $file ($(du -h "$file" | cut -f1))"
}

# Waits for the backend's health check to pass. A container that has stopped, or
# one whose check has failed, ends the wait at once; one that keeps restarting
# never reports healthy and runs into the timeout.
wait_for_backend() {
    local waited=0 container state health
    while (( waited < HEALTH_TIMEOUT_SECONDS )); do
        container="$(compose ps --all --quiet backend 2>/dev/null || true)"
        if [[ -n "$container" ]]; then
            state="$(docker inspect --format '{{.State.Status}}' "$container")"
            health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container")"
            if [[ "$health" == "healthy" ]]; then
                echo "Backend is healthy after ${waited}s."
                return 0
            fi
            if [[ "$health" == "unhealthy" || "$state" == "exited" || "$state" == "dead" ]]; then
                echo "Backend is $state, health $health, after ${waited}s." >&2
                return 1
            fi
        fi
        sleep 5
        waited=$((waited + 5))
    done
    echo "Backend did not report healthy within ${HEALTH_TIMEOUT_SECONDS}s." >&2
    return 1
}

# What a person needs to pick up a failed deploy, without putting the
# application's own log lines into a public job log.
report_failure() {
    local previous="$1" dump="$2"
    local log="$DEPLOY_DIR/deploy-failure-$(date -u +%Y%m%dT%H%M%SZ).log"
    compose logs --no-color --tail 300 backend > "$log" 2>&1 || true
    chmod 600 "$log"
    {
        echo "DEPLOY FAILED: the new backend is not healthy. Nothing was rolled back."
        echo "Container states:"
        compose ps --all || true
        echo "Backend log saved on the server: $log"
        echo "Tag that was running before: ${previous:-none}"
        echo "Dump taken before this deploy: ${dump:-none}"
        echo "To go back, run this workflow again with the earlier tag. If a migration"
        echo "already ran and the earlier build cannot start on the new schema, restore the dump."
    } >&2
}

main() {
    : "${IMAGE_TAG:?IMAGE_TAG is required}"
    : "${GHCR_TOKEN:?GHCR_TOKEN is required}"
    : "${GHCR_ACTOR:?GHCR_ACTOR is required}"
    if ! [[ "$IMAGE_TAG" =~ ^[0-9a-f]{40}$ ]]; then
        echo "IMAGE_TAG must be a full 40-character lowercase commit SHA" >&2
        exit 1
    fi

    cd "$DEPLOY_DIR"
    local previous
    previous="$(current_tag)"
    echo "Deploying $IMAGE_TAG over ${previous:-nothing}."

    # Compose takes IMAGE_TAG from the environment ahead of .env, so everything
    # below runs against the new tag before .env is rewritten.
    export IMAGE_TAG

    # The registry login lasts only as long as this script, however it ends.
    trap 'docker logout ghcr.io >/dev/null 2>&1 || true' EXIT
    echo "$GHCR_TOKEN" | docker login ghcr.io -u "$GHCR_ACTOR" --password-stdin >/dev/null

    install_compose_file
    compose pull --quiet

    local dump_report dump=""
    dump_report="$(backup_database "$previous")"
    echo "$dump_report"
    case "$dump_report" in
        *"Database dumped: "*) dump="${dump_report##*Database dumped: }" ;;
    esac

    # From here on the running site changes.
    sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG=${IMAGE_TAG}/" .env
    compose up -d --remove-orphans

    if ! wait_for_backend; then
        report_failure "$previous" "$dump"
        exit 1
    fi

    docker image prune -f >/dev/null
    echo "Deployed $IMAGE_TAG."
}

# Lets a test load the functions without starting a deploy.
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    main "$@"
fi
