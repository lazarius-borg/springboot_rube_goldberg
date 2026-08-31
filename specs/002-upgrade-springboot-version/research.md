# Technical Research: Upgrade Spring Boot Version

## Decision 1: Target Spring Boot Version Baseline & Ecosystem BOMs
- **Decision**: Upgrade parent POM and submodules from Spring Boot `3.4.3` to modern target `4.1.1` (or latest GA release train). Align Spring Cloud BOM, Springdoc OpenAPI (`springdoc-openapi-starter-webmvc-ui` 2.8+), and Testcontainers BOM.
- **Rationale**: Ensures runtime optimizations, modern virtual thread integration, security maintenance, and latest Spring Framework 6.x/7.x baseline.
- **Alternatives Considered**: Sticking to 3.4.3 was rejected because the user specifically requested upgrading to modern release train and refactoring to modern conventions.

## Decision 2: Modern Testing Annotations & Mocking Strategy
- **Decision**: Migrate test suites from `@MockBean` / `@SpyBean` (which are deprecated in modern Spring Boot in favor of Spring Framework's `@MockitoBean` / `@MockitoSpyBean` in `org.springframework.test.context.bean.override.mockito`).
- **Rationale**: Eliminates deprecation warnings and leverages first-class framework-level Bean Override mechanisms.
- **Alternatives Considered**: Using standalone MockMvc without `@MockitoBean` or retaining deprecated `@MockBean`. Upgrading to modern `@MockitoBean` provides clean test slicing and zero compiler warnings.

## Decision 3: Virtual Threads & Configuration Properties
- **Decision**: Retain `spring.threads.virtual.enabled: true` in `application.yml` and ensure Actuator metrics, Kafka listener containers, and web servers utilize virtual thread executors.
- **Rationale**: Virtual threads provide high concurrency with lightweight resource consumption across all 8 microservices.
