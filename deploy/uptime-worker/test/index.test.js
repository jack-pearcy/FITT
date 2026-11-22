// Plays a healthy, an unhealthy and an unreachable service at the Worker, and
// checks when it starts the Uptime workflow. No network: fetch is a stand-in.
import { test } from "node:test";
import assert from "node:assert/strict";
import { checkHealth, isHeartbeat, runOnce } from "../src/index.js";

const TOKEN = "test-token-not-real";
const NOON = Date.UTC(2026, 10, 21, 12, 0);
const EIGHT = Date.UTC(2026, 10, 21, 8, 0);
const EIGHT_OH_FIVE = Date.UTC(2026, 10, 21, 8, 5);

function json(status, body) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

// health: one entry per health request, each a Response or an Error to throw.
function fakeDeps(health, dispatchStatus = 204) {
  const calls = { health: 0, dispatches: [], sleeps: 0, log: [] };
  return {
    calls,
    deps: {
      fetch: async (url, init) => {
        if (url.startsWith("https://api.github.com/")) {
          calls.dispatches.push({ url, init });
          return new Response(null, { status: dispatchStatus });
        }
        const next = health[Math.min(calls.health, health.length - 1)];
        calls.health++;
        if (next instanceof Error) throw next;
        return next;
      },
      sleep: async () => {
        calls.sleeps++;
      },
      log: (line) => calls.log.push(line),
    },
  };
}

const ENV = { GITHUB_TOKEN: TOKEN };

test("up on the first try: one request, no wait, no dispatch", async () => {
  const { calls, deps } = fakeDeps([json(200, { status: "UP" })]);
  assert.equal(await runOnce(NOON, ENV, deps), "up");
  assert.equal(calls.health, 1);
  assert.equal(calls.sleeps, 0);
  assert.equal(calls.dispatches.length, 0);
});

test("one failed try then UP is not an outage", async () => {
  const { calls, deps } = fakeDeps([json(502, {}), json(200, { status: "UP" })]);
  assert.equal(await runOnce(NOON, ENV, deps), "up");
  assert.equal(calls.health, 2);
  assert.equal(calls.sleeps, 1);
  assert.equal(calls.dispatches.length, 0);
});

test("every try failing dispatches the workflow on main", async () => {
  const { calls, deps } = fakeDeps([json(503, {})]);
  assert.equal(await runOnce(NOON, ENV, deps), "down");
  assert.equal(calls.health, 3);
  assert.equal(calls.sleeps, 2);
  assert.equal(calls.dispatches.length, 1);
  const { url, init } = calls.dispatches[0];
  assert.equal(
    url,
    "https://api.github.com/repos/jack-pearcy/FITT/actions/workflows/uptime.yml/dispatches",
  );
  assert.equal(init.method, "POST");
  assert.equal(init.headers.authorization, `Bearer ${TOKEN}`);
  assert.ok(init.headers["user-agent"]);
  assert.deepEqual(JSON.parse(init.body), { ref: "main" });
});

test("HTTP 200 without status UP counts as down", async () => {
  const { calls, deps } = fakeDeps([json(200, { status: "DOWN" })]);
  assert.equal(await runOnce(NOON, ENV, deps), "down");
  assert.equal(calls.dispatches.length, 1);
});

test("no answer at all counts as down", async () => {
  const { calls, deps } = fakeDeps([new TypeError("fetch failed")]);
  assert.equal(await runOnce(NOON, ENV, deps), "down");
  assert.equal(calls.dispatches.length, 1);
  assert.match(calls.log[0], /no answer \(TypeError\)/);
});

test("08:00 UTC dispatches once while up, 08:05 does not", async () => {
  const at8 = fakeDeps([json(200, { status: "UP" })]);
  assert.equal(await runOnce(EIGHT, ENV, at8.deps), "heartbeat");
  assert.equal(at8.calls.dispatches.length, 1);

  const at805 = fakeDeps([json(200, { status: "UP" })]);
  assert.equal(await runOnce(EIGHT_OH_FIVE, ENV, at805.deps), "up");
  assert.equal(at805.calls.dispatches.length, 0);
});

test("a refused dispatch fails the run without naming the token", async () => {
  const { deps } = fakeDeps([json(503, {})], 401);
  await assert.rejects(runOnce(NOON, ENV, deps), (error) => {
    assert.match(error.message, /HTTP 401/);
    assert.ok(!error.message.includes(TOKEN));
    return true;
  });
});

test("a missing token fails the run before any request to GitHub", async () => {
  const { calls, deps } = fakeDeps([json(503, {})]);
  await assert.rejects(runOnce(NOON, {}, deps), /GITHUB_TOKEN is not set/);
  assert.equal(calls.dispatches.length, 0);
});

test("the log never carries the response body", async () => {
  const { calls, deps } = fakeDeps([json(200, { status: "UP", secret: "body-value" })]);
  await checkHealth(
    { HEALTH_URL: "https://example.test/health", ATTEMPTS: 1, WAIT_MS: 0, TIMEOUT_MS: 1000 },
    deps,
  ).then((result) => calls.log.push(...result.lines));
  assert.ok(calls.log.every((line) => !line.includes("body-value")));
});

test("isHeartbeat matches only the first run of the chosen hour", () => {
  assert.equal(isHeartbeat(EIGHT, 8), true);
  assert.equal(isHeartbeat(EIGHT_OH_FIVE, 8), false);
  assert.equal(isHeartbeat(NOON, 8), false);
});
