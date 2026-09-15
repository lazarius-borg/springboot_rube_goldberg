# Research: Refactor Configuration to Dedicated Classes

**Feature**: `003-refactor-configuration` | **Date**: 2026-09-11

## Technical Decisions & Rationale

### 1. Dedicated Package Hierarchy (`*.config`)
- **Decision**: Place all new configuration classes into `nl.invokedynamic.demo.<service>.config`.
- **Rationale**:
  - Automatically included within the `@SpringBootApplication` default component scanning tree rooted at `nl.invokedynamic.demo.<service>`.
  - Avoids polluting the top-level application root package with infrastructure wiring.
  - Consistent across all 8 microservices and gateway.
- **Alternatives Considered**:
  - *Root package (`nl.invokedynamic.demo.<service>`)*: Violates modularity and leaves infrastructure configuration mixed with the application runner.
  - *Shared common library module*: Violates microservice boundary autonomy and introduces unnecessary inter-module release coupling.

---

### 2. Concern-Specific Granular Configuration Classes
- **Decision**: Define separate configuration classes according to infrastructure concern:
  - `JacksonConfig`: Configures `ObjectMapper` with `JavaTimeModule` and serialization flags across all 7 domain microservices.
  - `CacheConfig`: Configures `RedisCacheManager` with default cache configurations in `availability-service`.
  - `SecurityConfig`: Configures `SecurityWebFilterChain` and `@EnableWebFluxSecurity` in `gateway`.
- **Rationale**:
  - Follows the Single Responsibility Principle (SRP).
  - Keeps serialization, caching, and security independently testable and configurable.
  - Enables modular `@Import(JacksonConfig.class)` in test slices if specific beans are required.
- **Alternatives Considered**:
  - *Single `AppConfig.java` per service*: Merges caching, serialization, and security into a monolithic configuration class.

---

### 3. Configuration Proxying Semantics (`proxyBeanMethods = false`)
- **Decision**: Annotate all extracted configuration classes with `@Configuration(proxyBeanMethods = false)`.
- **Rationale**:
  - None of the `@Bean` methods invoke other `@Bean` methods within the same class (they are pure factory methods).
  - Eliminates runtime CGLIB proxy subclass generation and byte-code weaving during context startup.
  - Aligns with modern Spring Boot 4 / Spring Framework 7 conventions for Ahead-Of-Time (AOT) readiness and virtual-thread-optimized fast startup.
- **Alternatives Considered**:
  - *Default `@Configuration` (proxyBeanMethods = true)*: Introduces unnecessary CGLIB proxy overhead with zero benefit for independent factory methods.

---

### 4. Application Entrypoint Cleanup
- **Decision**: Strip all `@Bean` methods and secondary infrastructure annotations (such as `@EnableWebFluxSecurity`) from `*Application.java` classes.
- **Rationale**:
  - The entrypoint class serves strictly as the bootstrap orchestrator (`public static void main(String[] args)`) and top-level application metadata holder (`@OpenAPIDefinition`, `@EnableScheduling`, `@EnableCaching`).
  - Ensures clean separation between application bootstrapping and infrastructure bean definitions.
- **Alternatives Considered**:
  - *Leaving some beans in Application*: Inconsistent and confusing to maintainers.

---

### 5. Test Suite Compatibility Strategy
- **Decision**: Verify all unit, slice (`@WebMvcTest`), and integration tests (`@SpringBootTest`) after extraction.
  - Full `@SpringBootTest` suites (e.g. `GatewayApplicationTest`) automatically scan and load the new `@Configuration` classes in the `.config` subpackage.
  - Slice tests (`@WebMvcTest`) that mock their dependencies remain unaffected unless they directly autowire `ObjectMapper`, in which case `@Import(JacksonConfig.class)` is added.
- **Rationale**: Zero test regression across the full Maven reactor.
