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
Active workout plan displayed in full (e.g. 16-week plan). Each day viewable and editable.
Dropdown to switch between saved plans with activate/delete options.
Create plan flow: days per week, day scheme (M-F/EOD/3-on-1-off/Custom),
split template (PPL/Upper-Lower/Full Body/Custom), exercise population from library.
Exercise library: fixed broad library by muscle group (Chest/Back/Shoulders/Arms/Legs/Glutes)
plus user-created custom exercises stored locally with edit and delete options.

**Active Session Screen**
Triggered by Today's Lift CTA. Global session timer from start.
Exercise rows with: exercise name, sets, reps, weight, RPE columns, per-row rest countdown timer.
After each set: green checkmark, row greys out, next row activates.
Finish button at bottom leads to workout summary: total volume, total time, PRs hit,
next session weight suggestions from progressive overload algorithm.
Progression model selectable by user with info icon explaining each model.

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

## Phase 9 — Full-Stack Integration

This phase connects frontend and backend into a single system and prepares the infrastructure for all future phases.

CORS is explained and configured correctly on the backend. API proxying confirmed as development-only.
Environment separation enforced. Correlation ID tracing implemented across Angular interceptor and Spring filter.
End-to-end request tracing verified across layers.

Outcome: the ability to reason about the entire system as one unit.

---

## Phase 10 — Domain Expansion: Identity and Onboarding

Real user registration replaces the hardcoded uid=1L placeholder throughout the system.

User registration endpoint, biometrics entity, goals entity, TDEE calculation service, and onboarding API.
UserEntity introduced with proper identity management. JWT uid claim wired to real database user IDs.
All ownership enforcement updated to use real principal identifiers.

Outcome: real multi-user identity with secure, user-scoped data access.

---

## Phase 11 — Domain Expansion: Workouts Full Implementation

The Workout placeholder is replaced with the full domain model from Phase 2.

WorkoutSession, ExerciseEntry, and SetEntry hierarchy implemented in full. Workout plan entity with split templates.
Exercise library with fixed entries by muscle group. Custom exercise support. Full backend: entities, repositories,
services, controllers, and ownership enforcement at every level.

Outcome: a complete, production-grade workouts domain.

---

## Phase 12 — Domain Expansion: Measurements

Body weight logging introduced as a first-class domain.

BodyWeightEntry entity, daily logging endpoint, 7-day average calculation service.
Historical query support for 30/60/90 day views. Ownership enforced.

Outcome: a complete measurements domain ready for frontend integration.

---

## Phase 13 — Progressive Overload Algorithm

Science-backed progression logic implemented as a backend service.

Literature review: double progression, RPE-based autoregulation, linear periodization.
Selectable progression model per user. Post-session analysis generates next session weight suggestions.
Algorithm service designed for extensibility as more models are added.

Outcome: a defensible, evidence-based progression engine.

---

## Phase 14 — Frontend Rebuild: Authentication and Onboarding

Login and sign up screens rebuilt to production quality.

Slideshow onboarding flow wired to Phase 10 backend. Units preference, biometrics, goals, TDEE display.
Smooth screen transitions. Form validation and error handling at every step.

Outcome: a complete, polished authentication and onboarding experience.

---

## Phase 15 — Frontend Rebuild: Main Screen and Navigation

Tab navigation shell established as the permanent application frame.

Main screen: session streak, 7-day weight average, calorie goal, Today's Lift CTA fully wired to backend.
Settings screen: personal info, contact info, units, notifications, delete account.

Outcome: a fully navigable application shell.

---

## Phase 16 — Frontend Rebuild: Workouts

Full workout plan builder UI with all active session features.

Exercise library with muscle group sections and custom exercise management.
Active session screen: global timer, per-row rest timer, set logging, green checkmark flow,
Finish button, workout summary screen with volume, time, PRs, and next session suggestions.
Date navigator (MyFitnessPal style) for session history.

Outcome: the core product feature, fully functional and polished.

---

## Phase 17 — Frontend Rebuild: Weight Log and Goals

Weight log tab with 7/30/60/90 day views and daily prompt on app open.
Goals tab: Bulk/Cut/Maintain, target weight, timeline, TDEE calorie goal, auto/manual toggle with info icons.

Outcome: all remaining product screens complete.

---

## Phase 18 — Testing Strategy

Testing introduced as a tool for confidence, not ceremony.

Unit and integration testing across all domains. Testcontainers for database-backed integration tests.
Frontend component testing. Decisions made about what should and should not be tested.

Outcome: tests that add value rather than friction.

---

## Phase 19 — Build, Docker, and Deployment

This phase focuses on delivery.

Maven lifecycle revisited. Docker fundamentals explained including image creation and container behavior.
Environment variables handled correctly. Production CORS locked to deployed frontend URL.
Differences between local and cloud environments understood.

Outcome: a clear understanding of how software leaves the development machine.

---

## Phase 20 — Polish and Portfolio Readiness

This phase prepares the project for external review.

Codebase reviewed for quality and clarity. Dead code removed. Naming improved.
Architectural decisions documented. Study guide produced covering the full stack.
Project prepared to be explained confidently in interviews.

Outcome: a portfolio-ready project that can be defended technically and verbally.

---

## Phase 21 — Mobile (iOS)

Native iOS client consuming the same backend API.

SwiftUI native design for mobile form factor. Workout logging, weight log, goals, and onboarding.
Secure API integration with JWT. Designed specifically for iOS interaction patterns.

Outcome: a native mobile client that demonstrates full-stack and mobile engineering capability.
