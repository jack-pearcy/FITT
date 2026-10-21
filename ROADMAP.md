# FITT Project Roadmap

Spring Boot + Angular Full-Stack Application

## Purpose

The FITT project is a full-stack fitness application built as a professional learning and portfolio project.
The goal is not speed, shortcuts, or demo-level code. The goal is correct architecture, clear reasoning, and
industry-standard development practices.
Each phase builds intentionally on the previous one. Nothing is added early, and no layer exists without a clear reason.

---

## Product Definition (Approved 2026-03-11)

FITT is a fitness tracking application for people who train with intention. The primary user is a lifter who wants
to track workouts, monitor body weight trends, manage progressive overload, and understand their calorie goals.

### Screens and Features

**Authentication**
Login and sign up screen with two buttons. Login: credential entry. Sign up: email, password, confirm email,
then onboarding. MFA deferred to production.

**Onboarding (one-time, slideshow style)**
One question per screen with smooth transitions. Collects: height, current weight, date of birth, biological sex,
activity level, units preference (lbs/kg), goal direction (Bulk/Cut/Maintain), target weight (optional),
timeline, and calorie goal via TDEE calculation with auto/manual toggle.

**Main Screen**
Tab navigation: Weight Log / Workouts / Goals. Settings icon top right.
Body: session streak, 7-day average body weight, calorie goal, large CTA button for Today's Lift.

**Weight Log Tab**
Past 7 days of body weight with date in column format. Dropdown to expand to 30/60/90 days.
Daily reminder notification at user-set time. Prompt on app open if today not yet logged.

**Workouts Tab**
Active workout plan displayed in full. Each day viewable and editable.
Dropdown to switch between saved plans with activate/delete options.
Create plan flow: days per week, split template (PPL/Upper-Lower/Full Body/Custom), exercise population from library.
Exercise library: fixed broad library by muscle group (Chest/Back/Shoulders/Arms/Legs/Glutes)
plus user-created custom exercises with edit and delete options.

**Active Session Screen**
Triggered by Today's Lift CTA. Global session timer from start.
Exercise rows from the active plan's current day. Set logging with reps, weight, and RPE inputs.
Prescribed set cap per exercise from the plan. Per-row rest countdown timer that disappears after all sets complete.
Finish button leads to an inline session summary: total volume, total time, sets logged.
Progression model selectable by user on the Workouts tab.

**Goals Tab**
Bulk/Cut/Maintain selection, target weight, timeline, auto/manual calorie goal toggle with info icon,
TDEE-based starter calorie recommendation, editable calorie goal.

**Settings**
Personal info, contact info, units preference, notification preferences, delete account.

### Platform
Web-first. Clean professional web UI designed for desktop and browser.
iOS gets its own native SwiftUI design in Phase 21 consuming the same API.

### Deferred (not v1)
Nutrition database and food logging, MFA, social features, barcode scanning.

---

## Phase 0 — Foundations (Complete)

This phase establishes a stable baseline before any real development begins.

The repository is structured as a monorepo containing both frontend and backend.
Git is initialized at the correct root and kept clean from the start. A Spring Boot application is created and verified
to start successfully. The test context loads without error.
The backend package structure is scaffolded but intentionally left empty to avoid premature design decisions.

Outcome: a clean, trusted starting point with no architectural debt.

---

## Phase 1 — Backend Architecture Design (Complete)

This phase focuses on design rather than implementation.

The backend follows a layered architecture with clear separation of concerns. Each layer has a defined role:
configuration, controllers, services, repositories, domain entities, and DTOs.
Communication boundaries are defined early so that responsibilities do not blur later. Naming conventions and package
layout are established up front, and Spring's component scanning behavior is fully understood.

Outcome: a clear mental model of how the backend is structured and why.

---

## Phase 2 — Domain Modeling (Complete, Locked)

The domain model forms the core of the application.

Business concepts are identified first and then translated into domain entities. Fields are chosen deliberately with
correct data types, nullability, and constraints. JPA annotations are introduced carefully and explained in detail,
including entity identity, lifecycle, and persistence context. At this stage, repositories and services are
intentionally avoided to keep focus on the model itself.

Outcome: a solid understanding of what JPA entities represent and how they behave at runtime.

---

## Phase 3 — Data Access Layer (Complete, Locked)

This phase introduces persistence in a controlled way.

Spring Data JPA is added, and repositories are created for the domain entities. The difference between repositories and
traditional DAOs is explained. Query method naming conventions are covered, along with basic transaction concepts. Lazy
versus eager loading is introduced with attention to real-world consequences.

Outcome: a clear understanding of how data is stored, retrieved, and managed.

---

## Phase 4 — Service Layer (Complete)

This phase introduces application behavior.

Services are used to coordinate business logic rather than acting as dumping grounds. Controllers remain thin, and
persistence concerns remain in repositories. Transactional boundaries are introduced where appropriate. Validation logic
is separated from persistence, and DTO mapping is handled explicitly rather than hidden behind magic.

Outcome: business logic that is readable, testable, and maintainable.

---

## Phase 5 — Controllers and API Design (Complete)

This phase exposes functionality through stable APIs.

REST principles are applied consistently. Endpoints are named intentionally, and HTTP methods are used correctly.
Request and response DTOs are kept separate. Validation, error handling, and status codes are handled in a predictable
and professional way.

Outcome: APIs that look and behave like real production systems.

---

## Phase 6 — Backend Security (Complete)

Security is designed into the system rather than added later.

Authentication and authorization are clearly separated. JWT concepts are introduced, along with Spring Security's
architecture and filter chain. Endpoints are protected intentionally, and the backend is kept stateless.
Object-level authorization enforced on all user-scoped resources.

Outcome: a secure backend with a clear security model.

---

## Phase 7 — Frontend Foundations (Complete)

This phase establishes Angular fundamentals.

Angular architecture is explained from first principles. The purpose of TypeScript is made clear. Components, services,
and modules each have defined roles. The Angular CLI and build process are understood rather than treated as black
boxes. Environment configuration is handled properly. Auth service, JWT interceptor, and route guard implemented.

Outcome: Angular becomes predictable rather than confusing.

---

## Phase 8 — Frontend Feature Development (Complete)

This phase focused on real user-facing functionality with a workouts placeholder implementation.

Routing and guards introduced. HTTP client integrated with the backend. Observable-based data flow implemented.
Workouts list, create, and delete wired end-to-end against the Spring backend with JWT authentication.

Outcome: a frontend that communicates correctly with the backend.

---

## Phase 9 — Full-Stack Integration (Complete)

This phase connects frontend and backend into a single system and prepares the infrastructure for all future phases.

CORS is explained and configured correctly on the backend. API proxying confirmed as development-only.
Environment separation enforced. Correlation ID tracing implemented across Angular interceptor and Spring filter.
End-to-end request tracing verified across layers.

Outcome: the ability to reason about the entire system as one unit.

---

## Phase 10 — Domain Expansion: Identity and Onboarding (Complete)

Real user registration replaces the hardcoded uid=1L placeholder throughout the system.

User registration endpoint, biometrics entity, goals entity, TDEE calculation service, and onboarding API.
UserEntity introduced with proper identity management. JWT uid claim wired to real database user IDs.
All ownership enforcement updated to use real principal identifiers.

Outcome: real multi-user identity with secure, user-scoped data access.

---

## Phase 11 — Domain Expansion: Workouts Full Implementation (Complete)

The Workout placeholder is replaced with the full domain model from Phase 2.

WorkoutSession, ExerciseEntry, and SetEntry hierarchy implemented in full. Workout plan entity with split templates.
Exercise library with fixed entries by muscle group. Custom exercise support. Full backend: entities, repositories,
services, controllers, and ownership enforcement at every level.

Outcome: a complete, production-grade workouts domain.

---

## Phase 12 — Domain Expansion: Measurements (Complete)

Body weight logging introduced as a first-class domain.

BodyWeightEntry entity, daily logging endpoint, 7-day average calculation service.
Historical query support for 30/60/90 day views. Ownership enforced.

Outcome: a complete measurements domain ready for frontend integration.

---

## Phase 13 — Progressive Overload Algorithm (Complete)

Science-backed progression logic implemented as a backend service.

Literature review: double progression, RPE-based autoregulation, linear periodization.
Selectable progression model per user. Post-session analysis generates next session weight suggestions.
Algorithm service designed for extensibility as more models are added.

Outcome: a defensible, evidence-based progression engine.

---

## Phase 14 — Frontend Rebuild: Authentication and Onboarding (Complete)

Login and sign up screens rebuilt to production quality.

Slideshow onboarding flow wired to Phase 10 backend. Units preference, biometrics, goals, TDEE display.
Form validation and error handling at every step.

Outcome: a complete authentication and onboarding experience.

---

## Phase 15 — Frontend Rebuild: Main Screen and Navigation (Complete)

Tab navigation shell established as the permanent application frame.

Main screen: session streak, 7-day weight average, calorie goal, Today's Lift CTA fully wired to backend.
Settings screen: personal info, contact info, units, notifications, delete account.

Outcome: a fully navigable application shell.

---

## Phase 16 — Frontend Rebuild: Workouts (Complete)

Full workout plan builder UI with all active session features.

Exercise library with muscle group sections and custom exercise management.
Active session screen: global timer, per-row rest timer, set logging, prescribed set cap per exercise,
Finish button leading to inline session summary with volume, time, and sets logged.

Outcome: the core product feature functional end-to-end.

---

## Phase 17 — Frontend Rebuild: Weight Log and Goals (Complete)

Weight log tab with 7/30/60/90 day views, SVG line chart, and daily prompt on app open.
Goals tab: Bulk/Cut/Maintain, target weight, timeline, TDEE calorie goal, auto/manual toggle with info icons.
Edit-in-place pattern for goals with forkJoin parallel loading.

Outcome: all remaining product screens complete.

---

## Phase 18 — Testing Strategy (Complete)

Testing introduced as a tool for confidence, not ceremony.

Strategy: bottom-up coverage starting with pure unit tests, then service-layer tests with mocked collaborators,
then controller tests with MockMvc and the full Spring context, then repository integration tests against a real
MySQL instance via Testcontainers. Frontend testing planned next using Vitest for service tests and TestBed for
component tests on the signal-heavy components.

Coverage in place across backend and frontend. Backend: the identity bounded context (auth controller, onboarding
controller, onboarding service, TDEE service), the progression slice of the workouts bounded context (three strategies,
resolver, service, controller, repository integration), and the measurements bounded context (body weight controller).
Frontend: Vitest with the Angular TestBed and the official HttpClient testing harness, covering the auth and onboarding
guards, the auth interceptor, the auth, identity, measurements, and workout services, and component-level tests for the
goals, weight log, and active session screens.

Outcome: tests that add value rather than friction, with documented conventions for both backend and frontend.

---

## Phase 19 — Build, Docker, and Deployment (Complete)

This phase took FITT from code-on-a-laptop to a live, HTTPS production deployment with an automated CI/CD pipeline.

What shipped:
- Multi-stage Dockerfile for the Spring Boot backend producing a slim runtime image from the jar.
- Multi-stage Dockerfile for the Angular frontend: builds the app, then copies the production output
  (dist/fitt-web/browser) into a Caddy image, so the frontend ships as one self-contained container.
- Caddy reverse proxy as the single public door: terminates TLS, routes /api/* to the backend with the
  prefix passed through intact (handle, not handle_path), serves the Angular SPA with index.html fallback
  for everything else. Automatic HTTPS via the DOMAIN env var (localhost uses Caddy's internal CA; a real
  domain triggers Let's Encrypt).
- docker-compose.prod.yml: mysql, backend, caddy. Caddy is the only service publishing host ports; backend
  (8080) and MySQL (3306) stay private on the internal Docker network. Pulls SHA-tagged images from GHCR
  rather than building. Cert state and DB data on named volumes.
- Flyway baseline migration for the production schema.
- CI pipeline (.github/workflows/ci.yml): on push to main, runs the full backend test suite on a clean
  runner (H2 unit tests plus one MySQL integration test via Testcontainers using the runner's Docker), then
  builds both images and pushes to ghcr.io tagged by commit SHA. Owner lowercased pre-emptively; on: quoted.
- Gated deploy workflow (.github/workflows/deploy.yml): manual workflow_dispatch with the image SHA as input,
  references environment: production, SSHes to the server, rewrites IMAGE_TAG in the server .env, logs into
  GHCR with the run-scoped GITHUB_TOKEN (no standing credential on the box; logs out after), pulls, and
  recreates the stack.

Production environment:
- Live at https://fittapp.dev with a real Let's Encrypt certificate.
- Domain registered via Cloudflare (DNS-only / grey cloud A records for apex and www).
- Host: Hetzner CPX21 (Hillsboro, Ubuntu 24.04). Initially deployed to DigitalOcean (Hetzner verification was
  delayed), then migrated to Hetzner to save cost; DigitalOcean droplet destroyed after cutover.
- Server hardened per the runbook: non-root deploy user, ufw (SSH, 80, 443 tcp and udp), Docker via the official script.

Key decisions and lessons:
- handle vs handle_path: backend controllers are all under /api, so the prefix must reach the backend
  untouched. handle_path would strip it and 404 everything.
- @angular/build:application nests servable output under dist/<project-name>/browser. Project name is
  fitt-web (from angular.json, NOT package.json which says "frontend"). Copying the wrong level breaks asset paths.
- Production CORS bug: the Phase 9 CORS config hardcoded http://localhost:4200, dev-correct but rejected the
  production origin with 403. Fixed by making allowed origins config-driven (fitt.cors.allowed-origins,
  env-supplied in prod). A second bug compounded it: the CORS env var was placed in the wrong compose service
  (mysql instead of backend), causing a fail-fast crash loop. Lesson reinforced: read the actual file/logs, do
  not infer.
- Required-reviewers approval gate is Enterprise-only on private repos (Pro/Team get it only on public repos).
  Using manual workflow_dispatch as the human gate instead; formal approval gate is a future item if the repo
  goes public or onto Enterprise.
- GHCR auth: the server holds no standing token. The deploy workflow authenticates with the run-scoped
  GITHUB_TOKEN over SSH and logs out after, so no credential persists on the box.

Deferred out of Phase 19 (see Phase 20 / monetization):
- CDN (Cloudflare proxy / orange-cloud), managed database migration, monitoring stack, automated DB backups.

Outcome: a complete, production-grade CI/CD setup with a live HTTPS deployment.

---

## Phase 20 — Polish and Portfolio Readiness (In Progress)

STRATEGIC DECISION (resolved 2026-06-07, governs this whole phase): the sequencing question is answered —
iOS informs Phase 20. The Angular web app is a COMPLETE THROWAWAY, kept only as a working test harness to
verify backend/API behavior until the iOS app exists, then replaced by a simple info/marketing site. iOS
(Phase 21, native SwiftUI) is the real product.

THE RULE for all Phase 20 work: keep every backend, data, and API item (the shared foundation iOS sits on);
drop all web-presentation polish (no further investment in Angular screens). The API IS the product now, so an
API-completeness audit is a high priority. Most dropped web-UI items have a backend twin that STAYS because
iOS needs it (edit-plan UI dropped but the plan update endpoint stays and must be built; add-custom-exercise UI
dropped but the create endpoint stays; edit/remove-weight UI dropped but those endpoints stay).

Completed this phase:
- Exercise library seed shipped (V2__seed_exercise_library.sql; 54 global exercises, owner_user_id NULL,
  is_custom 0). The library is GLOBAL, not per-user (service query findByOwnerUserIdIsNullOrOwnerUserId);
  seeded muscle_group strings match the MuscleGroup enum tokens exactly. Verified on a fresh volume, deployed.
- Auth 401 recovery shipped: interceptor clears the token and redirects to login on a 401 (401 only; 403
  passes through). Also fixed a compounding bug — the interceptor was attaching the stale token to the login
  request itself, so login was rejected with a malformed-token 401 before credentials were checked; now the
  token is skipped on /api/identity/auth/ endpoints. Verified live, deployed.
- Weight date "carry-over" investigated and resolved as NOT-a-bug (every layer computes the local calendar day
  correctly; the report was a genuine past-midnight log).
- Today's Lift page built (web; a demo artifact only, no further investment per the iOS-first rule). Two DRY
  extractions in the workout service, shared by workouts-main and today's-lift so they cannot drift:
  getTodaysDay (day-selection rotation) and startSessionForDay (createSession + populate, emits the session).
- Plan update endpoint shipped (PUT /workout-plans/{planId}): full structural replace of name, days-per-week,
  split type, and the whole days-and-exercises graph. Idempotent full replace, safe to retry on a flaky mobile
  connection. The surviving backend twin of the dropped edit-plan UI.
- Create-custom-exercise endpoint confirmed in place (POST), backed by the per-user owner_user_id / is_custom
  data model. The surviving twin of the dropped add-custom-exercise UI.
- GitHub Actions runtime bumped off Node 20 to Node 24 (checkout, setup-java, and the docker build/push actions
  moved to current majors) ahead of the runner cutoff.
- Request validation sweep shipped: @Valid wired onto the four workout write endpoints whose constraints were
  declared but never firing; register and login bodies validated (email format and a 12-to-72 character
  password on register, non-blank only on login) and converted to records; validation failures now return a
  per-field error map, and malformed JSON returns a clean 400 instead of a 500.
- Full codebase audit done (all 236 files; findings and a coordinated fix plan recorded). Verdict: the
  architecture, security model, cascade graph, and data integrity are sound. Every fix group from the audit is
  now shipped:
  - Progression cycle freeze fixed. Linear periodization derived the week from a session list the service
    capped at five, so the cycle froze at week two and the deload stopped firing. A week is now one full
    rotation of the split, computed from the true completed-session count, so the cycle advances on accumulated
    training. Also fixed an off-by-one that started a fresh exercise on week two.
  - Cross-user exercise references closed. The plan and session builders attached exercises with an unscoped
    lookup, so a user could reference another user's private custom exercise by id. They now resolve through a
    global-or-owned query, and a foreign custom exercise reads as not found.
  - Deleting an in-use custom exercise returns a clean 409 instead of a 500 from a foreign-key violation.
  - Correctness batch: a dedicated goal-not-found exception, JWT issuer validation on the decoder, an
    idempotent finishSession, and 404s for missing exercise entries and sets instead of 400s.
- Next-lift endpoint shipped (GET /users/{userId}/next-lift). The rotation cursor that decides which workout
  is next lived in the throwaway Angular, so iOS and the Watch would each reimplement it and drift. It now
  lives behind the API and returns one screen-shaped payload: the active plan, the day the rotation points at
  with its exercises and targets, the periodization week and deload status, and whether a session was finished
  today. This also closes the API-completeness items below (week and deload exposure, current-position,
  already-trained-today) and satisfies the API contract audit.
- CI PR gate and branch protection shipped (merged cd02e5c, PR #35). CI now runs the test suite on pull
  requests into main, not only after merge, so a PR carries a real status check. The build-and-push job is
  gated with if: github.event_name != 'pull_request', so a PR tests but never publishes an image; push and
  manual runs still build and push. An active branch ruleset on main (name: main protection) requires a pull
  request before merging and requires the test check to pass, so a red PR and a direct push to main are both
  blocked. Verified: #35 ran test green and build-and-push skipped, and a direct push to main was rejected
  (GH013, test required). This retires the elevated infra item below.

- Security and CI hardening batch shipped (2026-10-18, each its own PR through the gate):
  GitHub Actions SHA-pinning (every action pinned to a commit SHA, not a tag); image
  vulnerability scanning with Trivy in build-and-push (HIGH/CRITICAL, ignore-unfixed, blocks
  publish on a finding); dependency CVE remediation (Boot 4.0.1 to 4.0.8 plus three version
  overrides, 43 fixable HIGH/CRITICAL down to 0); CI test-suite split (fast H2 and unit tests
  on mvn test, the Testcontainers MySQL test on mvn verify); caddy fmt; a README rewrite and
  LICENSE for public release; and a Mac shell prompt that shows the git branch.
- Calorie-goal adjustment shipped (2026-10-18): the AUTO calorie goal is now the
  direction-adjusted number (bulk surplus / cut deficit / maintain), computed server-side,
  not raw TDEE. Goal management was also extracted out of onboarding into its own
  GoalController and GoalService.
- CORE muscle group plus seed shipped (2026-10-19, PR #46): CORE added to the MuscleGroup
  enum and a new Flyway V3__seed_core_exercises.sql seeding 9 core exercises, closing the
  no-core-or-ab gap. The enum change was mandatory, not cosmetic, because the read mapper
  does MuscleGroup.valueOf on the stored muscle_group string; the seed went in V3, never
  editing V2 (Flyway immutability). Verified against H2 and real MySQL 8.4.
- Conventional PR-title enforcement shipped (2026-10-21, PR #47): a pr-title workflow
  validates every PR title against Conventional Commits (amannn/action-semantic-pull-request
  pinned to the v6.1.1 commit SHA), made a required status check, so the squash subject that
  becomes permanent history is correct before merge.
- Alpine base-image CVE patch shipped (2026-10-21): apk --no-cache upgrade added to both
  Dockerfiles to clear a fixable HIGH (libexpat) the Trivy gate caught in the backend base
  image; build-and-push green again on main.
- Hetzner cloud firewall and keys-only SSH applied (2026-10-21, server-side): a Hetzner
  Cloud Firewall "fitt-edge" attached to fitt-prod as a second edge layer over ufw (inbound
  TCP 22, TCP 80, TCP 443, UDP 443, ICMP; outbound open; filters at the edge before the
  host, stateful). SSH password authentication, found enabled on port 22, was disabled, so
  SSH is keys-only. Verified live (SSH through the edge, HTTPS 200 from Caddy with h3).

Remaining in this phase (backend and infra, client-agnostic):
- Cross-group compound classifications in the seed (deadlift, RDL, shrug, face pull, step
  up): keep a single primary-mover muscle group per exercise, or move to multi-group, which
  is a schema change. The last pre-iOS front; decided and implemented when picked up.

Shipped since this list was last written (now in Completed this phase above): CORE enum plus
the V3 core-exercise seed, conventional PR-title enforcement, the Hetzner cloud firewall plus
keys-only SSH, the Alpine base-image CVE patch, the TDEE calorie-goal adjustment, image
vulnerability scanning (Trivy), the CI test-suite split, caddy fmt, GitHub Actions
SHA-pinning, dependency CVE remediation, and the git-branch shell prompt. The progression
suggestion and preference endpoints also shipped; the opt-in and increment-preference UI
placement waits for the iOS screens.

iOS-phase items (decide with the device in hand), beyond the subsection below: forgot-password
flow (needs a transactional email provider), and pre-built split templates (PPL, upper/lower,
full-body as clonable globals; source real programs, do not invent). Backend integration tests
against a standalone MySQL beyond the Testcontainers test are deferred pending Phase 21 CI.

Deferred to the iOS phase (decide with the device in hand):
- Autoregulated deload: trigger a deload when fatigue signals cluster (performance drop across sessions, slow
  reps at familiar loads) instead of on a fixed rotation cycle. Needs per-set RPE and performance-trend
  tracking the app does not have yet. The fixed rotation-cycle deload is the v1 model; this is the
  evidence-preferred upgrade for intermediate-plus lifters once the data exists.
- Weight entry date editing (move a logged entry to a different day). Resolved as not needed for now, since
  delete plus re-log is the workaround and the dates are stable; reconsider on-device.
- Auto weight-increase notification UX: how and when to surface the suggestion, and where the increment
  preference lives.

Dropped (web presentation; throwaway client): light/dark toggle, sign-up screen color, FITT-icon-routes-home,
hover icon on progression models, the today's-lift web page (demo artifact only), the Angular auth-guard
missing-token redirect (API-level behavior matters, the Angular guard does not), edit/remove-weight UI,
add-custom-exercise UI, enter-weight-on-new-day prompt, active-session web UX polish, E2E browser tests,
performance regression tests. Each dropped item whose value is real survives as a backend endpoint above.

Outcome: a complete, correct, well-documented backend/API foundation ready for the iOS client, plus a
portfolio-ready project that can be defended technically and verbally.

## Monetization Phase (Conditional — only if FITT goes public/commercial)

Tracked for the future; not scheduled.

- Freemium model: short full-access trial leading to a paywall on advanced features. Subscriptions outperform
  ads for solo indie fitness apps.
- Business email at the domain (Cloudflare Email Routing is the free receiving option; a hosted mailbox only if
  a team forms).
- Hetzner automated backups (worth the cost once there is real user data to protect).
- Cloud-config / infrastructure-as-code provisioning (fold the runbook into an automated bootstrap script).
- Cloudflare proxy / CDN (orange cloud) in front, with SSL mode Full (strict) to avoid loops.
- Formal required-reviewers approval gate (needs public repo or Enterprise).
- Secret rotation: the dev DB password and JWT secret were exposed in chat; production uses freshly generated
  values, and any further rotation should follow.

---

## Interview Readiness (cross-cutting, ongoing)

- Skills-fluency practice track: deliberately drill every technology FITT touched (Spring Boot, Spring Security,
  JPA, bounded contexts, Flyway; Angular signals/RxJS/guards/interceptors/TestBed; JUnit/Mockito/Testcontainers/
  Vitest; Docker/Compose/Caddy/GitHub Actions/GHCR; Linux hardening, DNS, ACME; MySQL, JWT) until each can be
  explained and re-derived under questioning. Method: explain cold (quiz gate), rebuild small isolated versions
  in senior-dev mode, anticipate follow-ups (why this not the alternative, what I'd do differently).
- Mock-interviewer quizzing mode drilling stack choices, architecture, request lifecycle, security, testing,
  deploy, tradeoffs.
- FITT Mega Project Guide (post-development): cover-to-cover guide teaching all of FITT from the ground up,
  PLUS a complete set of project flowcharts, every relevant file, component, flow, bounded context, request
  lifecycle, data model/ERD, and frontend flow charted so the whole system is understandable from the diagrams.
- Interview cheat sheet: trimmed (<=2 page) FITT Bible covering stack, architecture decisions, key lessons, and
  what would be done differently.
- AI tool framing: transparent about AI use, framed as interactive learning with personal accountability (the
  quiz gate is the concrete proof point), not passive reliance.

---

## Post-FITT — Second Portfolio Project (planned working agreement)

Senior-dev mode: no Claude lectures. Claude directs to official docs (angular.dev, docs.spring.io,
typescriptlang.org), reviews the work, and pushes back. Lean practice toward Angular and TypeScript before
backend topics, since frontend is the weaker side.

---

## Phase 21 — Mobile (iOS)

Native iOS client consuming the same backend API.

SwiftUI native design for mobile form factor. Workout logging, weight log, goals, and onboarding.
Secure API integration with JWT. Designed specifically for iOS interaction patterns.

Outcome: a native mobile client that demonstrates full-stack and mobile engineering capability.