# Tasks: Role-Based Access Control (RBAC) & Endpoint Authorization

**Feature**: `007-role-endpoint-access`  
**Spec**: [specs/007-role-endpoint-access/spec.md](spec.md) | **Plan**: [specs/007-role-endpoint-access/plan.md](plan.md)  
**Status**: Ready for Implementation  

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and security dependency setup for unsecured services

- [x] T001 Add `spring-boot-starter-oauth2-resource-server` dependency to `services/analytics-service/pom.xml`
- [x] T002 [P] Configure OpenAPI bearer authentication schema in `services/analytics-service/src/main/java/nl/invokedynamic/demo/analytics/config/OpenApiConfig.java`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Role extraction infrastructure and token decoding foundation required across all services

**⚠️ CRITICAL**: Must be completed before applying endpoint security matchers

- [x] T003 [P] Implement `KeycloakRealmRoleConverter` in `services/restaurant-service/src/main/java/nl/invokedynamic/demo/restaurant/config/KeycloakRealmRoleConverter.java`
- [x] T004 [P] Implement `KeycloakRealmRoleConverter` in `services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/config/KeycloakRealmRoleConverter.java`
- [x] T005 [P] Implement `KeycloakRealmRoleConverter` in `services/waiting-list-service/src/main/java/nl/invokedynamic/demo/waitinglist/config/KeycloakRealmRoleConverter.java`
- [x] T006 [P] Implement `KeycloakRealmRoleConverter` in `services/customer-service/src/main/java/nl/invokedynamic/demo/customer/config/KeycloakRealmRoleConverter.java`
- [x] T007 [P] Implement `KeycloakRealmRoleConverter` in `services/availability-service/src/main/java/nl/invokedynamic/demo/availability/config/KeycloakRealmRoleConverter.java`
- [x] T008 [P] Implement `KeycloakRealmRoleConverter` in `services/analytics-service/src/main/java/nl/invokedynamic/demo/analytics/config/KeycloakRealmRoleConverter.java`
- [x] T009 [P] Implement `JwtMultiIssuerValidator` in `services/analytics-service/src/main/java/nl/invokedynamic/demo/analytics/config/JwtMultiIssuerValidator.java`

**Checkpoint**: Foundation ready - Keycloak realm role converter and multi-issuer validation components available across all services.

---

## Phase 3: User Story 1 - Restrict Restaurant Management Operations to Authorized Managers and Administrators (Priority: P1) 🎯 MVP

**Goal**: Restrict establishment creation, table setup, schedules, reservation listings, status transitions, and analytics to `RESTAURANT_MANAGER` and `ADMIN`. Allow public/authenticated catalog read queries.

**Independent Test**: Invoking `POST /api/v1/restaurants`, table setup, reservation status update, or analytics with a `customer1` token returns HTTP 403 Forbidden, while the same requests succeed with `manager1` or `admin1` tokens.

### Tests for User Story 1

- [x] T010 [P] [US1] Add RBAC security filter tests in `services/restaurant-service/src/test/java/nl/invokedynamic/demo/restaurant/RestaurantSecurityTest.java`
- [x] T011 [P] [US1] Add restaurant-wide query and status update RBAC tests in `services/reservation-service/src/test/java/nl/invokedynamic/demo/reservation/ReservationSecurityTest.java`
- [x] T012 [P] [US1] Create security filter test suite in `services/analytics-service/src/test/java/nl/invokedynamic/demo/analytics/AnalyticsSecurityTest.java`

### Implementation for User Story 1

- [x] T013 [US1] Configure `SecurityConfig` in `services/restaurant-service/src/main/java/nl/invokedynamic/demo/restaurant/config/SecurityConfig.java` to wire `KeycloakRealmRoleConverter` and restrict `POST`/`PUT` endpoints to `RESTAURANT_MANAGER`/`ADMIN` and `GET` to all authenticated roles
- [x] T014 [US1] Configure `SecurityConfig` in `services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/config/SecurityConfig.java` to restrict restaurant listing (`GET /api/v1/reservations`) and status updates (`PATCH /api/v1/reservations/*/status`) to `RESTAURANT_MANAGER`/`ADMIN`
- [x] T015 [US1] Implement `SecurityConfig` in `services/analytics-service/src/main/java/nl/invokedynamic/demo/analytics/config/SecurityConfig.java` with OAuth2 Resource Server, dual-issuer decoders, and restrict `/api/v1/analytics/**` to `RESTAURANT_MANAGER`/`ADMIN`

**Checkpoint**: User Story 1 complete - Restaurant management, reservation administration, and analytics endpoints strictly enforce `RESTAURANT_MANAGER` and `ADMIN` privileges.

---

## Phase 4: User Story 2 - Restrict Dining Operations to Authorized Customers (Priority: P1)

**Goal**: Restrict table reservations, waitlist entry, offer acceptance, and customer profiles exclusively to callers possessing the `CUSTOMER` role. Allow reservation lookup/cancellation and availability checks to all platform roles.

**Independent Test**: Invoking `POST /api/v1/reservations`, `POST /api/v1/waiting-list`, or `GET /api/v1/customers/me` with a pure `manager1` or `admin1` token returns HTTP 403 Forbidden, but succeeds when presented with `customer1` token.

### Tests for User Story 2

- [x] T016 [P] [US2] Add customer booking and multi-role reservation inspection/cancellation tests in `services/reservation-service/src/test/java/nl/invokedynamic/demo/reservation/ReservationSecurityTest.java`
- [x] T017 [P] [US2] Add waiting list entry and offer claim RBAC tests in `services/waiting-list-service/src/test/java/nl/invokedynamic/demo/waitinglist/WaitingListSecurityTest.java`
- [x] T018 [P] [US2] Add customer profile RBAC tests in `services/customer-service/src/test/java/nl/invokedynamic/demo/customer/CustomerSecurityTest.java`
- [x] T019 [P] [US2] Add multi-role availability query tests in `services/availability-service/src/test/java/nl/invokedynamic/demo/availability/AvailabilitySecurityTest.java`

### Implementation for User Story 2

- [x] T020 [US2] Update `SecurityConfig` in `services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/config/SecurityConfig.java` to require `CUSTOMER` for `POST /api/v1/reservations`, and permit `CUSTOMER`, `RESTAURANT_MANAGER`, `ADMIN` for `GET` and `DELETE` on `/api/v1/reservations/*`
- [x] T021 [US2] Configure `SecurityConfig` in `services/waiting-list-service/src/main/java/nl/invokedynamic/demo/waitinglist/config/SecurityConfig.java` to wire `KeycloakRealmRoleConverter` and restrict `POST /api/v1/waiting-list/**` to `CUSTOMER`
- [x] T022 [US2] Configure `SecurityConfig` in `services/customer-service/src/main/java/nl/invokedynamic/demo/customer/config/SecurityConfig.java` to wire `KeycloakRealmRoleConverter` and restrict `/api/v1/customers/**` to `CUSTOMER`
- [x] T023 [US2] Configure `SecurityConfig` in `services/availability-service/src/main/java/nl/invokedynamic/demo/availability/config/SecurityConfig.java` to wire `KeycloakRealmRoleConverter` and require `CUSTOMER`, `RESTAURANT_MANAGER`, or `ADMIN` on `GET /api/v1/availability/**`

**Checkpoint**: User Stories 1 and 2 complete - Full RBAC enforcement active across all customer and administrative endpoints.

---

## Phase 5: User Story 3 - Role Responsibilities Documentation & Port-Accurate Walkthrough (Priority: P2)

**Goal**: Document role responsibilities in `README.md` and update the Interactive End-to-End Walkthrough with direct service ports (`8083`–`8087`) and role-authenticated curl examples.

**Independent Test**: Executing the updated curl commands in `README.md` sequentially against direct ports with corresponding `manager1` and `customer1` tokens completes the entire Rube Goldberg event chain without 403 or connection errors.

### Implementation for User Story 3

- [x] T024 [US3] Add Role-Based Access Control (RBAC) responsibility matrix and permission table in `README.md`
- [x] T025 [US3] Update "Interactive End-to-End Walkthrough" in `README.md` to acquire `$MANAGER_TOKEN` and `$CUSTOMER_TOKEN`, and retarget commands to direct ports (Restaurant: `8083`, Availability: `8084`, Reservation: `8085`, Waiting List: `8086`, Analytics: `8087`)

**Checkpoint**: Documentation aligns 100% with the RBAC matrix and executable walkthrough instructions.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Verification, quality gates, and regression testing across the reactor

- [x] T026 [P] Verify public accessibility of Swagger UI and OpenAPI docs across all services via test assertions
- [x] T027 Run reactor-wide build and test verification via `./mvnw clean test`
- [x] T028 Validate quickstart scenarios against `specs/007-role-endpoint-access/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Can start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 completion - BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Phase 2 completion
- **User Story 2 (Phase 4)**: Depends on Phase 2 completion (can proceed in parallel with US1)
- **User Story 3 (Phase 5)**: Depends on US1 and US2 endpoint contracts being finalized
- **Polish (Phase 6)**: Depends on all user story phases being complete

### User Story Dependencies

- **User Story 1 (P1)**: Independent of US2 (focuses on management & analytics services)
- **User Story 2 (P1)**: Independent of US1 (focuses on customer, reservation, waiting list, and availability services)
- **User Story 3 (P2)**: Documents the end-to-end flow utilizing both US1 (management) and US2 (customer) endpoints

---

## Parallel Execution Examples

### Parallel Opportunities in Phase 2 (Foundational)
```bash
# Implement KeycloakRealmRoleConverter in all services concurrently:
T003 (restaurant-service) & T004 (reservation-service) & T005 (waiting-list-service) &
T006 (customer-service) & T007 (availability-service) & T008 (analytics-service) &
T009 (analytics-service JwtMultiIssuerValidator)
```

### Parallel Opportunities in Phase 3 (User Story 1 Tests)
```bash
# Write security slice tests concurrently:
T010 (RestaurantSecurityTest) & T011 (ReservationSecurityTest) & T012 (AnalyticsSecurityTest)
```

### Parallel Opportunities in Phase 4 (User Story 2 Tests)
```bash
# Write customer security slice tests concurrently:
T016 (ReservationSecurityTest) & T017 (WaitingListSecurityTest) &
T018 (CustomerSecurityTest) & T019 (AvailabilitySecurityTest)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)
1. Complete Phase 1 (Setup) and Phase 2 (Foundational).
2. Implement Phase 3 (User Story 1) to secure restaurant management and analytics.
3. Validate independent tests: `customer1` receives 403 on administrative endpoints; `manager1`/`admin1` succeed.

### Incremental Delivery
1. Foundation (Phase 1 + 2) ready.
2. User Story 1 (Phase 3): Management & analytics secured.
3. User Story 2 (Phase 4): Customer booking, waitlist, and profile secured.
4. User Story 3 (Phase 5): README RBAC matrix and walkthrough updated.
5. Polish (Phase 6): Reactor test suite passed cleanly (`mvn clean test`).
