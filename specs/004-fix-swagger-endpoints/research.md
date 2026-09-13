# Research: Public Access to OpenAPI and Swagger UI Endpoints

**Feature**: `004-fix-swagger-endpoints` | **Date**: 2026-09-13

## Technical Decisions & Rationale

### 1. Spring Security Configuration in Domain Microservices
- **Decision**: Define a dedicated `SecurityConfig` in `nl.invokedynamic.demo.<service>.config` for each secured microservice (`customer-service`, `restaurant-service`, `reservation-service`, `availability-service`, `waiting-list-service`) defining a `SecurityFilterChain` bean.
- **Rationale**:
  - Without an explicit `SecurityFilterChain` bean, Spring Boot's `SpringBootWebSecurityConfiguration` applies a catch-all `anyRequest().authenticated()`.
  - Permitting `/swagger-ui.html`, `/swagger-ui/**`, and `/v3/api-docs/**` allows unauthenticated browser exploration and schema fetching while keeping all other endpoints (business APIs and actuator) strictly protected under OAuth2 Resource Server JWT verification.
  - Using `@Configuration(proxyBeanMethods = false)` avoids CGLIB proxy overhead and aligns with project conventions.
- **Alternatives Considered**:
  - *Disabling Spring Security entirely*: Violates core security requirements and PRD architecture.
  - *Permitting `/actuator/**` on domain services*: Rejected during clarification session (clarification Q1 - Option B). Actuator endpoints on domain services remain guarded.
  - *Permitting everything on domain services*: Violates defense-in-depth; domain services must enforce OAuth2 token validation independently.

---

### 2. Swagger UI "Authorize" Button with Bearer JWT SecurityScheme
- **Decision**: Configure `@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")` and `@OpenAPIDefinition(security = @SecurityRequirement(name = "bearerAuth"))` in a dedicated `OpenApiConfig` class in `nl.invokedynamic.demo.<service>.config`.
- **Rationale**:
  - In Swagger UI, this exposes the global "Authorize" lock icon button.
  - Developers inspecting the API can paste a Bearer token obtained from Keycloak and execute secured requests directly from the browser ("Try it out").
  - Moving `@OpenAPIDefinition` from `*Application.java` entrypoint classes to dedicated `OpenApiConfig.java` classes adheres to the architecture ratified in feature `003-refactor-configuration`.
- **Alternatives Considered**:
  - *Leaving OpenAPI definition without security scheme*: Developers clicking "Try it out" would get 401 without any way to pass authorization in the UI.
  - *Leaving `@OpenAPIDefinition` in `*Application.java`*: Violates clean separation of concerns and constitution principles.

---

### 3. Documentation Inspection Architecture
- **Decision**: Retain direct service port access (`http://localhost:<PORT>/swagger-ui.html`) per the README without introducing Gateway documentation proxy routes.
- **Rationale**:
  - Keeps service boundaries independent and autonomous.
  - Aligns with existing README documentation structure.
  - Avoids complexity of multi-service OpenAPI aggregation or path prefix rewrites in Spring Cloud Gateway.
- **Alternatives Considered**:
  - *Gateway OpenAPI Aggregator*: Introduces unnecessary cross-service routing complexity and single-point-of-failure for local developer exploration.

---

### 4. Test Verification Strategy
- **Decision**: Add automated security integration tests (`@SpringBootTest` with `@AutoConfigureMockMvc`) for secured services to verify:
  1. Unauthenticated `GET /swagger-ui/index.html` and `GET /v3/api-docs` return HTTP 200 (or 302 redirect for `/swagger-ui.html`).
  2. Unauthenticated `GET /actuator/health` and business endpoints return HTTP 401 Unauthorized.
- **Rationale**: Provides verifiable automated regression guards for SC-001 and SC-002 without relying on manual browser checks.

---

### 5. OAuth2 Resource Server Configuration Alignment (`JwtDecoder`)
- **Root Cause**: Spring Boot's `OAuth2ResourceServerAutoConfiguration` requires `spring.security.oauth2.resourceserver.jwt.issuer-uri` or `jwk-set-uri` to instantiate the `JwtDecoder` bean needed by `http.oauth2ResourceServer(oauth2 -> oauth2.jwt(...))`. `availability-service` previously lacked this property in `application.yml` and its Docker Compose environment, causing a startup failure (`required a bean of type 'org.springframework.security.oauth2.jwt.JwtDecoder' that could not be found`).
- **Resolution**: Aligned `services/availability-service/src/main/resources/application.yml` and `infrastructure/docker-compose.apps.yml` to declare `spring.security.oauth2.resourceserver.jwt.issuer-uri: ${KEYCLOAK_ISSUER_URI:http://localhost:8081/realms/rube-goldberg}` and `KEYCLOAK_ISSUER_URI=http://keycloak:8080/realms/rube-goldberg`, matching the other secured services (`customer`, `restaurant`, `reservation`, `waiting-list`). Added an automated test verifying `JwtDecoder` autoconfiguration.
