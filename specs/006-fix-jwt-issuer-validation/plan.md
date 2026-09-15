# Implementation Plan: Fix JWT Issuer Validation Across Deployment Environments

**Branch**: `006-fix-jwt-issuer-validation` | **Date**: 2026-09-14 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/006-fix-jwt-issuer-validation/spec.md`

## Summary

Reconcile Keycloak JWT access token issuer validation across local and containerized deployments. Introduce a Spring Security `JwtMultiIssuerValidator` and explicit `JwtDecoder` bean across secured microservices (`customer-service`, `restaurant-service`, `availability-service`, `reservation-service`, `waiting-list-service`) configured to accept both external (`http://localhost:8081/realms/rube-goldberg`) and internal (`http://keycloak:8080/realms/rube-goldberg`) issuers. Support clean configuration via Spring profiles (`default`/`local` vs `docker`) and externalized properties (`security.jwt.accepted-issuers`).

## Technical Context

**Language/Version**: Java 21 / 26 (Project Loom virtual threads enabled)  
**Primary Dependencies**: Spring Boot 3.x / 4.x, Spring Security OAuth2 Resource Server, Nimbus JOSE + JWT  
**Storage**: PostgreSQL 17 (Keycloak DB & service DBs)  
**Testing**: JUnit 5, Mockito, AssertJ, Spring WebApplicationContextRunner slice tests  
**Target Platform**: Docker Compose / Linux / macOS  
**Project Type**: Spring Boot Microservices / OAuth2 Resource Server Security  
**Performance Goals**: Sub-millisecond in-memory token validation (JWKS cached by Nimbus)  
**Constraints**: Zero disruption to cryptographic signature verification; maintain strict rejection of untrusted issuers or expired tokens  
**Scale/Scope**: 5 secured microservices (`customer`, `restaurant`, `availability`, `reservation`, `waiting-list`) and `docker-compose.apps.yml`  

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Strict Specification Adherence)**: Conforms directly to the approved specification and user-clarified design ("Spring profile with option A").
- **Principle II (Maven Reactor & Microservices Architecture)**: Each microservice sub-module retains encapsulation of its `SecurityConfig` and profile settings.
- **Principle III (Modern Spring Boot Feature Showcase)**: Leverages Spring Security OAuth2 Resource Server customization, `@Value` property injection, and Spring Profile configuration.
- **Principle IV (Agentic AI-Aided Development & Traceability)**: Full specification, research, data model, contracts, and quickstart documentation created.
- **Principle V (Comprehensive Testing & Quality Gates)**: Automated security slice tests validating both accepted issuers and rejecting invalid/expired tokens.

## Project Structure

### Documentation (this feature)

```text
specs/006-fix-jwt-issuer-validation/
├── spec.md              # Feature specification and clarifications
├── plan.md              # This implementation plan
├── research.md          # Technical decisions (dual-issuer, profiles, decoder bean)
├── data-model.md        # Token entity, issuer properties, validation state machine
├── contracts/           # Request/response contracts for token validation
│   └── jwt-validation-contract.md
├── quickstart.md        # Verification guide (automated tests & Docker curl/Swagger)
├── checklists/
│   └── requirements.md  # Spec quality checklist (16/16 pass)
└── tasks.md             # Generated in next phase (/speckit-tasks)
```

### Source Code (affected files)

```text
infrastructure/
└── docker-compose.apps.yml

services/
├── customer-service/
│   └── src/main/
│       ├── java/nl/invokedynamic/demo/customer/config/
│       │   ├── SecurityConfig.java
│       │   └── JwtMultiIssuerValidator.java
│       └── resources/application.yml
├── restaurant-service/
│   └── src/main/
│       ├── java/nl/invokedynamic/demo/restaurant/config/
│       │   ├── SecurityConfig.java
│       │   └── JwtMultiIssuerValidator.java
│       └── resources/application.yml
├── availability-service/
│   └── src/main/
│       ├── java/nl/invokedynamic/demo/availability/config/
│       │   ├── SecurityConfig.java
│       │   └── JwtMultiIssuerValidator.java
│       └── resources/application.yml
├── reservation-service/
│   └── src/main/
│       ├── java/nl/invokedynamic/demo/reservation/config/
│       │   ├── SecurityConfig.java
│       │   └── JwtMultiIssuerValidator.java
│       └── resources/application.yml
└── waiting-list-service/
    └── src/main/
        ├── java/nl/invokedynamic/demo/waitinglist/config/
        │   ├── SecurityConfig.java
        │   └── JwtMultiIssuerValidator.java
        └── resources/application.yml
```

## Implementation Strategy

1. **Multi-Issuer Token Validator (`JwtMultiIssuerValidator.java`)**:
   - Implements `OAuth2TokenValidator<Jwt>`.
   - Validates that `jwt.getIssuer().toString()` is present in a configured `Collection<String> allowedIssuers`.
   - Returns `OAuth2TokenValidatorResult.failure(...)` with error description if issuer is unrecognized.

2. **Custom `JwtDecoder` Bean in `SecurityConfig.java`**:
   - Build `NimbusJwtDecoder` using `issuer-uri` (or `jwk-set-uri`).
   - Combine `JwtValidators.createDefault()` (timestamp checks) with `JwtMultiIssuerValidator`.
   - Assign via `decoder.setJwtValidator(...)`.

3. **Externalized Properties & Spring Profiles**:
   - In `application.yml` for each service:
     ```yaml
     security:
       jwt:
         accepted-issuers:
           - ${KEYCLOAK_EXTERNAL_ISSUER_URI:http://localhost:8081/realms/rube-goldberg}
           - ${KEYCLOAK_INTERNAL_ISSUER_URI:http://keycloak:8080/realms/rube-goldberg}
     ```
   - In `docker-compose.apps.yml`: ensure `KEYCLOAK_ISSUER_URI=http://keycloak:8080/realms/rube-goldberg` is set for internal JWKS discovery, and containers accept both issuers.

4. **Security Slice Tests**:
   - Extend/add tests in `*SecurityTest.java` verifying that tokens with `http://localhost:8081/...` and `http://keycloak:8080/...` are accepted, while invalid issuers are rejected.
