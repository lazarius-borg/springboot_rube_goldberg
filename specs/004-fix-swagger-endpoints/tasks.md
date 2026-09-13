# Tasks: Public Access to OpenAPI and Swagger UI Endpoints

**Branch**: `004-fix-swagger-endpoints` | **Date**: 2026-09-13 | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Baseline verification of compilation and existing test suites.

- [X] T001 Verify project baseline build and test suite compilation via ./mvnw test-compile

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Verify configuration package structures across all domain microservices before creating security and OpenAPI classes.

**⚠️ CRITICAL**: Must complete before user story implementation begins.

- [X] T002 Verify existence of config directories across customer, restaurant, reservation, availability, waiting-list, and analytics services

**Checkpoint**: Foundation ready - user story implementation can proceed.

---

## Phase 3: User Story 1 - Inspect API Documentation via Swagger UI Without Authentication (Priority: P1) 🎯 MVP

**Goal**: Permit unauthenticated access to `/swagger-ui.html`, `/swagger-ui/**`, and `/v3/api-docs/**` by defining dedicated `SecurityConfig` classes in all 5 secured microservices.

**Independent Test**: Issue HTTP GET requests to `/swagger-ui.html`, `/swagger-ui/index.html`, and `/v3/api-docs` without Authorization header and receive HTTP 200 / 302 responses.

### Implementation for User Story 1

- [X] T003 [P] [US1] Create SecurityConfig in services/customer-service/src/main/java/nl/invokedynamic/demo/customer/config/SecurityConfig.java
- [X] T004 [P] [US1] Create SecurityConfig in services/restaurant-service/src/main/java/nl/invokedynamic/demo/restaurant/config/SecurityConfig.java
- [X] T005 [P] [US1] Create SecurityConfig in services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/config/SecurityConfig.java
- [X] T006 [P] [US1] Create SecurityConfig in services/availability-service/src/main/java/nl/invokedynamic/demo/availability/config/SecurityConfig.java
- [X] T007 [P] [US1] Create SecurityConfig in services/waiting-list-service/src/main/java/nl/invokedynamic/demo/waitinglist/config/SecurityConfig.java
- [X] T008 [US1] Compile and verify Spring Security filter chain configurations across all services via ./mvnw test-compile

**Checkpoint**: User Story 1 complete - all 5 secured services permit unauthenticated documentation exploration.

---

## Phase 4: User Story 2 - Maintain API Protection for Secured Business Endpoints (Priority: P2)

**Goal**: Validate defense-in-depth: ensure `/actuator/**` and domain business routes strictly require authentication and return HTTP 401 when unauthenticated.

**Independent Test**: Execute security tests against secured domain services asserting HTTP 200 on documentation endpoints and HTTP 401 on actuator and business endpoints.

### Implementation & Tests for User Story 2

- [X] T009 [P] [US2] Create security test in services/customer-service/src/test/java/nl/invokedynamic/demo/customer/CustomerSecurityTest.java
- [X] T010 [P] [US2] Create security test in services/restaurant-service/src/test/java/nl/invokedynamic/demo/restaurant/RestaurantSecurityTest.java
- [X] T011 [P] [US2] Create security test in services/reservation-service/src/test/java/nl/invokedynamic/demo/reservation/ReservationSecurityTest.java
- [X] T012 [P] [US2] Create security test in services/availability-service/src/test/java/nl/invokedynamic/demo/availability/AvailabilitySecurityTest.java
- [X] T013 [P] [US2] Create security test in services/waiting-list-service/src/test/java/nl/invokedynamic/demo/waitinglist/WaitingListSecurityTest.java
- [X] T014 [US2] Execute security test suite verifying documentation access and unauthorized API rejections via ./mvnw test -pl services/customer-service,services/restaurant-service,services/reservation-service,services/availability-service,services/waiting-list-service -am

**Checkpoint**: User Story 2 complete - API and actuator endpoints remain strictly guarded with 100% test verification.

---

## Phase 5: User Story 3 - Interactive Testing via Swagger UI "Authorize" Button and Clear README Documentation (Priority: P3)

**Goal**: Extract OpenAPI definitions from application entrypoint classes into dedicated `OpenApiConfig` classes, add HTTP Bearer JWT security scheme for the "Authorize" button, and update README instructions.

**Independent Test**: Verify `/v3/api-docs` contains `bearerAuth` security scheme component, and verify README contains updated Swagger UI walkthrough.

### Implementation for User Story 3

- [X] T015 [P] [US3] Create OpenApiConfig with bearerAuth in services/customer-service/src/main/java/nl/invokedynamic/demo/customer/config/OpenApiConfig.java and remove @OpenAPIDefinition from services/customer-service/src/main/java/nl/invokedynamic/demo/customer/CustomerServiceApplication.java
- [X] T016 [P] [US3] Create OpenApiConfig with bearerAuth in services/restaurant-service/src/main/java/nl/invokedynamic/demo/restaurant/config/OpenApiConfig.java and remove @OpenAPIDefinition from services/restaurant-service/src/main/java/nl/invokedynamic/demo/restaurant/RestaurantServiceApplication.java
- [X] T017 [P] [US3] Create OpenApiConfig with bearerAuth in services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/config/OpenApiConfig.java and remove @OpenAPIDefinition from services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/ReservationServiceApplication.java
- [X] T018 [P] [US3] Create OpenApiConfig with bearerAuth in services/availability-service/src/main/java/nl/invokedynamic/demo/availability/config/OpenApiConfig.java and remove @OpenAPIDefinition from services/availability-service/src/main/java/nl/invokedynamic/demo/availability/AvailabilityServiceApplication.java
- [X] T019 [P] [US3] Create OpenApiConfig with bearerAuth in services/waiting-list-service/src/main/java/nl/invokedynamic/demo/waitinglist/config/OpenApiConfig.java and remove @OpenAPIDefinition from services/waiting-list-service/src/main/java/nl/invokedynamic/demo/waitinglist/WaitingListServiceApplication.java
- [X] T020 [P] [US3] Create OpenApiConfig in services/analytics-service/src/main/java/nl/invokedynamic/demo/analytics/config/OpenApiConfig.java and remove @OpenAPIDefinition from services/analytics-service/src/main/java/nl/invokedynamic/demo/analytics/AnalyticsServiceApplication.java
- [X] T021 [US3] Update README.md under How to Use Swagger UI for Manual Inspection describing unauthenticated schema access and Bearer token usage in the Swagger UI Authorize modal

**Checkpoint**: User Story 3 complete - "Authorize" button enabled across OpenAPI schemas and README accurately reflects access model.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: End-to-end verification, full reactor build, and fat JAR packaging.

- [X] T022 Execute full Maven reactor build and test suite via ./mvnw clean test
- [X] T023 Repackage executable Spring Boot JAR archives via ./mvnw package -DskipTests
- [X] T024 Run quickstart.md validation scenarios

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately.
- **Foundational (Phase 2)**: Depends on Phase 1 - verifies config directory baseline.
- **User Story 1 (Phase 3)**: Depends on Phase 2 - delivers core unauthenticated documentation access (MVP).
- **User Story 2 (Phase 4)**: Depends on Phase 3 - adds automated security tests verifying documentation access and 401 enforcement.
- **User Story 3 (Phase 5)**: Depends on Phase 3 - adds OpenAPI Bearer auth scheme and updates documentation.
- **Polish (Phase 6)**: Depends on all user stories (US1, US2, US3) being complete.

### Within Each User Story

- Parallel tasks marked with `[P]` affect separate files and submodules and can execute concurrently.
- Module implementation tasks must compile before running corresponding verification tasks.

### Parallel Opportunities

- **US1 SecurityConfig Creation**: Tasks T003 through T007 touch separate files in distinct submodules and can run in parallel.
- **US2 Security Tests**: Tasks T009 through T013 touch distinct test files across different submodules and can run in parallel.
- **US3 OpenApiConfig Creation**: Tasks T015 through T020 touch distinct service submodules and can run in parallel.

---

## Implementation Strategy

### MVP First (User Story 1 Only)
1. Complete Phase 1 (Setup baseline) and Phase 2 (Foundational check).
2. Complete Phase 3 (User Story 1 - Create `SecurityConfig` across all 5 secured services).
3. Validate via T008.
4. Developers can immediately open `/swagger-ui.html` and `/v3/api-docs` without 401 errors.

### Incremental Delivery
1. Foundation & US1 (MVP): Resolves 401 on Swagger UI and OpenAPI paths.
2. US2: Adds automated test safety net verifying negative security assertions (401 on actuator and APIs).
3. US3: Adds "Authorize" dialog support via OpenAPI `bearerAuth` security scheme and updates README.
4. Polish: Validates full Maven reactor build and packages production fat JARs.
