# FITT

FITT is a production-grade, API-first fitness and strength-training platform: a Java 21 / Spring Boot 4 REST backend with JWT security, a full CI/CD pipeline, container vulnerability scanning, and a live HTTPS deployment.

Built solo as a software-engineering portfolio project, with an emphasis on clean architecture, real-world development practices, and security as a design constraint rather than an afterthought.

Live: https://fittapp.dev

---

## What It Does

A strength-training and body-metrics tracker. Users onboard with biometrics and goals, receive an automatically calculated calorie target, build workout plans, run and log training sessions, and get progression suggestions from a pluggable progression engine. The system is API-first, so multiple clients read from one authoritative source of truth.

---

## Tech Stack

- Backend: Java 21, Spring Boot 4, Spring Security (OAuth2 Resource Server / JWT), Spring Data JPA, Hibernate, Bean Validation
- Database: MySQL 8.4 in production, H2 for unit tests, Flyway for versioned migrations
- Testing: JUnit 5, Mockito, AssertJ, Spring MockMvc, Spring Security Test, Testcontainers (real MySQL)
- Infrastructure and DevOps: Docker (multi-stage, non-root), GitHub Actions (CI/CD), GitHub Container Registry, Trivy (image scanning), Caddy (reverse proxy, automatic HTTPS), Hetzner Cloud
- Frontend: Angular and TypeScript, a throwaway test harness. A native iOS client is the planned primary consumer.

---

## Architecture

Domain-driven design with bounded contexts, each as its own package with a layered structure (controller, service, domain/entity, repository):

- identity: registration, login, JWT auth, onboarding (biometrics and goals), and TDEE / calorie-goal calculation
- workouts: exercise library and custom exercises, workout plans, sessions and set logging, and the progression engine
- measurements: body-weight logging, scoped per user
- shared: centralized exception handling (RFC 7807 Problem Details), JWT and Spring Security configuration, custom JSON auth entry points, and system health

The API is designed so that logic a thick client might otherwise duplicate is pushed server-side, keeping all clients consistent. The clearest example is the next-lift endpoint, which computes the training rotation cursor on the server and returns a single screen-shaped payload (active plan, next day with targets, periodization week, deload status) so no client reimplements that logic.

---

## Security

- Stateless JWT authentication via Spring Security OAuth2 Resource Server, with issuer validation and configurable secret and token TTL
- Defense-in-depth authorization: per-method ownership checks in controllers, backed by service-layer verification, so a user cannot reach another user's data by guessing an ID
- Request validation with Bean Validation across write endpoints, returning RFC 7807 Problem Details responses
- Security response headers (content-type-options, frame-options, cache controls)
- Supply-chain hardening: all third-party GitHub Actions are pinned to immutable commit SHAs, including the Trivy action, which was itself the target of a 2026 tag-hijacking attack

---

## Testing and CI/CD

- 160 automated tests (157 fast unit/slice and 3 Testcontainers integration). The Maven Surefire/Failsafe split runs the fast unit and H2-backed tests first; a Testcontainers MySQL 8.4 container then runs the integration tests, so fast failures surface before anything heavier starts.
- Pipeline (GitHub Actions): CI runs on every pull request and push to main. On merge to main, multi-stage non-root Docker images are built, scanned with Trivy for fixable HIGH/CRITICAL CVEs, and published to GHCR only if the scan passes, so a vulnerable image never reaches the registry.
- Deployment: a separate, manually-gated workflow deploys a chosen image SHA over SSH using a run-scoped registry token (no standing credentials on the server), behind Caddy with automatic Let's Encrypt HTTPS. This is human-approved Continuous Delivery by design.
- Branch protection: main requires a passing CI status check and a pull request before merge, and direct pushes are blocked. Conventional Commits and squash merges keep a linear history.

---

## Running the Project Locally

Backend, from the `backend` directory:

    ./mvnw spring-boot:run

The backend starts on port 8080.

Frontend, from the `frontend` directory:

    npm install
    ng serve

The frontend starts on port 4200.

---

## Project Status

The backend, security model, CI/CD pipeline, and live deployment are complete and running. A native iOS client (SwiftUI) is planned as the primary consumer, alongside a nutrition domain and workout program templates.

Project direction, development phases, and architectural decisions are documented in `roadmap.md`.

---

## License

Copyright (c) 2026 Jack Pearcy. All rights reserved. See `LICENSE`.