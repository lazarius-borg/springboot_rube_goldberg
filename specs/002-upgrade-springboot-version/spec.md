# Feature Specification: Upgrade Spring Boot Version

**Feature Branch**: `002-upgrade-springboot-version`

**Created**: 2026-08-31

**Status**: Draft

**Input**: User description: "upgrade springboot version - SpringBoot version 4.1.1 is available, and the one used in the project is 3.4.3. Update the SpringBoot version, and refactor to code to use recommended approach in the 4.1.1 version as recommended by Spring documentation."

## Clarifications

### Session 2026-08-31
- Q: How should companion ecosystem dependencies be aligned alongside the framework upgrade? → A: Automatically align all companion BOMs (Spring Cloud, Springdoc OpenAPI, Testcontainers, Flyway) to compatible release trains.
- Q: How should test mock annotations be modernized across the test suites? → A: Modernize all mock annotations to @MockitoBean / modern slice configurations.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Platform-Wide Framework Upgrade (Priority: P1)

As a platform engineer and developer, I want all microservices, parent reactor POMs, and shared modules to build, compile, and run on the updated modern Spring Boot baseline so that the platform benefits from current runtime optimizations, security patches, and modern framework conventions.

**Why this priority**: Core platform upgrade is essential for all dependent microservices to function correctly and remain maintainable.

**Independent Test**: The entire Maven reactor build compiles, starts all microservices, passes all automated tests, and exposes all operational Actuator and OpenAPI endpoints without errors.

**Acceptance Scenarios**:

1. **Given** the multi-module Maven reactor project, **When** running the reactor build, **Then** all 8 microservices and gateway compile without deprecation warnings using the updated framework version.
2. **Given** any running microservice, **When** invoking its `/actuator/health` endpoint, **Then** the service reports status `UP` with valid readiness and liveness probe states.
3. **Given** any running microservice, **When** navigating to `/swagger-ui.html`, **Then** the interactive OpenAPI interface renders all operations correctly.

---

### User Story 2 - Idiomatic Configuration & Annotation Modernization (Priority: P2)

As a software engineer, I want the codebase refactored to align with the latest recommended declarative patterns, configuration properties, and testing conventions so that technical debt is eliminated.

**Why this priority**: Ensures long-term maintainability, idiomatic style, and eliminates obsolete or superseded programming patterns.

**Independent Test**: Code analysis and test suites verify that modern declarative annotations, configuration property namespaces, and testing slice annotations operate cleanly without fallback compatibility warnings.

**Acceptance Scenarios**:

1. **Given** microservice configuration files (`application.yml`), **When** the application starts up, **Then** no deprecated property warnings are emitted in the server logs.
2. **Given** security, web, and messaging configurations, **When** inspecting bean definitions, **Then** all beans utilize modern component registrations and fluent DSLs.
3. **Given** the unit and integration test suite, **When** executing tests, **Then** modern test annotations and mock injection mechanisms execute cleanly.

---

### User Story 3 - End-to-End Choreography & Functional Validation (Priority: P3)

As a restaurant customer or manager, I want the end-to-end reservation, availability caching, waiting list matchmaking, email notifications, and analytics choreography to function identically after the framework upgrade.

**Why this priority**: Confirms zero regression in domain business capabilities across all microservice boundaries.

**Independent Test**: Executing the end-to-end reservation booking and cancellation flow triggers the full event chain across Kafka, Redis, PostgreSQL, and Mailpit with verified side effects.

**Acceptance Scenarios**:

1. **Given** a dining table booking request, **When** submitted to the reservation service, **Then** an atomic outbox event is published to Kafka and the customer receives an email notification.
2. **Given** a reservation cancellation, **When** processed, **Then** the availability cache in Redis is invalidated and a waiting list candidate receives a time-limited offer.

---

### Edge Cases

- How does the system handle dependency version misalignments between third-party starters (e.g. springdoc-openapi, flyway, testcontainers) and the updated framework baseline?
- How are deprecated configuration properties gracefully migrated without breaking runtime environment variable overrides?
- How does the build verify compatibility with virtual threads and modern JDK versions?

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The root reactor POM MUST declare the updated Spring Boot framework version baseline across all submodules.
- **FR-002**: All microservices (`customer-service`, `restaurant-service`, `availability-service`, `reservation-service`, `waiting-list-service`, `notification-service`, `analytics-service`, `gateway`) MUST build and execute using the updated framework starters.
- **FR-003**: All configuration property keys in `application.yml` and environment variable mappings MUST be migrated to match updated framework naming conventions.
- **FR-004**: Web, security, data, and messaging configuration classes MUST be refactored to replace deprecated classes, methods, and builders with modern idiomatic alternatives.
- **FR-005**: All test classes across all modules MUST be updated to modern test slicing and mock verification patterns, replacing deprecated `@MockBean` with modern `@MockitoBean` or isolated mock runners.
- **FR-006**: All Actuator endpoints, health probes, Prometheus metrics, and OpenTelemetry integrations MUST remain fully functional under the updated framework.
- **FR-007**: Interactive OpenAPI / Swagger UI endpoints MUST remain accessible and properly formatted on each service's designated port.
- **FR-008**: All companion BOMs (Spring Cloud, Springdoc OpenAPI, Testcontainers, Flyway) MUST be upgraded in coordinated alignment with the target framework baseline.

---

### Key Entities

- **Reactor Build Configuration**: Root parent POM defining dependency management BOMs, plugin configurations, and compiler baselines.
- **Microservice Configuration Model**: Submodule configuration files (`application.yml`) managing datasource, caching, messaging, and security settings.
- **Service Bean Definitions**: Spring `@Configuration` components defining security filter chains, Kafka listeners, Redis templates, and outbox pollers.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of submodules in the Maven reactor project build and pass all unit, domain, and web tests.
- **SC-002**: Zero deprecation or compatibility warnings emitted during application startup and test execution.
- **SC-003**: 100% of REST endpoints, outbox publishers, Kafka event listeners, and scheduled background workers remain functionally verified with zero regression in business workflows.
- **SC-004**: All health and readiness probes respond with status `UP` within 5 seconds of container startup.

---

## Assumptions

- Compatible companion starters (e.g. Spring Cloud, Springdoc OpenAPI, Flyway) are aligned with the target framework baseline.
- Existing domain logic, data models, PostgreSQL database schemas, and Kafka event payload contracts remain unchanged.
- The platform continues to target modern LTS / preview Java runtimes with Virtual Threads enabled.
