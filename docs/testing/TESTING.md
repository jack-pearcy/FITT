# Testing Standards

Testing strategy and conventions for the FITT codebase. Locked in during Phase 18 as part of the backend coverage
rebuild and the frontend testing introduction.

## Purpose

This document captures the testing decisions made across the codebase so future contributors can land new tests without
re-deriving the conventions. Every pattern listed here is in active use in at least one existing test file. If a pattern
is not in this doc, it has not been adopted on this project.

## Test layers

### Backend

Three layers of tests live under `backend/src/test/java`. The split follows Maven's conventional directory layout:
source under `src/main/java`, tests under `src/test/java`.

**Service unit tests.** Pure JUnit 5 plus Mockito plus AssertJ. No Spring context, no embedded database, no Spring
profile activation. The service-under-test is instantiated by Mockito via `@InjectMocks` and its dependencies are
provided as `@Mock` stubs. These tests run in single-digit milliseconds because nothing wires up.

**Repository integration tests.** Spring Boot slice tests with `@DataJpaTest` against the H2 in-memory database. These
verify that JPA queries, derived-finder method names, and persistence cascades actually behave the way the service layer
assumes. They run slower than unit tests because Spring starts a context, but the slice keeps that context tight.

**Controller tests.** Full Spring Boot tests with `@WebMvcTest` (or where end-to-end behavior matters, `@SpringBootTest`
with `MockMvc`). The service layer is mocked, but request mapping, validation, status codes, and JSON shape are
exercised against real Spring infrastructure.

### Frontend

One layer, colocated. Spec files live next to the source file they test under `frontend/src/app`, named
`<source>.spec.ts`. Angular's build configuration handles separation: `tsconfig.app.json` excludes `**/*.spec.ts` from
production bundles, `tsconfig.spec.json` includes them for the test runner. No separate test directory tree.

The runner is Vitest, configured by Angular's `@angular/build:unit-test` builder. `npm test` triggers `ng test`, which
calls Vitest with jsdom as the DOM environment. Globals are enabled via `vitest/globals` in `tsconfig.spec.json`, so
`describe`, `it`, `expect`, `beforeEach`, and `afterEach` work without imports. By convention this codebase imports them
explicitly anyway, for IDE refactoring support and to make the test runner choice visible at the top of each file.

## File location and naming

Backend test files mirror the source path under `src/test/java` instead of `src/main/java`. A service at
`backend/src/main/java/com/fitt/workouts/service/WorkoutSessionServiceImpl.java` has its test at
`backend/src/test/java/com/fitt/workouts/service/WorkoutSessionServiceImplTest.java`.

Frontend spec files sit in the same folder as their source file with a `.spec.ts` suffix. A service at
`frontend/src/app/core/services/auth.service.ts` has its spec at `frontend/src/app/core/services/auth.service.spec.ts`.

Test method names follow the pattern `subject_scenario_expectedOutcome`. Examples from this codebase:

- `createSession_validRequest_setsAllFieldsIncludingPlanId`
- `getSession_notOwned_throwsAccessDeniedException`
- `logSet_validInputs_appendsSetAndMarksCompletedTrue`

The frontend uses describe block titles plus the test name as a single sentence, which Vitest concatenates in output.
The backend uses the underscored method name only because JUnit does not have nested describe blocks.

## Test structure

Every test follows Arrange-Act-Assert in that order. The structure is implicit in most tests but visible in any test
that has substantial setup. Comments inside test bodies are kept short and call out non-obvious decisions, not the
structure itself.

One behavior per test. A single `it` or `@Test` block verifies one specific outcome. Multiple assertions in a single
test are acceptable when they all describe one outcome (asserting both the URL and the body of a POST request is one
outcome: the request was made correctly).

## What gets tested

**Backend service layer.** Every public method on every service. The unit test verifies the logic in isolation from the
database and from Spring. Branches, exception paths, and the ownership-guard pattern (`loadAndVerify` style methods) all
get explicit coverage.

**Backend repository layer.** Custom derived-finder methods and any non-trivial JPA query. Standard `JpaRepository`
methods like `findById` and `save` are tested through the service layer rather than independently.

**Backend controller layer.** One test per endpoint covering the happy path, plus one test per documented error
response (validation failure, 404, 403, 409). Authorization checks at the controller level get their own tests.

**Frontend services.** Every public method. URL, HTTP method, request body shape (for write methods), query parameter
shape, and that the typed response reaches the subscriber. No assertion on Angular's internal HTTP plumbing.

**Frontend guards.** Every branch of the guard function. Synchronous returns are asserted directly. Observable returns
are subscribed and the captured value is asserted.

**Frontend interceptors.** Every category of behavior: conditional headers, unconditional headers, uniqueness across
requests, and pass-through of the underlying request (URL, method, body, existing headers). Interceptors run on every
request, so a regression here has wide blast radius.

**Frontend components.** Only the components with signal-heavy or interaction-heavy logic worth testing in isolation.
Pure presentation components are excluded by design; testing them via TestBed mostly verifies that Angular renders
templates, which is Angular's job. WeightLog, Goals, and ActiveSession are tested. Login, register, settings, and other
presentation-only components are not.

**Frontend pipes.** Pipes with branching logic. Trivial pass-through pipes are excluded.

**What does not get tested.** Generated code. Pure presentation HTML. Angular framework behavior. Third-party library
internals. Configuration files (covered by the application itself failing to start if they are wrong).

## Backend patterns

### No Spring context for service unit tests

The load-bearing rule for backend test speed. A pure JUnit plus Mockito test runs in single-digit milliseconds. A
Spring-context test takes hundreds of milliseconds to start the context plus the test runtime itself. Across a 130-test
suite the difference is seconds versus minutes.

The rule is enforced by convention rather than tooling: service unit tests live in their own file with
`@ExtendWith(MockitoExtension.class)` only. No `@SpringBootTest`, no `@DataJpaTest`, no `@MockBean`. If a test needs
Spring, it is a repository or controller test, not a service unit test.

### Mockito setup

`@ExtendWith(MockitoExtension.class)` on the class. `@Mock` for each collaborator. `@InjectMocks` for the service under
test. Mockito 5 in strict-stubs mode by default catches unused stubs as test failures, which keeps the test setup
honest.

### Database-generated IDs

JPA entities use `@GeneratedValue(strategy = GenerationType.IDENTITY)` for primary keys. In unit tests where no database
runs, the entity is constructed without an id and the id field needs to be set explicitly so service code that looks up
by id can find the entity. The pattern:

```java
ReflectionTestUtils.setField(entity, "id",DB_GENERATED_VALUE);
```

This sets the private `id` field directly. The alternative is exposing a package-private setter, which leaks test-only
access into production code.

### Hierarchical entity assertions

Where a service constructs and saves an aggregate root with nested children (a `WorkoutPlanEntity` with days and
exercises, a `WorkoutSessionEntity` with entries and sets), the test captures the saved entity with
`ArgumentCaptor.forClass(...)` and walks the captured object graph to verify the structure was built correctly.

```java
ArgumentCaptor<WorkoutSessionEntity> captor = ArgumentCaptor.forClass(WorkoutSessionEntity.class);

verify(sessionRepository).

save(captor.capture());
WorkoutSessionEntity saved = captor.getValue();

assertThat(saved.getExerciseEntries()).

hasSize(1);

assertThat(saved.getExerciseEntries().

get(0).

getSetEntries()).

hasSize(1);
```

### Save-returns-argument pattern

For tests that need to verify both that save was called and that the post-save service code ran (typically the
`toResponse(saved)` mapping at the end of a write method), the save mock returns the argument that was passed to it:

```java
when(sessionRepository.save(any(WorkoutSessionEntity.class)))
        .

thenAnswer(invocation ->invocation.

getArgument(0));
```

This lets the service code complete normally because `save` returns a valid entity for the response mapper to operate
on.

### BigDecimal assertions

`isEqualByComparingTo` for BigDecimal, not `isEqualTo`. The difference is scale: `new BigDecimal("100")` and
`new BigDecimal("100.00")` are not equal under `equals` but are equal under `compareTo`. The compareTo-based assertion
catches a value regression while ignoring scale-only differences that may arise from BigDecimal arithmetic.

### Exception assertions

AssertJ's `assertThatThrownBy` for both type and (where the message is part of the contract) message:

```java
assertThatThrownBy(() ->service.

getSession(USER_ID, SESSION_ID))
        .

isInstanceOf(AccessDeniedException .class);
```

For tests that verify an exception path also skipped a side effect, follow up with
`verify(repo, never()).save(any(...))` to assert the side effect did not happen.

## Frontend patterns

### Provider setup

Service tests, guard tests, and component tests all set up `TestBed.configureTestingModule` with `provideHttpClient()`
plus `provideHttpClientTesting()` when the service or component makes HTTP calls. The first registers the real
HttpClient pipeline. The second registers the test backend that intercepts requests and exposes them via
`HttpTestingController`.

For functional guards and interceptors, the test calls them via
`TestBed.runInInjectionContext(() => myGuard(route, state))`. Without the injection context, the `inject(MyService)`
calls inside the function throw.

### Mocking services via useValue

```typescript
const mockAuth = {getUserId: vi.fn()};

TestBed.configureTestingModule({
    providers: [
        {provide: AuthService, useValue: mockAuth}
    ]
});
```

Partial mocks are acceptable. The mock only needs to expose the methods the system-under-test calls. Angular's
`useValue` accepts `any`, so TypeScript does not complain about the mock missing fields from the full service interface.

### Mock reset

`vi.resetAllMocks()` in `beforeEach`. This wipes both call history and any `mockReturnValue` set during a previous test.
The alternative `vi.clearAllMocks()` only wipes call history but keeps implementations, which leaks state across tests.
Reset is the safe default for this codebase.

### HTTP testing controller

`HttpTestingController.expectOne(url)` matches against the full URL including query parameters. For tests that need to
assert on the URL and the params separately, use the predicate form:

```typescript
const req = controller.expectOne(
    r => r.url === '/api/foo' && r.params.get('days') === '7'
);
```

`controller.verify()` in `afterEach` catches HTTP requests that were never matched. A leaked subscription that fires an
extra request would silently pass other tests but fail at `verify()`, which is the safety net.

### Signal testing

Read a signal by calling it as a function: `component.mySignal()`. Computed signals work the same way: set the source
signals, then read the computed signal. No `detectChanges` needed for pure signal reads because signals are not part of
the change detection cycle.

### Component lifecycle

`TestBed.createComponent(MyComponent)` constructs the component instance, running field initializers and the
constructor. `fixture.detectChanges()` triggers the first change detection cycle, which calls `ngOnInit` if it has not
run yet. Tests that only need signal state and methods can skip `detectChanges`. Tests that need `ngOnInit` to run must
call it.

### Observable mocks

`of(value)` for synchronous success. `throwError(() => new Error(...))` for synchronous failure. Both emit in the same
tick as the subscribe call, so test assertions can read the resulting state immediately after the service method
returns.

For multiple sequential calls returning different values, `mockReturnValueOnce` queues per-call returns. The first call
to the mock returns the first queued value, the second call returns the second, and so on. The default set by
`mockReturnValue` applies after the queue is empty.

### Fake timers

`vi.useFakeTimers()` in `beforeEach` for tests that exercise `interval`, `setTimeout`, or `Date.now()`.
`vi.advanceTimersByTime(N)` instantly fires every scheduled callback whose deadline falls within the advanced window.
`vi.setSystemTime(date)` pins `new Date()` to a known value. `vi.useRealTimers()` in `afterEach` restores normal
behavior so the next test starts clean.

This pattern is required for the WeightLog and ActiveSession component tests because both have timer-based logic.
Without fake timers, those tests would have to actually wait real seconds, which is unacceptable for a unit test suite.

### Opaque return assertions

For Angular framework types that are tedious to construct (UrlTree, ActivatedRouteSnapshot, HttpRequest), tests use
sentinel objects cast to the target type and assert on reference equality:

```typescript
const fakeUrlTree = {__sentinel: 'login-tree'} as unknown as UrlTree;
mockRouter.createUrlTree.mockReturnValue(fakeUrlTree);

const result = TestBed.runInInjectionContext(() => authGuard(route, state));
expect(result).toBe(fakeUrlTree);
```

The sentinel proves the function returned exactly what the dependency produced, which is a stronger contract than "
called with the right arguments".

## Anti-patterns

The following patterns have been considered and rejected on this project.

**Spring context in service unit tests.** Slows the suite, contributes nothing to confidence at the unit level.

**TestBed in pipe tests.** Pure pipes are pure functions. `new PipeName().transform(...)` is sufficient.

**Asserting on Angular framework behavior.** If the test is verifying that Angular's HttpClient sends a request when you
call `http.get`, the test is testing Angular. Not in scope.

**Testing private methods directly.** Private methods are tested through their public callers.

**Sleeping in tests.** Hard waits via `setTimeout` or real-time delays. Use fake timers for time-dependent code and
synchronous mocks for observables.

**Shared mutable state across tests.** Every test gets fresh mocks via `vi.resetAllMocks` and a fresh TestBed via
Angular's automatic per-test reset. Module-level mutable state is forbidden.

**Magic numbers in assertions without context.** When the expected value is a domain constant (a default rest period of
90 seconds, a default sets count of 3), the assertion comments call out why that number is the contract. The number
itself stays inline because it is what the assertion is checking.

## Running the tests

Backend, full suite: