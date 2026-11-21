#!/usr/bin/env bash
# Exercises uptime-check.sh against a small local server that plays the
# healthy, unhealthy and missing service in turn.
#
# Usage, from the repository root (needs python3 and curl):
#
#   bash deploy/test-uptime-check.sh
#
# Exit 0 when every case gave the expected exit code, 1 otherwise.
set -u
cd "$(dirname "$0")/.." || exit 2

PORT="${TEST_PORT:-18473}"
BASE="http://127.0.0.1:$PORT"
export UPTIME_ATTEMPTS=2 UPTIME_WAIT_SECONDS=1 UPTIME_TIMEOUT_SECONDS=2

python3 - "$PORT" <<'PY' &
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer

hits = {"flaky": 0}

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def reply(self, code, body):
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(body.encode())

    def do_GET(self):
        if self.path == "/up":
            self.reply(200, '{"status":"UP","application":"backend"}')
        elif self.path == "/down-body":
            self.reply(200, '{"status":"DOWN"}')
        elif self.path == "/error":
            self.reply(503, '{"status":"UP"}')
        elif self.path == "/flaky":
            # Fails the first request, then answers healthy.
            hits["flaky"] += 1
            if hits["flaky"] == 1:
                self.reply(502, "")
            else:
                self.reply(200, '{"status":"UP"}')
        else:
            self.reply(404, "")

HTTPServer(("127.0.0.1", int(sys.argv[1])), Handler).serve_forever()
PY
SERVER=$!
trap 'kill "$SERVER" 2>/dev/null' EXIT

# Wait for the test server to accept connections.
for _ in 1 2 3 4 5 6 7 8 9 10; do
  curl --silent --output /dev/null "$BASE/up" && break
  sleep 0.3
done

failed=0
check() {
  local name="$1" expected="$2"
  shift 2
  "$@" >/dev/null 2>&1
  local actual=$?
  if [ "$actual" = "$expected" ]; then
    echo "ok    $name (exit $actual)"
  else
    echo "FAIL  $name: expected exit $expected, got $actual"
    failed=1
  fi
}

check "healthy service passes"               0 bash deploy/uptime-check.sh "$BASE/up"
check "200 without UP in the body fails"     1 bash deploy/uptime-check.sh "$BASE/down-body"
check "503 fails even with UP in the body"   1 bash deploy/uptime-check.sh "$BASE/error"
check "one failure then healthy passes"      0 bash deploy/uptime-check.sh "$BASE/flaky"
check "nothing listening fails"              1 bash deploy/uptime-check.sh "http://127.0.0.1:1/up"
check "no address is a usage error"          2 bash deploy/uptime-check.sh

exit "$failed"
