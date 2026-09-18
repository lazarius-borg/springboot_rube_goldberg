# Implementation Tasks: Rely on Jib Container Images in Docker Compose

**Feature**: `018-jib-docker-containers`  
**Date**: 2026-09-18  
**Spec**: [spec.md](file:///Users/lazolazarev/projects/springboot_rube_goldberg/specs/018-jib-docker-containers/spec.md) | **Plan**: [plan.md](file:///Users/lazolazarev/projects/springboot_rube_goldberg/specs/018-jib-docker-containers/plan.md)

---

## Phase 1: Setup (Root POM Configuration)

**Purpose**: Establish base Jib plugin management, default skip properties, and reactor traversal in the root POM.

- [X] T001 Configure root `pom.xml` plugin management with base image `eclipse-temurin:26-jre-alpine`, target image pattern `springboot-rube-goldberg/${project.artifactId}:${project.version}`, dual tag `<tag>latest</tag>`, and set `<jib.skip>true</jib.skip>` in properties
- [X] T002 Add `com.google.cloud.tools:jib-maven-plugin` to root `pom.xml` `<build><plugins>` to enable reactor traversal when invoking `compile jib:dockerBuild`

---

## Phase 2: Foundational (Module Entrypoint & Jib Activation)

**Purpose**: Configure explicit main class declarations and enable Jib across all 8 runnable microservices, bypassing Java 26 ASM bytecode scanning limitations.

**⚠️ CRITICAL**: Must be completed before executing the reactor-wide image build.

- [X] T003 [P] Enable Jib (`<jib.skip>false</jib.skip>`) and configure explicit main class `nl.invokedynamic.demo.gateway.GatewayApplication` in `gateway/pom.xml`
- [X] T004 [P] Enable Jib (`<jib.skip>false</jib.skip>`) and configure explicit main class `nl.invokedynamic.demo.customer.CustomerServiceApplication` in `services/customer-service/pom.xml`
- [X] T005 [P] Enable Jib (`<jib.skip>false</jib.skip>`) and configure explicit main class `nl.invokedynamic.demo.restaurant.RestaurantServiceApplication` in `services/restaurant-service/pom.xml`
- [X] T006 [P] Enable Jib (`<jib.skip>false</jib.skip>`) and configure explicit main class `nl.invokedynamic.demo.availability.AvailabilityServiceApplication` in `services/availability-service/pom.xml`
- [X] T007 [P] Enable Jib (`<jib.skip>false</jib.skip>`) and configure explicit main class `nl.invokedynamic.demo.reservation.ReservationServiceApplication` in `services/reservation-service/pom.xml`
- [X] T008 [P] Enable Jib (`<jib.skip>false</jib.skip>`) and configure explicit main class `nl.invokedynamic.demo.waitinglist.WaitingListServiceApplication` in `services/waiting-list-service/pom.xml`
- [X] T009 [P] Enable Jib (`<jib.skip>false</jib.skip>`) and configure explicit main class `nl.invokedynamic.demo.notification.NotificationServiceApplication` in `services/notification-service/pom.xml`
- [X] T010 [P] Enable Jib (`<jib.skip>false</jib.skip>`) and configure explicit main class `nl.invokedynamic.demo.analytics.AnalyticsServiceApplication` in `services/analytics-service/pom.xml`

**Checkpoint**: Foundation ready - all 8 services declare explicit main classes and inherit parent Jib defaults.

---

## Phase 3: User Story 2 - One-Step Container Image Generation for All Microservices (Priority: P2)

**Goal**: Build all 8 application microservices in parallel via a single Maven command directly into the local Docker daemon, cleanly skipping non-runnable modules (`pom` root and `common/event-contracts`).

**Independent Test**: Run `./mvnw compile jib:dockerBuild -DskipTests` from repository root and verify that 8 images exist in the local Docker daemon tagged with both `1.0.0-SNAPSHOT` and `latest`.

- [X] T011 [US2] Execute `./mvnw compile jib:dockerBuild -DskipTests` from the project root to compile and build container images for all 8 microservices
- [X] T012 [US2] Verify local Docker daemon contains both `1.0.0-SNAPSHOT` and `latest` image tags for all 8 services (`gateway`, `customer-service`, `restaurant-service`, `availability-service`, `reservation-service`, `waiting-list-service`, `notification-service`, `analytics-service`)

**Checkpoint**: Container images for all 8 services successfully built and cached in the local Docker daemon.

---

## Phase 4: User Story 1 - Run Application Services via Jib-Built Images (Priority: P1) 🎯 MVP

**Goal**: Update `docker-compose.apps.yml` to consume pre-built Jib images directly via `image: springboot-rube-goldberg/<service>:${IMAGE_TAG:-latest}`, remove `docker/Dockerfile.service`, and verify clean platform boot.

**Independent Test**: Start the platform with `docker compose -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.apps.yml up -d`, verify no Dockerfile builds occur, and verify all services return HTTP 200 on `/actuator/health`.

- [X] T013 [US1] Update `infrastructure/docker-compose.apps.yml` to replace `build:` blocks with `image: springboot-rube-goldberg/<service>:${IMAGE_TAG:-latest}` across all 8 services while preserving all environment variables, ports, memory limits, and health dependencies
- [X] T014 [US1] Remove `docker/Dockerfile.service` and the `docker/` directory from the repository
- [X] T015 [US1] Launch the application stack using `docker compose -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.apps.yml up -d` and verify zero Dockerfile builds occur
- [X] T016 [US1] Verify health actuators across all running services (`curl http://localhost:8080/actuator/health`, `8082-8088/actuator/health`) return HTTP 200 with status `UP`

**Checkpoint**: Application stack runs 100% from Jib images with zero drift from previous runtime configuration.

---

## Phase 5: User Story 3 - Development & Deployment Documentation Update (Priority: P3)

**Goal**: Document the Jib build workflow and Docker Compose usage in the repository README.

**Independent Test**: Follow the updated setup steps in `README.md` and confirm commands match the actual workflow.

- [X] T017 [P] [US3] Update `README.md` to document the `./mvnw compile jib:dockerBuild -DskipTests` command, explain Jib integration, and remove references to `docker/Dockerfile.service`

---

## Phase 6: Polish & Cross-Cutting Validation

**Purpose**: Final test suite verification, container teardown, and workspace cleanliness.

- [X] T018 Run `./mvnw test` across all reactor modules to verify 100% unit and slice test pass rate
- [X] T019 Tear down test containers using `docker compose -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.apps.yml down` to leave the environment clean

---

## Dependencies & Execution Order

### Phase Dependencies
- **Setup (Phase 1)**: Can start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 completion (blocks Phase 3)
- **User Story 2 (Phase 3)**: Depends on Phase 2 completion (builds images needed by US1)
- **User Story 1 (Phase 4)**: Depends on Phase 3 completion (runs the images built in US2)
- **User Story 3 (Phase 5)**: Depends on Phase 4 completion (documents verified workflow)
- **Polish (Phase 6)**: Depends on Phase 4 & Phase 5 completion

### Parallel Opportunities
- In Phase 2: Tasks T003 through T010 can all be executed in parallel (different POM files)
- In Phase 5: Task T017 can be drafted in parallel once T013/T014 are finalized

---

## Parallel Example: Foundational Phase

```bash
# Launch edits for all 8 service POMs in parallel:
Task T003: gateway/pom.xml
Task T004: services/customer-service/pom.xml
Task T005: services/restaurant-service/pom.xml
Task T006: services/availability-service/pom.xml
Task T007: services/reservation-service/pom.xml
Task T008: services/waiting-list-service/pom.xml
Task T009: services/notification-service/pom.xml
Task T010: services/analytics-service/pom.xml
```

---

## Implementation Strategy

### Incremental Delivery (Recommended)
1. Configure root POM (Phase 1)
2. Configure all 8 service POMs (Phase 2)
3. Build all images into Docker daemon via Jib (Phase 3: US2)
4. Migrate `docker-compose.apps.yml` to images and remove Dockerfile (Phase 4: US1 - MVP!)
5. Validate running container health probes
6. Update documentation (Phase 5: US3)
7. Run clean test validation & compose teardown (Phase 6: Polish)
