# Tasks: Local Remote Debugging for Containerized Services

**Feature**: `021-remote-debugging` | **Branch**: `021-remote-debugging` | **Spec**: [specs/021-remote-debugging/spec.md](spec.md) | **Plan**: [specs/021-remote-debugging/plan.md](plan.md)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Validate configuration baselines and structural prerequisites

- [X] T001 Review local Docker Compose and JDWP configuration schema in `infrastructure/docker-compose.apps.yml` against `specs/021-remote-debugging/contracts/debug-port-allocation.json`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Ensure environment readiness and clean baseline before applying changes

**⚠️ CRITICAL**: Must complete before any user story tasks can proceed

- [X] T002 Verify current baseline build and test compilation across all reactor modules using `./mvnw test-compile`

**Checkpoint**: Baseline verified - user story implementation can begin

---

## Phase 3: User Story 1 - Attach Remote Debugger to Running Service Container (Priority: P1) 🎯 MVP

**Goal**: Configure JDWP remote debugging with non-colliding 1:1 host-to-container port publishing across all 8 application microservices in local Docker Compose.

**Independent Test**: Validate Docker Compose configuration with `docker compose -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.apps.yml config`, start containers, and confirm JDWP ports `5080` and `5082`–`5088` are listening and open per `specs/021-remote-debugging/quickstart.md` Section 3.

### Implementation for User Story 1

- [X] T003 [P] [US1] Configure JDWP options `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5080` in `JAVA_TOOL_OPTIONS` and publish port `"5080:5080"` for `gateway` in `infrastructure/docker-compose.apps.yml`
- [X] T004 [P] [US1] Configure JDWP options `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5082` in `JAVA_TOOL_OPTIONS` and publish port `"5082:5082"` for `customer-service` in `infrastructure/docker-compose.apps.yml`
- [X] T005 [P] [US1] Configure JDWP options `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5083` in `JAVA_TOOL_OPTIONS` and publish port `"5083:5083"` for `restaurant-service` in `infrastructure/docker-compose.apps.yml`
- [X] T006 [P] [US1] Configure JDWP options `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5084` in `JAVA_TOOL_OPTIONS` and publish port `"5084:5084"` for `availability-service` in `infrastructure/docker-compose.apps.yml`
- [X] T007 [P] [US1] Configure JDWP options `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5085` in `JAVA_TOOL_OPTIONS` and publish port `"5085:5085"` for `reservation-service` in `infrastructure/docker-compose.apps.yml`
- [X] T008 [P] [US1] Configure JDWP options `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5086` in `JAVA_TOOL_OPTIONS` and publish port `"5086:5086"` for `waiting-list-service` in `infrastructure/docker-compose.apps.yml`
- [X] T009 [P] [US1] Configure JDWP options `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5087` in `JAVA_TOOL_OPTIONS` and publish port `"5087:5087"` for `analytics-service` in `infrastructure/docker-compose.apps.yml`
- [X] T010 [P] [US1] Configure JDWP options `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5088` in `JAVA_TOOL_OPTIONS` and publish port `"5088:5088"` for `notification-service` in `infrastructure/docker-compose.apps.yml`
- [X] T011 [US1] Validate Compose YAML syntax and port mapping configuration using `docker compose -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.apps.yml config`

**Checkpoint**: Remote debugging sockets and port publishing are fully configured across all 8 microservices (MVP deliverable ready).

---

## Phase 4: User Story 2 - Non-Blocking Container Operation & Unaffected Functionality (Priority: P2)

**Goal**: Guarantee that container startup is non-blocking (`suspend=n`), health probes pass without pause, and all existing business logic, Actuator metrics, and automated tests run cleanly.

**Independent Test**: Run `./mvnw clean test` to verify that all 10 reactor submodules pass unit and integration tests with zero regression.

### Implementation for User Story 2

- [X] T012 [US2] Audit all 8 service definitions in `infrastructure/docker-compose.apps.yml` to ensure `suspend=n` is uniformly applied and memory options `-Xms128m -Xmx384m` are preserved
- [X] T013 [US2] Execute full Maven reactor test suite `./mvnw clean test` to confirm 100% pass rate and zero degradation of service functionality

**Checkpoint**: Non-suspending container operation and zero test suite regression verified.

---

## Phase 5: User Story 3 - Transparent Developer Documentation in README (Priority: P3)

**Goal**: Provide clear, comprehensive documentation in the project root `README.md` covering service debug ports, IDE connection guides (IntelliJ IDEA, Eclipse, VS Code), and debugging best practices.

**Independent Test**: Follow the instructions in `README.md` to verify all port numbers, menu paths, and connection options match the actual Compose configuration.

### Implementation for User Story 3

- [X] T014 [US3] Add a dedicated "Remote Debugging" subsection under "Local Development" in `README.md` containing the complete 8-service port reference table
- [X] T015 [US3] Document step-by-step connection guides for IntelliJ IDEA, Visual Studio Code (`launch.json`), and Eclipse in `README.md`
- [X] T016 [US3] Add operational documentation in `README.md` detailing non-blocking behavior (`suspend=n`), simultaneous multi-service debugging, and debugger detach handling

**Checkpoint**: Developer documentation in `README.md` is complete and verified.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final end-to-end consistency and verification checks

- [X] T017 Execute validation checks outlined in `specs/021-remote-debugging/quickstart.md`
- [X] T018 Verify full cross-artifact consistency between `specs/021-remote-debugging/contracts/debug-port-allocation.json`, `infrastructure/docker-compose.apps.yml`, and `README.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: Can start immediately.
- **Phase 2 (Foundational)**: Depends on Phase 1. Blocks all user stories.
- **Phase 3 (User Story 1 - MVP)**: Depends on Phase 2 completion.
- **Phase 4 (User Story 2)**: Depends on Phase 3 (evaluates non-blocking behavior and test suite with Compose changes in place).
- **Phase 5 (User Story 3)**: Depends on Phase 3 (documents established port allocations). Can run in parallel with Phase 4.
- **Phase 6 (Polish)**: Depends on Phases 3, 4, and 5.

### User Story Dependencies

- **User Story 1 (P1)**: Independent of other stories. Foundational for US2 and US3.
- **User Story 2 (P2)**: Depends on US1 (validates US1 JDWP configuration does not impede execution).
- **User Story 3 (P3)**: Depends on US1 (documents US1 port mappings). Can be executed alongside US2.

### Parallel Opportunities

- In **Phase 3 (User Story 1)**: Tasks T003 through T010 modify independent service blocks in `infrastructure/docker-compose.apps.yml` and can be prepared or authored in parallel before T011 syntax validation.
- In **Phase 4 & 5**: T012–T013 (test execution) and T014–T016 (`README.md` documentation) can execute concurrently once Phase 3 is complete.

---

## Parallel Example: User Story 1

```bash
# Configure JDWP ports across independent services:
Task: "Configure JDWP options for gateway in infrastructure/docker-compose.apps.yml" (T003)
Task: "Configure JDWP options for customer-service in infrastructure/docker-compose.apps.yml" (T004)
Task: "Configure JDWP options for restaurant-service in infrastructure/docker-compose.apps.yml" (T005)
Task: "Configure JDWP options for availability-service in infrastructure/docker-compose.apps.yml" (T006)
Task: "Configure JDWP options for reservation-service in infrastructure/docker-compose.apps.yml" (T007)
Task: "Configure JDWP options for waiting-list-service in infrastructure/docker-compose.apps.yml" (T008)
Task: "Configure JDWP options for analytics-service in infrastructure/docker-compose.apps.yml" (T009)
Task: "Configure JDWP options for notification-service in infrastructure/docker-compose.apps.yml" (T010)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 (Setup) and Phase 2 (Foundational).
2. Complete Phase 3 (User Story 1: JDWP options & port exposures for all 8 services).
3. **STOP and VALIDATE**: Validate Compose configuration via `docker compose config` and check socket connectivity per `quickstart.md`.
4. Deploy / verify MVP.

### Incremental Delivery

1. **Increment 1 (MVP)**: JDWP enabled and ports published in `infrastructure/docker-compose.apps.yml` (US1).
2. **Increment 2**: Verify non-blocking operation (`suspend=n`) and run full Maven reactor test suite `./mvnw clean test` (US2).
3. **Increment 3**: Document debug ports and step-by-step IDE instructions in `README.md` (US3).
4. **Increment 4**: Cross-artifact verification and final polish (Phase 6).
