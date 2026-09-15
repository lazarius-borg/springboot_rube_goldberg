# Implementation Plan: Role-Based Access Control (RBAC) & Endpoint Authorization

**Branch**: `007-role-endpoint-access` | **Date**: 2026-09-14 | **Spec**: [specs/007-role-endpoint-access/spec.md](spec.md)

---

## Summary

Implement fine-grained Role-Based Access Control (RBAC) across all microservices using Keycloak realm roles (`CUSTOMER`, `RESTAURANT_MANAGER`, `ADMIN`). Custom authorities converters will extract `realm_access.roles` claims from JWTs and map them to Spring Security `ROLE_*` granted authorities. Declarative endpoint matchers in each service's `SecurityFilterChain` will enforce access rules (e.g. restaurant management restricted to `RESTAURANT_MANAGER` / `ADMIN`, table booking restricted to `CUSTOMER`, analytics restricted to `RESTAURANT_MANAGER` / `ADMIN`, and catalog/availability queries open to all authenticated roles). Additionally, secure `analytics-service` as an OAuth2 Resource Server with dual-issuer validation support, update `README.md` with role responsibilities and port-accurate walkthrough commands, and write comprehensive Spring slice/unit tests verifying 401, 403, and 200/201 response codes.

---

## Technical Context

**Language/Version**: Java 21  
**Primary Dependencies**: Spring Boot 3.3.x, Spring Security 6.x (`spring-boot-starter-oauth2-resource-server`), Nimbus JOSE + JWT, Springdoc OpenAPI  
**Storage**: PostgreSQL 16 (per-service relational schemas), Redis (availability caching)  
**Testing**: JUnit 5, Mockito, AssertJ, Spring Boot Test (`WebApplicationContextRunner`, `@WebMvcTest`, MockMvc)  
**Target Platform**: Linux / macOS containerized microservices (Docker Compose, Kubernetes)  
**Project Type**: Multi-module Maven reactor microservices  
**Performance Goals**: Sub-5ms authorization overhead per HTTP request at filter chain level; preserve sub-50ms availability lookup latency  
**Constraints**: Must preserve profile-based dual-issuer token validation (`docker` vs `!docker`); zero regression on Swagger UI and Actuator endpoints; strict adherence to HTTP 401 for unauthenticated vs 403 for unauthorized callers  
**Scale/Scope**: 6 microservices (`restaurant-service`, `reservation-service`, `waiting-list-service`, `customer-service`, `availability-service`, `analytics-service`) and root `README.md`

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] **I. Strict Specification Adherence**: All RBAC rules, roles, allowed endpoints, HTTP status codes, and walkthrough updates directly match `specs/007-role-endpoint-access/spec.md` and clarification agreements.
- [x] **II. Maven Reactor & Microservices Architecture**: All changes reside within designated Maven submodules (`services/*`), maintaining loose coupling, independent runnability, and clear boundaries.
- [x] **III. Modern Spring Boot Feature Showcase**: Demonstrates Spring Security 6 OAuth2 Resource Server conventions, custom JWT authorities converters, declarative `requestMatchers`, and conditional profile decoders.
- [x] **IV. Agentic AI-Aided Development & Traceability**: Clean specification, research, data model, contract, and quickstart artifacts generated under `specs/007-role-endpoint-access/`.
- [x] **V. Comprehensive Testing & Quality Gates**: Slices and security test runners (`WebApplicationContextRunner`) verify 401, 403, and 200/201 behavior across all roles without regressions.

---

## Project Structure

### Documentation (this feature)

```text
specs/007-role-endpoint-access/
├── plan.md              # Implementation Plan (/speckit-plan)
├── research.md          # Keycloak role mapping & service authorization design (/speckit-plan)
├── data-model.md        # Security entities, roles, and authorization matrix (/speckit-plan)
├── quickstart.md        # Verification scenarios and slice test guide (/speckit-plan)
├── contracts/           # Endpoint security contracts (/speckit-plan)
│   └── rbac-endpoint-contract.md
├── checklists/
│   └── requirements.md  # Spec requirements checklist
└── tasks.md             # Implementation tasks (/speckit-tasks)
```

### Source Code Layout

```text
services/
├── analytics-service/
│   ├── pom.xml                                              # Add oauth2-resource-server
│   ├── src/main/java/nl/invokedynamic/demo/analytics/config/
│   │   ├── JwtMultiIssuerValidator.java                    # Dual-issuer token validator
│   │   ├── KeycloakRealmRoleConverter.java                 # realm_access.roles -> ROLE_*
│   │   ├── SecurityConfig.java                             # Security filter chain for analytics
│   │   └── OpenApiConfig.java                              # Bearer auth OpenAPI definition
│   └── src/test/java/nl/invokedynamic/demo/analytics/
│       └── AnalyticsSecurityTest.java                      # Security test suite (401, 403, 200)
├── restaurant-service/
│   ├── src/main/java/nl/invokedynamic/demo/restaurant/config/
│   │   ├── KeycloakRealmRoleConverter.java                 # realm_access.roles -> ROLE_*
│   │   └── SecurityConfig.java                             # Matchers for RESTAURANT_MANAGER, ADMIN, CUSTOMER
│   └── src/test/java/nl/invokedynamic/demo/restaurant/
│       └── RestaurantSecurityTest.java                     # RBAC test cases
├── reservation-service/
│   ├── src/main/java/nl/invokedynamic/demo/reservation/config/
│   │   ├── KeycloakRealmRoleConverter.java                 # realm_access.roles -> ROLE_*
│   │   └── SecurityConfig.java                             # Matchers for CUSTOMER vs RESTAURANT_MANAGER/ADMIN
│   └── src/test/java/nl/invokedynamic/demo/reservation/
│       └── ReservationSecurityTest.java                    # RBAC test cases
├── waiting-list-service/
│   ├── src/main/java/nl/invokedynamic/demo/waitinglist/config/
│   │   ├── KeycloakRealmRoleConverter.java                 # realm_access.roles -> ROLE_*
│   │   └── SecurityConfig.java                             # Matchers for CUSTOMER
│   └── src/test/java/nl/invokedynamic/demo/waitinglist/
│       └── WaitingListSecurityTest.java                    # RBAC test cases
├── customer-service/
│   ├── src/main/java/nl/invokedynamic/demo/customer/config/
│   │   ├── KeycloakRealmRoleConverter.java                 # realm_access.roles -> ROLE_*
│   │   └── SecurityConfig.java                             # Matchers for CUSTOMER
│   └── src/test/java/nl/invokedynamic/demo/customer/
│       └── CustomerSecurityTest.java                       # RBAC test cases
├── availability-service/
│   ├── src/main/java/nl/invokedynamic/demo/availability/config/
│   │   ├── KeycloakRealmRoleConverter.java                 # realm_access.roles -> ROLE_*
│   │   └── SecurityConfig.java                             # Matchers for all authenticated roles
│   └── src/test/java/nl/invokedynamic/demo/availability/
│       └── AvailabilitySecurityTest.java                   # RBAC test cases
└── README.md                                               # Role responsibilities + port-accurate walkthrough
```

---

## Complexity Tracking

*No violations. All design patterns align strictly with the project Constitution and Spring Boot best practices.*
