# Tasks: Fix JWT Issuer Validation Across Deployment Environments

**Branch**: `006-fix-jwt-issuer-validation` | **Date**: 2026-09-14 | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Baseline verification of Keycloak realm configuration, token claim structure, and security dependencies.

- [X] T001 Verify baseline Keycloak token issuance format and claims using infrastructure/keycloak/realm-export.json
- [X] T002 [P] Verify Spring Security OAuth2 Resource Server dependencies in pom.xml across secured microservices

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core multi-issuer token validation component and property definitions that all user stories depend on.

**⚠️ CRITICAL**: Must complete before user story updates begin.

- [X] T003 Implement JwtMultiIssuerValidator in services/restaurant-service/src/main/java/nl/invokedynamic/demo/restaurant/config/JwtMultiIssuerValidator.java
- [X] T004 [P] Add externalized configuration properties for security.jwt.accepted-issuers in services/restaurant-service/src/main/resources/application.yml

**Checkpoint**: Foundation ready - user story implementation can now proceed.

---

## Phase 3: User Story 1 - Authenticate Protected Operations with Valid Identity Tokens (Priority: P1) 🎯 MVP

**Goal**: Enable Spring Security to accept tokens minted by the external Keycloak host endpoint (`http://localhost:8081/realms/rube-goldberg`) in `restaurant-service` and `customer-service`, eliminating the `iss` mismatch error in Swagger UI and CLI requests.

**Independent Test**: Obtain an authentication token for `manager1` from `http://localhost:8081/...` and invoke `GET /api/v1/restaurants` and `GET /api/v1/customers/me`, confirming HTTP 200 OK without 401 invalid_token issuer rejection.

### Implementation for User Story 1

- [X] T005 [US1] Define JwtDecoder bean in services/restaurant-service/src/main/java/nl/invokedynamic/demo/restaurant/config/SecurityConfig.java configuring JwtMultiIssuerValidator and timestamp validators
- [X] T006 [P] [US1] Implement JwtMultiIssuerValidator in services/customer-service/src/main/java/nl/invokedynamic/demo/customer/config/JwtMultiIssuerValidator.java
- [X] T007 [US1] Define JwtDecoder bean in services/customer-service/src/main/java/nl/invokedynamic/demo/customer/config/SecurityConfig.java and configure security.jwt.accepted-issuers in services/customer-service/src/main/resources/application.yml
- [X] T008 [P] [US1] Add unit/slice tests in services/restaurant-service/src/test/java/nl/invokedynamic/demo/restaurant/RestaurantSecurityTest.java verifying dual-issuer validation and untrusted issuer rejection
- [X] T009 [P] [US1] Add unit/slice tests in services/customer-service/src/test/java/nl/invokedynamic/demo/customer/CustomerSecurityTest.java verifying dual-issuer validation and untrusted issuer rejection

**Checkpoint**: User Story 1 complete - developers and managers can authenticate in Swagger UI and CLI on restaurant-service and customer-service.

---

## Phase 4: User Story 2 - Uniform Issuer Acceptance Across Deployment Topologies (Priority: P2)

**Goal**: Propagate dual-issuer validation across the remaining domain microservices (`availability-service`, `reservation-service`, `waiting-list-service`) and reconcile Docker Compose deployment manifests (`docker-compose.apps.yml`) with Spring profiles (`default`/`local` vs `docker`).

**Independent Test**: Run the full suite of microservices in Docker Compose, acquire a token from the external host URL, and verify that all 5 secured services accept it uniformly without issuer mismatch errors.

### Implementation for User Story 2

- [X] T010 [P] [US2] Implement JwtMultiIssuerValidator and configure JwtDecoder bean with security.jwt.accepted-issuers in services/availability-service/src/main/java/nl/invokedynamic/demo/availability/config/SecurityConfig.java, JwtMultiIssuerValidator.java, and application.yml
- [X] T011 [P] [US2] Implement JwtMultiIssuerValidator and configure JwtDecoder bean with security.jwt.accepted-issuers in services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/config/SecurityConfig.java, JwtMultiIssuerValidator.java, and application.yml
- [X] T012 [P] [US2] Implement JwtMultiIssuerValidator and configure JwtDecoder bean with security.jwt.accepted-issuers in services/waiting-list-service/src/main/java/nl/invokedynamic/demo/waitinglist/config/SecurityConfig.java, JwtMultiIssuerValidator.java, and application.yml
- [X] T013 [P] [US2] Add dual-issuer security slice tests in services/availability-service, reservation-service, and waiting-list-service
- [X] T014 [US2] Update infrastructure/docker-compose.apps.yml to declare SPRING_PROFILES_ACTIVE=docker and ensure KEYCLOAK_ISSUER_URI and SECURITY_JWT_ACCEPTED_ISSUERS are consistently configured

**Checkpoint**: User Story 2 complete - all microservices operate consistently across local and Docker Compose environments.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Validation, regression testing, and documentation alignment.

- [X] T015 [P] Update README.md with troubleshooting notes regarding dual-issuer validation and Spring profile usage
- [X] T016 Execute full Maven test validation across all microservice security tests via mvn test -Dtest=*SecurityTest
- [X] T017 Execute quickstart.md validation scenarios to confirm end-to-end token acceptance in Swagger UI and CLI

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately.
- **Foundational (Phase 2)**: Depends on Phase 1 - creates the core multi-issuer validator and property model.
- **User Story 1 (Phase 3)**: Depends on Phase 2 - integrates dual-issuer validation into `restaurant-service` and `customer-service` (MVP).
- **User Story 2 (Phase 4)**: Depends on Phase 3 - rolls out dual-issuer validation across the remaining services and Docker Compose.
- **Polish (Phase 5)**: Depends on Phase 4 - executes cross-service test suite and quickstart scenarios.

### User Story Dependencies

- **User Story 1 (P1)**: Foundation for token acceptance; independent of Story 2.
- **User Story 2 (P2)**: Builds on the patterns established in Story 1 to complete system-wide multi-environment parity.

---

## Parallel Execution Opportunities

### User Story 1 Parallel Work
```bash
# Implement customer-service validator and tests concurrently with restaurant-service:
T006: "Implement JwtMultiIssuerValidator in services/customer-service"
T008: "Add unit/slice tests in services/restaurant-service/RestaurantSecurityTest.java"
T009: "Add unit/slice tests in services/customer-service/CustomerSecurityTest.java"
```

### User Story 2 Parallel Work
```bash
# Implement remaining services concurrently:
T010: "Configure availability-service dual-issuer validation"
T011: "Configure reservation-service dual-issuer validation"
T012: "Configure waiting-list-service dual-issuer validation"
T013: "Add security tests across availability, reservation, and waiting-list services"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Setup (T001-T002) and Foundational (T003-T004).
2. Complete User Story 1 (T005-T009).
3. Validate: Obtain token for `manager1` and successfully access `/api/v1/restaurants` in Swagger UI (resolves the user's reported bug directly).

### Incremental Delivery

1. Deliver User Story 1 (Restaurant & Customer Service token validation).
2. Deliver User Story 2 (Full microservice cluster rollout + Docker Compose profile alignment).
3. Deliver Polish phase (end-to-end verification and documentation).
