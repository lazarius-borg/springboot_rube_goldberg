# Tasks: Upgrade Spring Boot Version

**Branch**: `002-upgrade-springboot-version` | **Date**: 2026-08-31 | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

## Phase 1: Setup (Parent POM & Dependency Alignment)

**Purpose**: Update the root parent Maven reactor POM and aligned companion BOMs.

- [x] T001 Update Spring Boot version property and BOM in pom.xml
- [x] T002 [P] Update Spring Cloud release train BOM version in pom.xml
- [x] T003 [P] Update Springdoc OpenAPI starter dependency versions in pom.xml
- [x] T004 [P] Update Testcontainers BOM and Surefire plugin configuration in pom.xml

---

## Phase 2: Foundational (Submodule Starters & Build Baseline)

**Purpose**: Verify all submodule POM configurations and compiler compatibility.

- [x] T005 [P] Verify starter dependencies in gateway/pom.xml
- [x] T006 [P] Verify starter dependencies across all service POMs in services/*/pom.xml
- [x] T007 [P] Verify shared contracts POM in common/event-contracts/pom.xml

---

## Phase 3: User Story 1 - Platform-Wide Framework Upgrade (Priority: P1) 🎯 MVP

**Goal**: Ensure all 8 microservices and gateway compile and execute under the upgraded framework version.

**Independent Test**: The full Maven reactor build compiles cleanly and starts services with Actuator health probes returning UP.

### Implementation for User Story 1
- [x] T008 [P] [US1] Modernize Spring Boot starter imports in services/customer-service/src/main/java/nl/invokedynamic/demo/customer/CustomerServiceApplication.java
- [x] T009 [P] [US1] Modernize Spring Boot starter imports in services/restaurant-service/src/main/java/nl/invokedynamic/demo/restaurant/RestaurantServiceApplication.java
- [x] T010 [P] [US1] Modernize Spring Boot starter imports in services/availability-service/src/main/java/nl/invokedynamic/demo/availability/AvailabilityServiceApplication.java
- [x] T011 [P] [US1] Modernize Spring Boot starter imports in services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/ReservationServiceApplication.java
- [x] T012 [P] [US1] Modernize Spring Boot starter imports in services/waiting-list-service/src/main/java/nl/invokedynamic/demo/waitinglist/WaitingListServiceApplication.java
- [x] T013 [P] [US1] Modernize Spring Boot starter imports in services/notification-service/src/main/java/nl/invokedynamic/demo/notification/NotificationServiceApplication.java
- [x] T014 [P] [US1] Modernize Spring Boot starter imports in services/analytics-service/src/main/java/nl/invokedynamic/demo/analytics/AnalyticsServiceApplication.java
- [x] T015 [P] [US1] Modernize Spring Cloud Gateway starter imports in gateway/src/main/java/nl/invokedynamic/demo/gateway/GatewayApplication.java

**Checkpoint**: All microservices compile cleanly with updated starters.

---

## Phase 4: User Story 2 - Idiomatic Configuration & Annotation Modernization (Priority: P2)

**Goal**: Refactor configuration properties, security beans, and test slicing annotations to modern standards.

**Independent Test**: Verify zero deprecation warnings during application startup and test execution.

### Implementation for User Story 2
- [x] T016 [P] [US2] Review and modernize configuration properties in services/*/src/main/resources/application.yml
- [x] T017 [P] [US2] Modernize test mocking annotations and test slice configs in services/customer-service/src/test/
- [x] T018 [P] [US2] Modernize test mocking annotations and test slice configs in services/restaurant-service/src/test/
- [x] T019 [P] [US2] Modernize test mocking annotations and test slice configs in services/reservation-service/src/test/
- [x] T020 [P] [US2] Modernize test mocking annotations and test slice configs in services/availability-service/src/test/
- [x] T021 [P] [US2] Modernize test mocking annotations and test slice configs in services/waiting-list-service/src/test/
- [x] T022 [P] [US2] Modernize test mocking annotations and test slice configs in services/notification-service/src/test/
- [x] T023 [P] [US2] Modernize test mocking annotations and test slice configs in services/analytics-service/src/test/

**Checkpoint**: Deprecated testing annotations eliminated; tests run with zero warnings.

---

## Phase 5: User Story 3 - End-to-End Choreography & Functional Validation (Priority: P3)

**Goal**: Verify zero regression across event choreography, caching, matchmaking, and notifications.

**Independent Test**: Execute the full Maven reactor test suite and verify 100% pass rate.

### Implementation for User Story 3
- [x] T024 [US3] Execute full Maven reactor build and test suite via ./mvnw clean test
- [x] T025 [US3] Validate Swagger UI and OpenAPI documentation generation on all services
- [x] T026 [US3] Verify Actuator health and metrics endpoints on all services

---

## Phase 6: Polish & Documentation

**Purpose**: Update documentation and finalize delivery.

- [x] T027 [P] Update version numbers and notes in README.md
- [x] T028 Run quickstart.md validation scenario
