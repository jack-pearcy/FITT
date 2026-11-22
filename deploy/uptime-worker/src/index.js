// Watches the live service from Cloudflare's network, every five minutes.
//
// WHY THIS EXISTS. The Uptime workflow in .github/workflows/uptime.yml asks
// GitHub to run it every 15 minutes, but GitHub runs scheduled workflows on a
// best-effort basis and drops most of them: on 2026-11-21 one scheduled run
// fired in 11 hours. A run that someone dispatches through the API is not
// dropped. So this Worker keeps the schedule and GitHub keeps the alarm.
//
// WHAT IT DOES, on every run:
//
//   1. Ask the health endpoint, up to three times, 20 seconds apart.
//   2. If no attempt says UP, dispatch the Uptime workflow. That run checks
//      again from GitHub's network, fails, and GitHub emails the owner of the
//      token below, as it does for any failed run they started. Two networks
//      have to agree before anyone is woken up.
//   3. Once a day at 08:00 UTC, dispatch the workflow even when all is well.
//      That green run in the Actions tab shows the token and the path still
//      work. If it stops appearing, the alarm path is broken.
//
// While the service stays down, step 2 repeats every five minutes. A repeated
// alarm is the point: one email is easy to miss.
//
// WHAT IT SEES. Only the health endpoint's status code and its "status" field.
// It never reads or logs anything else, and it has no address of its own:
// workers_dev is off and there is no fetch handler, so nobody can call it.
//
// ONE-TIME SETUP, by a person (the token value never goes in chat or git):
//
//   1. On GitHub: Settings, Developer settings, Fine-grained tokens, Generate.
//      Repository access: only jack-pearcy/FITT. Repository permissions:
//      Actions, read and write. Nothing else. Expiration: one year, with a
//      reminder to replace it.
//   2. From this folder:
//
//        npx wrangler@4.158.0 login
//        npx wrangler@4.158.0 deploy
//        npx wrangler@4.158.0 secret put GITHUB_TOKEN
//
//      The last command asks for the token at a prompt.
//   3. Watch it work: "npx wrangler@4.158.0 tail" prints one line per run.
//
// test/index.test.js exercises this with Node's own test runner, no packages:
// "node --test deploy/uptime-worker/test/index.test.js".

const DEFAULTS = {
  HEALTH_URL: "https://fittapp.dev/api/health",
  GITHUB_REPO: "jack-pearcy/FITT",
  WORKFLOW_FILE: "uptime.yml",
  ATTEMPTS: 3,
  WAIT_MS: 20_000,
  TIMEOUT_MS: 10_000,
  HEARTBEAT_HOUR_UTC: 8,
};

// One answer from the health endpoint: up only on HTTP 200 with status UP.
async function askOnce(url, fetchFn, timeoutMs) {
  try {
    const response = await fetchFn(url, {
      headers: { "user-agent": "fitt-uptime-worker" },
      signal: AbortSignal.timeout(timeoutMs),
    });
    if (response.status !== 200) {
      return { up: false, detail: `HTTP ${response.status}` };
    }
    const body = await response.json().catch(() => null);
    if (body && body.status === "UP") {
      return { up: true, detail: "HTTP 200, status UP" };
    }
    return { up: false, detail: "HTTP 200, but the body does not say UP" };
  } catch (error) {
    // No HTTP answer at all: refused, failed TLS, DNS or the timeout.
    return { up: false, detail: `no answer (${error.name})` };
  }
}

// The same rule as deploy/uptime-check.sh: down only when every attempt fails.
export async function checkHealth(settings, deps) {
  const lines = [];
  for (let attempt = 1; attempt <= settings.ATTEMPTS; attempt++) {
    const result = await askOnce(settings.HEALTH_URL, deps.fetch, settings.TIMEOUT_MS);
    lines.push(`Attempt ${attempt} of ${settings.ATTEMPTS}: ${result.detail}`);
    if (result.up) {
      return { up: true, lines };
    }
    if (attempt < settings.ATTEMPTS) {
      await deps.sleep(settings.WAIT_MS);
    }
  }
  return { up: false, lines };
}

// True for the one run a day that dispatches even when the service is up.
// Runs land on minutes 0, 5, 10 and so on, so "minute below 5" is exactly one.
export function isHeartbeat(scheduledTime, hourUtc) {
  const when = new Date(scheduledTime);
  return when.getUTCHours() === hourUtc && when.getUTCMinutes() < 5;
}

// Starts the Uptime workflow on main. Throws on any answer but success, so the
// run shows as failed in Cloudflare. The error names the status, never the token.
export async function dispatchWorkflow(settings, token, fetchFn) {
  if (!token) {
    throw new Error("GITHUB_TOKEN is not set, so the workflow cannot be started.");
  }
  const url =
    `https://api.github.com/repos/${settings.GITHUB_REPO}` +
    `/actions/workflows/${settings.WORKFLOW_FILE}/dispatches`;
  const response = await fetchFn(url, {
    method: "POST",
    headers: {
      accept: "application/vnd.github+json",
      authorization: `Bearer ${token}`,
      "content-type": "application/json",
      // GitHub refuses API requests without a User-Agent.
      "user-agent": "fitt-uptime-worker",
      "x-github-api-version": "2026-03-10",
    },
    body: JSON.stringify({ ref: "main" }),
  });
  if (!response.ok) {
    throw new Error(`GitHub refused the dispatch: HTTP ${response.status}.`);
  }
}

// One scheduled run. Returns what happened, for the log and the tests.
export async function runOnce(scheduledTime, env, deps) {
  const settings = { ...DEFAULTS, ...pickSettings(env) };
  const health = await checkHealth(settings, deps);
  for (const line of health.lines) {
    deps.log(line);
  }

  if (!health.up) {
    deps.log(`DOWN: asking GitHub to confirm from its own network.`);
    await dispatchWorkflow(settings, env.GITHUB_TOKEN, deps.fetch);
    return "down";
  }
  if (isHeartbeat(scheduledTime, settings.HEARTBEAT_HOUR_UTC)) {
    deps.log("UP: daily dispatch to prove the alarm path works.");
    await dispatchWorkflow(settings, env.GITHUB_TOKEN, deps.fetch);
    return "heartbeat";
  }
  return "up";
}

// Only the plain settings from wrangler.jsonc's "vars", never the secret.
function pickSettings(env) {
  const picked = {};
  for (const key of ["HEALTH_URL", "GITHUB_REPO", "WORKFLOW_FILE"]) {
    if (typeof env[key] === "string" && env[key] !== "") {
      picked[key] = env[key];
    }
  }
  return picked;
}

export default {
  async scheduled(controller, env, ctx) {
    ctx.waitUntil(
      runOnce(controller.scheduledTime, env, {
        fetch: (url, init) => fetch(url, init),
        sleep: (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
        log: (line) => console.log(line),
      }),
    );
  },
};
