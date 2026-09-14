# Research & Technical Decisions: Fix JWT Issuer Validation Across Deployment Environments

**Feature**: `006-fix-jwt-issuer-validation` | **Date**: 2026-09-14

## Executive Summary

When developers or external clients obtain a Keycloak JWT access token from the host machine (`http://localhost:8081/...`), Keycloak issues the token with claim:
`"iss": "http://localhost:8081/realms/rube-goldberg"`.
However, microservices running in Docker Compose communicate with Keycloak over the internal Docker network at `http://keycloak:8080/...`. Previously, services relied on Spring Boot's default `NimbusJwtDecoder` auto-configuration, which enforces strict single-string equality (`token.iss == configured.issuer`). When configured with `KEYCLOAK_ISSUER_URI=http://keycloak:8080/realms/rube-goldberg`, Spring Security rejected tokens issued via `localhost:8081` with:
`The iss claim is not valid`.

This research resolves this architectural tension through dual-issuer token validation coupled with profile-based Spring configuration.

---

## Technical Decisions

### Decision 1: Dual-Issuer Token Validation Strategy

- **Decision**: Implement a custom `OAuth2TokenValidator<Jwt>` (`JwtMultiIssuerValidator`) in Spring Security that accepts both the external host issuer (`http://localhost:8081/realms/rube-goldberg`) and the internal container issuer (`http://keycloak:8080/realms/rube-goldberg`).
- **Rationale**:
  - Eliminates the mismatch between external browser/Swagger/CLI token acquisition and internal service-to-service communication.
  - Leaves cryptographic signature verification 100% intact: the token's signature must still verify against Keycloak's real public JWKS keys.
  - Requires zero changes to developer host `/etc/hosts` or complex Docker network routing hacks.
- **Alternatives Considered**:
  - *Fixed Keycloak Frontend URL (`KC_HOSTNAME_URL`)*: Forces Keycloak to always issue tokens with `localhost:8081`. However, containerized microservices attempting to fetch OIDC metadata from `http://localhost:8081` fail inside container networking unless host aliases are configured.
  - *Host DNS Alias (`keycloak.local`)*: Requires modifying `/etc/hosts` on every developer machine, introducing high developer friction and onboarding failures.
  - *Disabling Issuer Validation*: Disabling issuer checks opens the system to token spoofing across different Keycloak realms or untrusted identity providers.

---

### Decision 2: Spring Profile Architecture (`default`/`local` vs `docker`)

- **Decision**: Externalize the list of accepted issuers and Keycloak URIs using Spring Boot configuration properties (`security.jwt.accepted-issuers`) and support Spring profiles:
  - **Default / Local Profile** (`application.yml`):
    - `spring.security.oauth2.resourceserver.jwt.issuer-uri`: `http://localhost:8081/realms/rube-goldberg`
    - `security.jwt.accepted-issuers`: `http://localhost:8081/realms/rube-goldberg`, `http://keycloak:8080/realms/rube-goldberg`
  - **Docker Profile** (`application-docker.yml` or `SPRING_PROFILES_ACTIVE=docker` in `docker-compose.apps.yml`):
    - `spring.security.oauth2.resourceserver.jwt.issuer-uri`: `http://keycloak:8080/realms/rube-goldberg`
    - `security.jwt.accepted-issuers`: `http://localhost:8081/realms/rube-goldberg`, `http://keycloak:8080/realms/rube-goldberg`
- **Rationale**:
  - In local development (`mvn spring-boot:run` on host), services connect directly to `localhost:8081`.
  - In containerized execution (`docker compose up`), services connect to `keycloak:8080` while accepting tokens minted externally via `localhost:8081`.
  - Directly fulfills user requirement: *"Spring profile with option A"*.
- **Alternatives Considered**:
  - *Hardcoding Issuers in Java Code*: Inflexible, violates twelve-factor app principles, and prevents staging/production deployments.

---

### Decision 3: Spring Security `JwtDecoder` Construction & Key Resolution

- **Decision**: Declare an explicit `@Bean public JwtDecoder jwtDecoder(...)` in `SecurityConfig` across the 5 secured services (`customer-service`, `restaurant-service`, `availability-service`, `reservation-service`, `waiting-list-service`).
  ```java
  NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
  OAuth2TokenValidator<Jwt> defaultValidator = JwtValidators.createDefault();
  OAuth2TokenValidator<Jwt> multiIssuerValidator = new JwtMultiIssuerValidator(acceptedIssuers);
  jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(defaultValidator, multiIssuerValidator));
  ```
- **Rationale**:
  - `NimbusJwtDecoder.withIssuerLocation(issuerUri)` automatically resolves the JWKS endpoint from `{issuerUri}/.well-known/openid-configuration` (connecting to `keycloak:8080` inside Docker or `localhost:8081` on host).
  - Calling `jwtDecoder.setJwtValidator(...)` replaces the single-issuer check with `JwtMultiIssuerValidator` while keeping `JwtTimestampValidator` (validating `exp`, `nbf`, `iat`).
- **Alternatives Considered**:
  - *Spring Security `OAuth2TokenValidator` bean override*: Spring Boot's auto-configuration does not expose an extension point for replacing just the issuer validator without declaring the full `JwtDecoder` bean. Declaring the `JwtDecoder` bean is standard Spring Security practice.

---

## Implementation Scope & Affected Services

The following 5 secured REST microservices require the `JwtDecoder` bean and multi-issuer validator:
1. `services/restaurant-service`
2. `services/customer-service`
3. `services/availability-service`
4. `services/reservation-service`
5. `services/waiting-list-service`

Plus configuration updates in:
- `application.yml` and `application-docker.yml` across services
- `infrastructure/docker-compose.apps.yml` (ensuring `SPRING_PROFILES_ACTIVE=docker` or environment overrides)
