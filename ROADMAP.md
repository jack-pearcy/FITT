# FITT Project Roadmap

Spring Boot + Angular Full-Stack Application

## Purpose

The FITT project is a full-stack fitness application built as a professional learning and portfolio project.
The goal is not speed, shortcuts, or demo-level code. The goal is correct architecture, clear reasoning, and
industry-standard development practices.
Each phase builds intentionally on the previous one. Nothing is added early, and no layer exists without a clear reason.

---

## Phase 0 — Foundations

This phase establishes a stable baseline before any real development begins.

The repository is structured as a monorepo containing both frontend and backend.
Git is initialized at the correct root and kept clean from the start. A Spring Boot application is created and verified
to start successfully. The test context loads without error.
The backend package structure is scaffolded but intentionally left empty to avoid premature design decisions.

Outcome: a clean, trusted starting point with no architectural debt.

---

## Phase 1 — Backend Architecture Design

This phase focuses on design rather than implementation.

The backend follows a layered architecture with clear separation of concerns. Each layer has a defined role:
configuration, controllers, services, repositories, domain entities, and DTOs.
Communication boundaries are defined early so that responsibilities do not blur later. Naming conventions and package
layout are established up front, and Spring’s component scanning behavior is fully understood.

Outcome: a clear mental model of how the backend is structured and why.

---

## Phase 2 — Domain Modeling

The domain model forms the core of the application.

Business concepts are identified first and then translated into domain entities. Fields are chosen deliberately with
correct data types, nullability, and constraints. JPA annotations are introduced carefully and explained in detail,
including entity identity, lifecycle, and persistence context. At this stage, repositories and services are
intentionally avoided to keep focus on the model itself.

Outcome: a solid understanding of what JPA entities represent and how they behave at runtime.

---

## Phase 3 — Data Access Layer

This phase introduces persistence in a controlled way.

Spring Data JPA is added, and repositories are created for the domain entities. The difference between repositories and
traditional DAOs is explained. Query method naming conventions are covered, along with basic transaction concepts. Lazy
versus eager loading is introduced with attention to real-world consequences.

Outcome: a clear understanding of how data is stored, retrieved, and managed.

---

## Phase 4 — Service Layer

This phase introduces application behavior.

Services are used to coordinate business logic rather than acting as dumping grounds. Controllers remain thin, and
persistence concerns remain in repositories. Transactional boundaries are introduced where appropriate. Validation logic
is separated from persistence, and DTO mapping is handled explicitly rather than hidden behind magic.

Outcome: business logic that is readable, testable, and maintainable.

---

## Phase 5 — Controllers and API Design

This phase exposes functionality through stable APIs.

REST principles are applied consistently. Endpoints are named intentionally, and HTTP methods are used correctly.
Request and response DTOs are kept separate. Validation, error handling, and status codes are handled in a predictable
and professional way.

Outcome: APIs that look and behave like real production systems.

---

## Phase 6 — Backend Security

Security is designed into the system rather than added later.

Authentication and authorization are clearly separated. JWT concepts are introduced, along with Spring Security’s
architecture and filter chain. Endpoints are protected intentionally, and the backend is kept stateless.

Outcome: a secure backend with a clear security model.

---

## Phase 7 — Frontend Foundations

This phase establishes Angular fundamentals.

Angular architecture is explained from first principles. The purpose of TypeScript is made clear. Components, services,
and modules each have defined roles. The Angular CLI and build process are understood rather than treated as black
boxes. Environment configuration is handled properly.

Outcome: Angular becomes predictable rather than confusing.

---

## Phase 8 — Frontend Feature Development

This phase focuses on real user-facing functionality.

Routing and guards are introduced. Forms are handled correctly, with an understanding of when to use template-driven or
reactive approaches. The HTTP client is integrated with the backend. Observables are explained conceptually. Loading
states, error states, and component communication are handled cleanly.

Outcome: a frontend that behaves like a real application, not a demo.

---

## Phase 9 — Full-Stack Integration

This phase connects frontend and backend into a single system.

CORS is explained and configured correctly. API proxying is set up for local development. Environment separation is
enforced. End-to-end data flow is traced and debugged across layers.

Outcome: the ability to reason about the entire system as one unit.

---

## Phase 10 — Testing Strategy

Testing is introduced as a tool for confidence, not ceremony.

The difference between unit tests and integration tests is made clear. Spring Boot testing annotations are explained.
Testcontainers are introduced conceptually. Frontend testing is covered at a high level. Decisions are made about what
should and should not be tested.

Outcome: tests that add value rather than friction.

---

## Phase 11 — Build, Docker, and Deployment

This phase focuses on delivery.

The Maven lifecycle is revisited in context. Docker fundamentals are explained, including image creation and container
behavior. Environment variables are handled correctly. Differences between local and cloud environments are understood.
Deployment is treated as a process, not a mystery.

Outcome: a clear understanding of how software leaves the development machine.

---

## Phase 12 — Polish and Portfolio Readiness

This final phase prepares the project for external review.

The codebase is reviewed for quality and clarity. Dead code is removed. Naming is improved. Documentation is written
clearly. Architectural decisions are documented. The project is prepared to be explained confidently in interviews.

Outcome: a portfolio-ready project that can be defended technically and verbally.
