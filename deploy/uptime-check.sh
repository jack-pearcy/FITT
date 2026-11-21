#!/usr/bin/env bash
# Asks the health endpoint whether the service is up, from outside the server.
#
# Usage:
#
#   bash deploy/uptime-check.sh https://fittapp.dev/api/health
#
# Exit 0 when one attempt gets HTTP 200 with "status":"UP" in the body.
# Exit 1 when every attempt fails. Exit 2 when no address is given.
#
# One failed request is not an outage: a deploy restarts the backend, and a
# single packet can get lost. So the check tries a few times with a pause
# between tries, and only reports down when all of them fail.
#
# It prints the HTTP status of each try and nothing else. The response body is
# never printed, because this runs in a public workflow log.
set -u

URL="${1:-}"
ATTEMPTS="${UPTIME_ATTEMPTS:-3}"
WAIT_SECONDS="${UPTIME_WAIT_SECONDS:-20}"
TIMEOUT_SECONDS="${UPTIME_TIMEOUT_SECONDS:-10}"

if [ -z "$URL" ]; then
  echo "Usage: uptime-check.sh <health address>" >&2
  exit 2
fi

BODY="$(mktemp)"
trap 'rm -f "$BODY"' EXIT

attempt=1
while [ "$attempt" -le "$ATTEMPTS" ]; do
  # 000 is what curl reports when it never got an HTTP answer at all:
  # refused connection, failed TLS, DNS failure or a timeout.
  status="$(curl --silent --output "$BODY" --write-out '%{http_code}' \
    --max-time "$TIMEOUT_SECONDS" "$URL")" || status="${status:-000}"

  if [ "$status" = "200" ] && grep -q '"status":"UP"' "$BODY"; then
    echo "Attempt $attempt of $ATTEMPTS: HTTP 200, status UP"
    exit 0
  fi

  if [ "$status" = "200" ]; then
    echo "Attempt $attempt of $ATTEMPTS: HTTP 200, but the body does not say UP"
  else
    echo "Attempt $attempt of $ATTEMPTS: HTTP $status"
  fi

  if [ "$attempt" -lt "$ATTEMPTS" ]; then
    sleep "$WAIT_SECONDS"
  fi
  attempt=$((attempt + 1))
done

echo "DOWN: $ATTEMPTS attempts failed for $URL"
exit 1
