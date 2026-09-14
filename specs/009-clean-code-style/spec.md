# Feature Specification: Code Style Refactoring - FQCN Elimination and Stream API Modernization

**Feature Branch**: `009-clean-code-style`

**Created**: 2026-09-15

**Status**: Draft

**Input**: User description: "code style - Locate any occurances of fully qualified class names that are used in a class body and try to use the class name only with imports unless FQCN is needed due to name collisions. Identify any code with loops and nested if structures that can be replaced with java stream APIs."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Eliminate Inline Fully Qualified Class Names in Favor of Imports (Priority: P1)

As a maintainer and developer working on the codebase, I want all classes referenced in method bodies and member declarations to use concise simple class names backed by file-level `import` statements (unless an explicit name collision requires qualification), so that the code is readable, idiomatic, and adheres to Java clean code best practices.

**Why this priority**: Inline fully qualified class names (e.g., `com.fasterxml.jackson.databind.JsonNode`, `java.util.List.of`, `java.util.Map.of`) clutter method bodies, reduce visual clarity, and deviate from standard Java conventions.

**Independent Test**: Can be verified by scanning the codebase for inline package prefixes in class bodies and ensuring each referenced type is imported at the top of the file without compiler or lint errors.

**Acceptance Scenarios**:

1. **Given** a class body containing inline fully qualified class names (such as `com.fasterxml.jackson.databind.JsonNode` or `java.util.List`), **When** no name conflict exists with another class in the same file or package, **Then** the fully qualified name is replaced with the simple class name and an explicit `import` is added to the file's import declarations.
2. **Given** a class where two imported types share the same simple name (a genuine name collision), **When** inspected, **Then** one type is imported and the colliding type retains its fully qualified name to avoid ambiguity.
3. **Given** test classes utilizing inline `java.util.List.of` or `java.util.Map.of`, **When** refactored, **Then** standard `import java.util.List;` and `import java.util.Map;` (or static factory imports where appropriate) are used instead.

---

### User Story 2 - Modernize Iteration and Filtering Logic Using Java Stream APIs (Priority: P1)

As a software engineer reading or maintaining service logic, I want imperative `for`/`while` loops accompanied by nested `if` conditions to be refactored into declarative Java Stream pipelines (`filter`, `map`, `anyMatch`, `findFirst`, `collect`), so that intent is immediately clear, boilerplate accumulator variables are minimized, and data transformations are concise and functional.

**Why this priority**: Imperative loops with nested conditionals obscure business intent, require mutable accumulator state, and introduce boilerplate compared to standard declarative Java Stream operations.

**Independent Test**: Can be verified by inspecting target methods in `AvailabilityService`, `WaitingListService`, `KeycloakRealmRoleConverter`, and `ValidationExceptionHandler`, confirming they utilize Stream expressions while maintaining exact functional equivalence and passing all unit tests.

**Acceptance Scenarios**:

1. **Given** `AvailabilityService` checking single table and table combination availability with nested loops and boolean flags, **When** refactored, **Then** the logic uses `stream().anyMatch()` / `noneMatch()` pipelines to evaluate table and combination availability declaratively.
2. **Given** `WaitingListService` finding the oldest matching FIFO candidate using a `for` loop with nested bounds checking, **When** refactored, **Then** the search uses `waiting.stream().filter(...).findFirst()` to locate and process the eligible candidate.
3. **Given** `KeycloakRealmRoleConverter` extracting roles using a loop with nested `instanceof` and blank checks, **When** refactored, **Then** the role conversion is expressed via a clean stream pipeline (`filter`, `map`, `forEach`).
4. **Given** `ValidationExceptionHandler` processing nested parameter validation errors using nested loops/callbacks and mutable list additions, **When** refactored, **Then** errors are flattened and transformed into immutable lists using `stream().flatMap(...).toList()`.
5. **Given** persistence logic mapping collections into entities one-by-one in a loop, **When** refactored, **Then** entities are mapped via streams and persisted using batch repository operations (e.g. `saveAll`) where appropriate.

---

### User Story 3 - Guarantee Behavioral Equivalence and Zero Functional Regressions (Priority: P1)

As a quality engineer, I want all refactorings to maintain strict operational, algorithmic, and transactional equivalence with the current codebase, so that refactored code passes all existing test suites without behavioral changes.

**Why this priority**: Code style refactorings must never alter functional behavior, breaking contracts, or transaction boundaries.

**Independent Test**: Can be verified by running the complete Maven test suite (`./mvnw test`) across all 10 modules, verifying 100% pass rate with zero test modifications needed.

**Acceptance Scenarios**:

1. **Given** the refactored codebase, **When** the complete automated test suite is executed across all microservices, **Then** all tests pass with 0 failures and 0 errors.
2. **Given** loops with sequential ordering guarantees (such as transactional outbox dispatch with short-circuiting error halts), **When** analyzed, **Then** sequential error handling and termination semantics are strictly preserved.

---

### Edge Cases

- **Name Collisions**: When a class references two types with identical simple names (e.g., `org.springframework.cache.Cache` and `com.github.benmanes.caffeine.cache.Cache`), one must remain fully qualified.
- **Checked Exceptions in Lambdas**: Where loop bodies throw checked exceptions (e.g., in serialization loops or synchronous Kafka dispatch), standard try-catch constructs or helper methods must preserve error handling rather than introducing unsafe lambda wrapping.
- **Short-Circuit Semantics**: When a loop uses `break` upon finding the first match, stream equivalents must use short-circuiting terminal operations (e.g., `findFirst()`, `anyMatch()`) to avoid unnecessary iterations or evaluations.
- **Null-Safety**: Stream pipelines operating on collections that may be null or contain null elements must maintain explicit null checks identical to the original imperative code.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Scan all Java source files across `services/`, `gateway/`, and `common/` for inline fully qualified class names used in class bodies, replacing them with simple class names and top-level `import` statements whenever no name collision exists.
- **FR-002**: Refactor `AvailabilityService.checkAvailability` table availability checks and occupancy ID collection to use Java Streams (`map`, `collect(toSet())`, `anyMatch`, `noneMatch`).
- **FR-003**: Refactor `WaitingListService.processCancellationOpening` FIFO search loop to use Stream filtering and `findFirst()`.
- **FR-004**: Refactor `KeycloakRealmRoleConverter.convert` role mapping across all microservices to use a functional Stream pipeline (`filter`, `map`, `forEach`).
- **FR-005**: Refactor `ValidationExceptionHandler.handleHandlerMethodValidation` error flattening across all services to use `flatMap()` streams.
- **FR-006**: Refactor entity transformation loops in event listeners (`AvailabilityEventListener`) to stream mappings and `saveAll` batch repository calls where applicable.
- **FR-007**: Ensure all code changes preserve formatting, adhere to project conventions, and pass compilation and automated testing without regressions.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of non-colliding inline fully qualified class names in class bodies are replaced with top-level imports across the codebase.
- **SC-002**: All identified loop-and-nested-if lookup/transformation patterns in the service and configuration layers are modernized to stream pipelines.
- **SC-003**: 100% of automated test suites across all 10 modules pass (`./mvnw test`) with 0 failures and 0 errors.
- **SC-004**: Zero functional regressions in API responses or event processing.
