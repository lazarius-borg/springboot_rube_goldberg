# Tasks: Restaurant Reservation Platform (Rube Goldberg Showcase)

**Branch**: `001-restaurant-reservation-platform` | **Date**: 2026-08-31 | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

## Phase 1: Setup (Multi-Module Maven Reactor & Baseline)

**Purpose**: Establish the root Maven reactor project, Maven Wrapper, parent POM configuration, Java 26 / Spring Boot 3.4 baseline, and module directory structure.

- [x] T001 Initialize repository root Maven reactor POM with `groupId: nl.invokedynamic.demo`, `artifactId: springboot.rubegoldberg`, dependency management BOMs (Spring Boot 3.4+, Spring Cloud), Java 26 compiler target, and Jib plugin in pom.xml
- [x] T002 Initialize Maven wrapper executable scripts and properties in mvnw, mvnw.cmd, and .mvn/wrapper/maven-wrapper.properties
- [x] T003 [P] Create submodule skeleton directories and submodule POMs for `gateway`, `services/customer-service`, `services/restaurant-service`, `services/reservation-service`, `services/availability-service`, `services/waiting-list-service`, `services/notification-service`, `services/analytics-service`
- [x] T004 [P] Create common shared build/event contracts module in `common/event-contracts/pom.xml`

---

## Phase 2: Foundational (Cross-Cutting Infrastructure & Platform)

**Purpose**: Provision local container infrastructure, database migration baselines, OIDC security, and shared transactional outbox patterns.

- [x] T005 [P] Create Docker Compose environment definitions for PostgreSQL (with multi-database init scripts), Kafka, Redis, Keycloak, Mailpit, OTel Collector, Prometheus, Grafana, OpenSearch, and Alertmanager in infrastructure/docker-compose.yml
- [x] T006 [P] Create Keycloak realm export file with seeded clients (`rube-goldberg-app`), roles (`CUSTOMER`, `RESTAURANT_MANAGER`, `ADMIN`), and demo users (`customer1`, `manager1`) in infrastructure/keycloak/realm-export.json
- [x] T007 [P] Create OpenTelemetry Collector configuration pipeline (OTLP receivers -> Prometheus exporter, OpenSearch exporter) in infrastructure/opentelemetry/otel-collector-config.yaml
- [x] T008 [P] Implement Spring Cloud Gateway routing rules, JWT authentication token relay, and distributed rate limiting with Redis in gateway/src/main/resources/application.yml and gateway/src/main/java/nl/invokedynamic/demo/gateway/GatewayApplication.java
- [x] T009 [P] Create generic reusable Transactional Outbox entity, JPA repository, and Kafka publisher component in services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/outbox/OutboxEventPublisher.java
- [x] T010 [P] Create generic idempotent consumer repository and event deduplication aspect in services/notification-service/src/main/java/nl/invokedynamic/demo/notification/idempotency/IdempotentConsumerAspect.java

**Checkpoint**: Core infrastructure, reactor build, and common platform patterns ready.

---

## Phase 3: User Story 1 - Restaurant Configuration & Management (Priority: P1)

**Goal**: Enable restaurant managers to register dining establishments, configure multiple opening hour intervals/exceptions, manage tables, and define combinable table groups.

**Independent Test**: Execute `RestaurantControllerIntegrationTest` using Testcontainers PostgreSQL to verify restaurant creation, table inventory setup, and opening hours retrieval against REST endpoints.

### Tests for User Story 1
- [x] T011 [P] [US1] Unit test table capacity and combination validation logic in services/restaurant-service/src/test/java/nl/invokedynamic/demo/restaurant/domain/RestaurantTableTest.java
- [x] T012 [P] [US1] Integration test for restaurant and opening hours API endpoints in services/restaurant-service/src/test/java/nl/invokedynamic/demo/restaurant/api/RestaurantControllerIntegrationTest.java

### Implementation for User Story 1
- [x] T013 [P] [US1] Create Flyway migration script for `restaurant`, `opening_hours`, `restaurant_table`, and `table_combination` tables in services/restaurant-service/src/main/resources/db/migration/V1__init_restaurant_schema.sql
- [x] T014 [P] [US1] Create JPA Entities (`RestaurantEntity`, `OpeningHoursEntity`, `RestaurantTableEntity`, `TableCombinationEntity`) in services/restaurant-service/src/main/java/nl/invokedynamic/demo/restaurant/domain/
- [x] T015 [P] [US1] Create Spring Data JPA repositories in services/restaurant-service/src/main/java/nl/invokedynamic/demo/restaurant/repository/
- [x] T016 [US1] Implement `RestaurantService` business logic (validating IANA timezone, duration, advance booking rules, table combinations) in services/restaurant-service/src/main/java/nl/invokedynamic/demo/restaurant/service/RestaurantService.java
- [x] T017 [US1] Implement REST Controllers with RFC 9457 Problem Details and OpenAPI annotations in services/restaurant-service/src/main/java/nl/invokedynamic/demo/restaurant/api/RestaurantController.java
- [x] T018 [US1] Publish `RestaurantCreatedEvent` and `TableConfigurationChangedEvent` via outbox in services/restaurant-service/src/main/java/nl/invokedynamic/demo/restaurant/events/RestaurantEventPublisher.java

**Checkpoint**: Restaurant service fully functional and verified independently.

---

## Phase 4: User Story 2 - Customer Discovery & Real-Time Availability Search (Priority: P1)

**Goal**: Allow customers to query table availability for a specific restaurant, date, time, and party size with sub-50ms latency using Redis read-model caching.

**Independent Test**: Execute `AvailabilityServiceIntegrationTest` verifying that available slots reflect operating hours and table combinations and that query responses are cached in Redis.

### Tests for User Story 2
- [x] T019 [P] [US2] Unit test timezone-aware slot generation and table occupancy calculations in services/availability-service/src/test/java/nl/invokedynamic/demo/availability/domain/SlotCalculatorTest.java
- [x] T020 [P] [US2] Integration test with Testcontainers PostgreSQL & Redis in services/availability-service/src/test/java/nl/invokedynamic/demo/availability/api/AvailabilityControllerIntegrationTest.java

### Implementation for User Story 2
- [x] T021 [P] [US2] Create Flyway migration for availability read models (`restaurant_view`, `table_inventory_view`, `slot_occupancy_view`) in services/availability-service/src/main/resources/db/migration/V1__init_availability_schema.sql
- [x] T022 [P] [US2] Create Kafka consumer listeners for `RestaurantCreatedEvent`, `ReservationCreatedEvent`, and `ReservationCancelledEvent` to update read projections in services/availability-service/src/main/java/nl/invokedynamic/demo/availability/events/AvailabilityEventListener.java
- [x] T023 [US2] Implement `AvailabilityService` with Redis cache lookup and fallback query calculation in services/availability-service/src/main/java/nl/invokedynamic/demo/availability/service/AvailabilityService.java
- [x] T024 [US2] Implement `/api/v1/availability` REST endpoint and OpenAPI documentation in services/availability-service/src/main/java/nl/invokedynamic/demo/availability/api/AvailabilityController.java

**Checkpoint**: Real-time availability search active with event-driven cache invalidation.

---

## Phase 5: User Story 3 - Guaranteed Reservation Creation & Automatic Table Allocation (Priority: P1) 🎯 MVP

**Goal**: Authoritatively allocate optimal tables (smallest sufficient single table or valid combination), persist confirmed reservations, enforce strict concurrency/double-booking protection, and write `ReservationCreated` events to the transactional outbox.

**Independent Test**: Run concurrent multithreaded test verifying that simultaneous booking requests for the last available table result in exactly 1 confirmed reservation and 1 conflict rejection.

### Tests for User Story 3
- [x] T025 [P] [US3] Unit test deterministic table allocation algorithm (prefer smallest single table, fallback to combination) in services/reservation-service/src/test/java/nl/invokedynamic/demo/reservation/domain/TableAllocatorTest.java
- [x] T026 [P] [US3] Concurrency integration test with Testcontainers PostgreSQL verifying exclusion constraints in services/reservation-service/src/test/java/nl/invokedynamic/demo/reservation/api/ConcurrentReservationTest.java

### Implementation for User Story 3
- [x] T027 [P] [US3] Create Flyway migration script for `reservation`, `reservation_table_allocation`, and `outbox_events` tables with exclusion indexes in services/reservation-service/src/main/resources/db/migration/V1__init_reservation_schema.sql
- [x] T028 [P] [US3] Create JPA Entities (`ReservationEntity`, `ReservationTableAllocationEntity`, `OutboxEventEntity`) in services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/domain/
- [x] T029 [US3] Implement `TableAllocationEngine` to match smallest sufficient single table or valid combination in services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/domain/TableAllocationEngine.java
- [x] T030 [US3] Implement `ReservationService.createReservation()` with atomic outbox event persistence in services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/service/ReservationService.java
- [x] T031 [US3] Implement REST endpoint `POST /api/v1/reservations` with Problem Details conflict handling in services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/api/ReservationController.java
- [x] T032 [US3] Implement scheduled outbox polling worker dispatching events to Kafka in services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/outbox/ScheduledOutboxPoller.java

**Checkpoint**: Core transactional booking MVP functional with zero double-booking guarantee.

---

## Phase 6: User Story 4 - Reservation Lifecycle Management & Cancellation (Priority: P2)

**Goal**: Support reservation retrieval, customer cancellations within the cancellation window, and manager status transitions (`CONFIRMED` -> `ARRIVED` -> `COMPLETED`, `NO_SHOW`, `CANCELLED`).

**Independent Test**: Execute lifecycle integration test verifying policy deadline rejection and successful table release on valid cancellation.

### Tests for User Story 4
- [x] T033 [P] [US4] Unit test reservation status transition state machine in services/reservation-service/src/test/java/nl/invokedynamic/demo/reservation/domain/ReservationStateMachineTest.java
- [x] T034 [P] [US4] Integration test for cancellation policy window and manager status transitions in services/reservation-service/src/test/java/nl/invokedynamic/demo/reservation/api/ReservationLifecycleIntegrationTest.java

### Implementation for User Story 4
- [x] T035 [US4] Implement cancellation validation checking restaurant's `cancellation_window_hours` in services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/service/CancellationPolicyValidator.java
- [x] T036 [US4] Implement `DELETE /api/v1/reservations/{id}` producing `ReservationCancelledEvent` in outbox in services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/api/ReservationController.java
- [x] T037 [US4] Implement `PATCH /api/v1/reservations/{id}/status` for status updates in services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/api/ReservationController.java

**Checkpoint**: Reservation lifecycle and policy-enforced cancellation complete.

---

## Phase 7: User Story 5 - Fair FIFO Waiting List & Automated Offer Lifecycle (Priority: P2)

**Goal**: Enable customers to join a waiting list and automatically match cancelled reservation slots to the oldest matching entry (FIFO), dispatching time-limited offers that expire if not accepted.

**Independent Test**: Test waiting list matching by adding two waiting entries, publishing a cancellation event, and verifying that only the earliest entry receives an offer.

### Tests for User Story 5
- [x] T038 [P] [US5] Unit test FIFO matching priority and time boundary overlap in services/waiting-list-service/src/test/java/nl/invokedynamic/demo/waitinglist/domain/FifoMatcherTest.java
- [x] T039 [P] [US5] Integration test for offer generation and expiration workflow in services/waiting-list-service/src/test/java/nl/invokedynamic/demo/waitinglist/api/WaitingListIntegrationTest.java

### Implementation for User Story 5
- [x] T040 [P] [US5] Create Flyway migration for `waiting_list_entry`, `waiting_list_offer`, `outbox_events`, and `processed_events` in services/waiting-list-service/src/main/resources/db/migration/V1__init_waiting_list_schema.sql
- [x] T041 [P] [US5] Create JPA Entities (`WaitingListEntryEntity`, `WaitingListOfferEntity`) in services/waiting-list-service/src/main/java/nl/invokedynamic/demo/waitinglist/domain/
- [x] T042 [US5] Implement Kafka consumer for `ReservationCancelledEvent` triggering FIFO offer evaluation in services/waiting-list-service/src/main/java/nl/invokedynamic/demo/waitinglist/events/ReservationCancelledListener.java
- [x] T043 [US5] Implement `WaitingListService` (join list, accept offer, cascade next candidate) in services/waiting-list-service/src/main/java/nl/invokedynamic/demo/waitinglist/service/WaitingListService.java
- [x] T044 [US5] Implement scheduled background job to expire pending offers past deadline in services/waiting-list-service/src/main/java/nl/invokedynamic/demo/waitinglist/service/OfferExpirationScheduler.java
- [x] T045 [US5] Implement REST endpoints `POST /api/v1/waiting-list` and `POST /api/v1/waiting-list/offers/{id}/accept` in services/waiting-list-service/src/main/java/nl/invokedynamic/demo/waitinglist/api/WaitingListController.java

**Checkpoint**: Waiting list FIFO matchmaking and offer lifecycle active.

---

## Phase 8: User Story 6 - Automated Customer Notifications & Reminders (Priority: P3)

**Goal**: Consume business events from Kafka, render email templates, deliver messages to Mailpit, and execute scheduled reservation reminders with idempotency protection.

**Independent Test**: Integration test verifying that `ReservationCreatedEvent` and `WaitingListOfferCreatedEvent` result in dispatched emails received in Mailpit.

### Tests for User Story 6
- [x] T046 [P] [US6] Unit test email template rendering and reminder schedule calculations in services/notification-service/src/test/java/nl/invokedynamic/demo/notification/domain/EmailTemplateRendererTest.java
- [x] T047 [P] [US6] Integration test with Kafka and Mailpit container in services/notification-service/src/test/java/nl/invokedynamic/demo/notification/api/NotificationServiceIntegrationTest.java

### Implementation for User Story 6
- [x] T048 [P] [US6] Create Flyway migration for `notification_log`, `reminder_schedule`, and `processed_events` in services/notification-service/src/main/resources/db/migration/V1__init_notification_schema.sql
- [x] T049 [P] [US6] Implement HTML/Text email template renderer in services/notification-service/src/main/java/nl/invokedynamic/demo/notification/template/EmailTemplateRenderer.java
- [x] T050 [US6] Implement Spring JavaMailSender adapter connecting to Mailpit in services/notification-service/src/main/java/nl/invokedynamic/demo/notification/mail/MailpitEmailSender.java
- [x] T051 [US6] Implement Kafka listeners for `ReservationCreated`, `ReservationCancelled`, `WaitingListOfferCreated` in services/notification-service/src/main/java/nl/invokedynamic/demo/notification/events/NotificationEventListener.java
- [x] T052 [US6] Implement scheduled background runner for upcoming reservation reminders with deduplication locks in services/notification-service/src/main/java/nl/invokedynamic/demo/notification/scheduler/ReservationReminderScheduler.java

**Checkpoint**: Asynchronous notification pipeline and Mailpit email delivery verified.

---

## Phase 9: User Story 7 - Read-Only Business Analytics & Operational Telemetry (Priority: P3)

**Goal**: Aggregate real-time reservation metrics, cancellation rates, no-show counts, and waiting list conversion statistics from Kafka events in an isolated read-only service.

**Independent Test**: Publish test events and verify that `/api/v1/analytics/summary` reflects exact calculated metrics.

### Tests for User Story 7
- [x] T053 [P] [US7] Unit test metric accumulation and conversion rate math in services/analytics-service/src/test/java/nl/invokedynamic/demo/analytics/domain/MetricsCalculatorTest.java
- [x] T054 [P] [US7] Integration test verifying event consumption and analytics summary output in services/analytics-service/src/test/java/nl/invokedynamic/demo/analytics/api/AnalyticsIntegrationTest.java

### Implementation for User Story 7
- [x] T055 [P] [US7] Create Flyway migration for `reservation_daily_metrics`, `waiting_list_daily_metrics`, and `processed_events` in services/analytics-service/src/main/resources/db/migration/V1__init_analytics_schema.sql
- [x] T056 [P] [US7] Implement JPA entities and repositories in services/analytics-service/src/main/java/nl/invokedynamic/demo/analytics/domain/
- [x] T057 [US7] Implement idempotent Kafka consumer updating daily aggregations in services/analytics-service/src/main/java/nl/invokedynamic/demo/analytics/events/AnalyticsEventListener.java
- [x] T058 [US7] Implement REST endpoint `GET /api/v1/analytics/summary` in services/analytics-service/src/main/java/nl/invokedynamic/demo/analytics/api/AnalyticsController.java

**Checkpoint**: Analytics service consuming events independently without coupling to core transactional services.

---

## Phase 10: User Story 8 - Customer Profile & Minimal Web Portal Experience (Priority: P3)

**Goal**: Provide customer profile management (Keycloak subject mapping) and a responsive minimal web UI demonstrating end-to-end customer and manager workflows.

**Independent Test**: Test customer registration and portal navigation through the API Gateway.

### Tests for User Story 8
- [x] T059 [P] [US8] Integration test for customer profile creation and Keycloak token mapping in services/customer-service/src/test/java/nl/invokedynamic/demo/customer/api/CustomerControllerIntegrationTest.java

### Implementation for User Story 8
- [x] T060 [P] [US8] Create Flyway migration for `customer_profile` in services/customer-service/src/main/resources/db/migration/V1__init_customer_schema.sql
- [x] T061 [P] [US8] Implement `CustomerService` and REST endpoints in services/customer-service/src/main/java/nl/invokedynamic/demo/customer/
- [x] T062 [P] [US8] Implement minimal web UI customer portal (search availability, make reservation, view waiting list) in ui/src/customer/index.html and ui/src/customer/app.js
- [x] T063 [P] [US8] Implement minimal web UI restaurant manager portal (table management, opening hours, live roster) in ui/src/manager/index.html and ui/src/manager/app.js

**Checkpoint**: End-to-end portal workflows operational for both customer and manager roles.

---

## Phase 11: Polish, Observability Dashboards, Kubernetes Manifests & Verification

**Purpose**: Observability dashboards, Alertmanager rules, Kubernetes deployment artifacts, and full end-to-end walkthrough verification.

- [x] T064 [P] Create Grafana dashboards for JVM metrics, HTTP throughput, Kafka lag, and reservation business metrics in infrastructure/grafana/dashboards/
- [x] T065 [P] Create Prometheus alerting rules and Alertmanager routing config in infrastructure/prometheus/alert-rules.yml and infrastructure/alertmanager/alertmanager.yml
- [x] T066 [P] Create Kubernetes deployment manifests with Actuator liveness/readiness probes, ConfigMaps, and Services for all 8 microservices in k8s/deployments/
- [x] T067 [P] Implement end-to-end integration test validating the complete Rube Goldberg walkthrough in tests/e2e/src/test/java/nl/invokedynamic/demo/e2e/RubeGoldbergEndToEndTest.java
- [x] T068 Execute full root Maven build `./mvnw clean verify` and run quickstart walkthrough verification per specs/001-restaurant-reservation-platform/quickstart.md
