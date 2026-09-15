# Feature Specification: Refactor Configuration to Dedicated Classes

**Feature Branch**: `003-refactor-configuration`

**Created**: 2026-09-11

**Status**: Draft

**Input**: User description: "refactor configuration - Move bean declaration to dedicated configuration classes, annotated with '@Configuration' annotation in its own package."

## Clarifications

### Session 2026-09-11

- Q: How should dedicated configuration classes be organized when a service has multiple infrastructure concerns (e.g., Jackson serialization vs. Redis caching in `availability-service`)? → A: Granular configuration classes by concern (e.g., `JacksonConfig`, `CacheConfig`, `SecurityConfig`).
- Q: Should the extracted `@Configuration` classes specify `proxyBeanMethods = false` (lite mode) or use standard full CGLIB proxying? → A: Lite mode (`@Configuration(proxyBeanMethods = false)`) across all new configuration classes.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Dedicated Serialization Configuration in Domain Services (Priority: P1)

As a platform engineer and developer, I want all object serialization and date/time formatting bean declarations moved out of the `@SpringBootApplication` entrypoint classes and into dedicated configuration classes in their own `config` package across all domain services, so that application bootstrap logic remains concise and serialization concerns are isolated and easily maintainable.

**Why this priority**: Every domain microservice (customer, restaurant, availability, reservation, waiting-list, notification, analytics) currently defines custom serialization beans directly in the root application bootstrap class, mixing startup orchestration with infrastructure configuration.

**Independent Test**: Can be fully tested by running service-level unit, slice, and context tests across all domain microservices, verifying that JSON serialization beans are correctly discovered from the dedicated configuration package and injected into listeners, publishers, and controllers.

**Acceptance Scenarios**:

1. **Given** any domain microservice (`customer-service`, `restaurant-service`, `reservation-service`, `availability-service`, `waiting-list-service`, `notification-service`, `analytics-service`), **When** inspecting the main `@SpringBootApplication` class, **Then** it contains only application startup lifecycle logic (`main` method) and top-level annotations, with zero `@Bean` declarations.
2. **Given** any domain microservice, **When** inspecting its configuration package (`*.config`), **Then** a dedicated `@Configuration` class exists containing the configured `ObjectMapper` bean with Java 8/date-time modules enabled.
3. **Given** any domain microservice, **When** executing `./mvnw clean test`, **Then** the service compiles cleanly and all dependent components receive the configured serialization beans without failure.

---

### User Story 2 - Modular Gateway Security Configuration (Priority: P2)

As a platform engineer, I want the API Gateway security filter chain bean declaration moved out of `GatewayApplication` into a dedicated security configuration class within the `gateway.config` package, so that routing gateway lifecycle and network security policies are decoupled.

**Why this priority**: The API Gateway acts as the single point of ingress. Isolating its security filter chain into a dedicated configuration class improves readability and facilitates future enhancements such as custom CORS, authentication converters, or rate-limiting filters.

**Independent Test**: Can be fully tested by executing `GatewayApplicationTest` to ensure the reactive security filter chain is instantiated from the dedicated configuration class and enforces public endpoint access and routing rules.

**Acceptance Scenarios**:

1. **Given** the `gateway` module, **When** inspecting `GatewayApplication.java`, **Then** the `SecurityWebFilterChain` bean method is absent, leaving only standard application startup orchestration.
2. **Given** the `gateway` module, **When** inspecting the `nl.invokedynamic.demo.gateway.config` package, **Then** a dedicated `@Configuration` class (e.g., `SecurityConfig`) defines the `SecurityWebFilterChain` with `@EnableWebFluxSecurity`.
3. **Given** the `gateway` module, **When** executing tests, **Then** all gateway route filters and security rules execute without regression.

---

### User Story 3 - Dedicated Caching Configuration in Availability Service (Priority: P3)

As a platform engineer, I want the caching bean declarations in `availability-service` moved from `AvailabilityServiceApplication` into a dedicated caching configuration class within the `availability.config` package, so that Redis caching policies are clearly separated from application startup.

**Why this priority**: `availability-service` manages high-throughput read projections with specialized caching needs (such as Redis cache managers and TTL policies). Isolating cache infrastructure into a dedicated configuration class allows caching policies to evolve independently.

**Independent Test**: Can be fully tested by running availability service tests to verify that `CacheManager` is initialized from the dedicated configuration class and provides caching support for availability lookup queries.

**Acceptance Scenarios**:

1. **Given** the `availability-service` module, **When** inspecting `AvailabilityServiceApplication.java`, **Then** the `CacheManager` bean is absent from the main class.
2. **Given** the `availability-service` module, **When** inspecting the `nl.invokedynamic.demo.availability.config` package, **Then** a dedicated `@Configuration` class provides the `CacheManager` bean.
3. **Given** the `availability-service` module, **When** executing service tests, **Then** table availability calculation and cache interactions continue to operate with 100% test pass rate.

---

### Edge Cases

- **Package Scanning Discovery**: Configuration classes must reside in sub-packages of each service's base package (`nl.invokedynamic.demo.<service>.config`) to ensure default Spring component scanning discovers them without requiring explicit `@Import` or `@ComponentScan` directives.
- **Slice Test Compatibility**: Slice tests (e.g., `@WebMvcTest`) that do not load full `@Configuration` classes automatically must either provide required mock beans or import necessary configuration classes cleanly.
- **Proxying Bean Methods**: Configuration classes must specify `@Configuration(proxyBeanMethods = false)` (lite mode) consistently across all modules to optimize startup and AOT readiness.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Every application entrypoint class (`*Application.java`) across all modules MUST be free of `@Bean` methods, serving solely as the application bootstrap entrypoint.
- **FR-002**: Every microservice MUST establish a dedicated `config` package (under `nl.invokedynamic.demo.<service>.config`) to house its infrastructure and bean configuration classes.
- **FR-003**: All extracted configuration classes MUST be annotated with `@Configuration(proxyBeanMethods = false)`.
- **FR-004**: In all 7 domain microservices, the `ObjectMapper` bean declaration MUST be housed within a dedicated `JacksonConfig.java` class in the service's `config` package.
- **FR-005**: In `gateway`, the `SecurityWebFilterChain` bean declaration and `@EnableWebFluxSecurity` annotation MUST be housed within a dedicated `SecurityConfig.java` class in `nl.invokedynamic.demo.gateway.config`.
- **FR-006**: In `availability-service`, the `CacheManager` bean declaration MUST be housed within a dedicated `CacheConfig.java` class in `nl.invokedynamic.demo.availability.config`.
- **FR-007**: All existing unit, slice, and integration tests MUST continue to pass with zero test failures across the entire Maven reactor.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of `@Bean` declarations across all 8 microservices and gateway are housed in dedicated classes within a `config` package.
- **SC-002**: Zero `@Bean` annotations remain inside any `*Application.java` bootstrap class.
- **SC-003**: 100% test pass rate maintained across the full Maven reactor build (`./mvnw clean test`).
- **SC-004**: Service startup and Actuator health check response times remain uncompromised after bean relocation.

## Assumptions

- **Configuration Package Convention**: The target package for all new configuration classes is `nl.invokedynamic.demo.<service_name>.config`, which is automatically included in the default component scan hierarchy of `@SpringBootApplication`.
- **Bean Scopes**: All relocated beans retain their default singleton scope and exact existing configurations/features (such as `JavaTimeModule` and serialization flags).
- **No Shared Config Module Needed**: Each service retains independent ownership of its configuration classes according to microservice encapsulation principles, avoiding cross-service coupling.
