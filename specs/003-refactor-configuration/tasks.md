# Tasks: Refactor Configuration to Dedicated Classes

**Branch**: `003-refactor-configuration` | **Date**: 2026-09-11 | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify project baseline and environment readiness across all submodules.

- [X] T001 Verify project baseline and test suite passing via ./mvnw test-compile

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Establish configuration package structures across all microservices before extracting bean definitions.

**⚠️ CRITICAL**: Must complete before user stories begin.

- [X] T002 Establish config directories (nl/invokedynamic/demo/<service>/config) across gateway and all 7 domain services

**Checkpoint**: Foundation ready - user story implementation can proceed.

---

## Phase 3: User Story 1 - Dedicated Serialization Configuration in Domain Services (Priority: P1) 🎯 MVP

**Goal**: Extract `ObjectMapper` bean definitions from `@SpringBootApplication` entrypoint classes into dedicated `JacksonConfig.java` components in `nl.invokedynamic.demo.<service>.config` across all 7 domain services.

**Independent Test**: Execute service unit and slice tests to confirm that `ObjectMapper` beans are cleanly instantiated and injected without errors.

### Implementation for User Story 1

- [X] T003 [P] [US1] Create JacksonConfig in services/customer-service/src/main/java/nl/invokedynamic/demo/customer/config/JacksonConfig.java
- [X] T004 [P] [US1] Create JacksonConfig in services/restaurant-service/src/main/java/nl/invokedynamic/demo/restaurant/config/JacksonConfig.java
- [X] T005 [P] [US1] Create JacksonConfig in services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/config/JacksonConfig.java
- [X] T006 [P] [US1] Create JacksonConfig in services/availability-service/src/main/java/nl/invokedynamic/demo/availability/config/JacksonConfig.java
- [X] T007 [P] [US1] Create JacksonConfig in services/waiting-list-service/src/main/java/nl/invokedynamic/demo/waitinglist/config/JacksonConfig.java
- [X] T008 [P] [US1] Create JacksonConfig in services/notification-service/src/main/java/nl/invokedynamic/demo/notification/config/JacksonConfig.java
- [X] T009 [P] [US1] Create JacksonConfig in services/analytics-service/src/main/java/nl/invokedynamic/demo/analytics/config/JacksonConfig.java
- [X] T010 [P] [US1] Remove ObjectMapper bean from services/customer-service/src/main/java/nl/invokedynamic/demo/customer/CustomerServiceApplication.java
- [X] T011 [P] [US1] Remove ObjectMapper bean from services/restaurant-service/src/main/java/nl/invokedynamic/demo/restaurant/RestaurantServiceApplication.java
- [X] T012 [P] [US1] Remove ObjectMapper bean from services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/ReservationServiceApplication.java
- [X] T013 [P] [US1] Remove ObjectMapper bean from services/availability-service/src/main/java/nl/invokedynamic/demo/availability/AvailabilityServiceApplication.java
- [X] T014 [P] [US1] Remove ObjectMapper bean from services/waiting-list-service/src/main/java/nl/invokedynamic/demo/waitinglist/WaitingListServiceApplication.java
- [X] T015 [P] [US1] Remove ObjectMapper bean from services/notification-service/src/main/java/nl/invokedynamic/demo/notification/NotificationServiceApplication.java
- [X] T016 [P] [US1] Remove ObjectMapper bean from services/analytics-service/src/main/java/nl/invokedynamic/demo/analytics/AnalyticsServiceApplication.java
- [X] T017 [US1] Validate domain microservice serialization bean injection and run domain unit/slice tests via ./mvnw test -pl services/customer-service,services/restaurant-service,services/reservation-service,services/availability-service,services/waiting-list-service,services/notification-service,services/analytics-service

**Checkpoint**: User Story 1 complete - all domain microservices use dedicated `JacksonConfig` classes with zero `@Bean` methods in their entrypoints.

---

## Phase 4: User Story 2 - Modular Gateway Security Configuration (Priority: P2)

**Goal**: Extract the `SecurityWebFilterChain` bean and `@EnableWebFluxSecurity` annotation from `GatewayApplication.java` into a dedicated `SecurityConfig.java` in `nl.invokedynamic.demo.gateway.config`.

**Independent Test**: Run `GatewayApplicationTest` to confirm reactive security configuration and routing policies initialize cleanly.

### Implementation for User Story 2

- [X] T018 [US2] Create SecurityConfig in gateway/src/main/java/nl/invokedynamic/demo/gateway/config/SecurityConfig.java
- [X] T019 [US2] Remove SecurityWebFilterChain bean and @EnableWebFluxSecurity from gateway/src/main/java/nl/invokedynamic/demo/gateway/GatewayApplication.java
- [X] T020 [US2] Validate gateway security filter chain and routing via gateway/src/test/java/nl/invokedynamic/demo/gateway/GatewayApplicationTest.java

**Checkpoint**: User Story 2 complete - API Gateway security filter chain is cleanly isolated in `SecurityConfig`.

---

## Phase 5: User Story 3 - Dedicated Caching Configuration in Availability Service (Priority: P3)

**Goal**: Extract the `RedisCacheManager` bean and `@EnableCaching` annotation from `AvailabilityServiceApplication.java` into a dedicated `CacheConfig.java` in `nl.invokedynamic.demo.availability.config`.

**Independent Test**: Run availability service tests to verify table availability queries and cache operations succeed.

### Implementation for User Story 3

- [X] T021 [US3] Create CacheConfig in services/availability-service/src/main/java/nl/invokedynamic/demo/availability/config/CacheConfig.java
- [X] T022 [US3] Remove CacheManager bean from services/availability-service/src/main/java/nl/invokedynamic/demo/availability/AvailabilityServiceApplication.java
- [X] T023 [US3] Validate availability caching operations and run availability tests via ./mvnw test -pl services/availability-service

**Checkpoint**: User Story 3 complete - Redis caching infrastructure in availability-service is isolated in `CacheConfig`.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: End-to-end verification, static compliance checking, and artifact packaging.

- [X] T024 Perform static grep validation ensuring zero @Bean annotations remain in any *Application.java
- [X] T025 Execute full Maven reactor build and test suite via ./mvnw clean test
- [X] T026 Repackage executable Spring Boot JAR archives via ./mvnw package -DskipTests
- [X] T027 Run quickstart.md validation scenarios

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately.
- **Foundational (Phase 2)**: Depends on Setup completion - creates `config` packages for all submodules.
- **User Story 1 (Phase 3)**: Depends on Phase 2 - delivers serialization configuration decoupling (MVP).
- **User Story 2 (Phase 4)**: Depends on Phase 2 - can run in parallel with US1 or sequentially.
- **User Story 3 (Phase 5)**: Depends on Phase 2 (and US1 for `availability-service` clean separation).
- **Polish (Phase 6)**: Depends on all user stories (US1, US2, US3) being complete.

### Within Each User Story

- Create `@Configuration(proxyBeanMethods = false)` class in `config/` package.
- Remove `@Bean` declaration from `*Application.java`.
- Run module-specific tests to verify successful bean injection.

### Parallel Opportunities

- **US1 JacksonConfig Creation**: Tasks T003 through T009 affect distinct files and can all run in parallel.
- **US1 Application Cleanup**: Tasks T010 through T016 affect distinct files and can all run in parallel.
- **US1, US2, and US3**: User Story 2 (`gateway`) and User Story 1 (`services/*`) touch completely disjoint modules and can execute concurrently.

---

## Implementation Strategy

### MVP First (User Story 1 Only)
1. Complete Phase 1 (Setup) and Phase 2 (Foundational package creation).
2. Complete Phase 3 (User Story 1 - `JacksonConfig` extraction across all 7 domain services).
3. Validate via T017.

### Incremental Delivery
1. Foundation & US1 (MVP): Decouples serialization across all backend microservices.
2. US2: Decouples reactive security configuration in the API Gateway.
3. US3: Decouples Redis caching configuration in the availability service.
4. Polish: Validates complete eradication of `@Bean` methods in application classes and executes full reactor packaging.
