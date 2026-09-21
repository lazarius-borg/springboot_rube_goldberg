# Feature Specification: Local Remote Debugging for Containerized Services

**Feature Branch**: `021-remote-debugging`

**Created**: 2026-09-19

**Status**: Draft

**Input**: User description: "The docker compose setup is intended for local development, and it would be useful for developers to be able to debug individual services. Configure the services so that the JVM that runs them is available for remote debugging, so that developer can attach the debugger if needed. This should be configured in the docker compose file, and documented in the README. Functionality must remain unafected."

## Clarifications

### Session 2026-09-19

- Q: What debug port allocation and container-to-host mapping convention should be standardized across all services? → A: Strict 1:1 port alignment: Gateway on `5080:5080` and services on `5082:5082` through `5088:5088` (matching HTTP `808x` -> `508x`).

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Attach Remote Debugger to Running Service Container (Priority: P1) 🎯 MVP

As a software engineer developing or troubleshooting features locally,  
I want to attach my IDE's Java debugger to any individual microservice running inside Docker Compose,  
So that I can set breakpoints, step through live code execution, and inspect thread stacks and variables during runtime operations without restarting services locally outside Docker.

**Why this priority**: Local debugging is essential for developer productivity when tracing complex interactions, state transitions, and edge cases across the distributed platform.

**Independent Test**: Start the platform with Docker Compose, configure a Remote JVM Debug configuration in an IDE (e.g., IntelliJ IDEA, Eclipse, or VS Code) targeting the service's designated debug port on `localhost`, trigger an HTTP request to that service, and verify that execution pauses at an active breakpoint and can be inspected and resumed.

**Acceptance Scenarios**:

1. **Given** services running in Docker Compose, **When** a developer attaches a standard JDWP debugger to a service's exposed debug port on `localhost`, **Then** the debugger attaches successfully without connection errors.
2. **Given** an attached debugger with a breakpoint set in a controller or service method, **When** a matching HTTP request or Kafka message arrives, **Then** thread execution pauses at the breakpoint, allowing variable inspection and step-by-step debugging.
3. **Given** a paused debugging session, **When** the developer resumes execution, **Then** the request completes normally and returns the expected response to the client.

---

### User Story 2 - Non-Blocking Container Operation & Unaffected Functionality (Priority: P2)

As a developer running the full platform or automated integration checks,  
I want the services to boot and execute continuously without blocking or waiting for a debugger connection,  
So that normal development workflows, health checks, background jobs, and inter-service transactions function without disruption when no debugger is attached.

**Why this priority**: Debugging must be strictly opt-in and non-intrusive. If containers wait for a debugger at startup (`suspend=y`), automated startup and standard test workflows will freeze.

**Independent Test**: Launch the platform via `docker compose up -d`, execute end-to-end API calls through the Gateway, and verify that all services achieve ready status, serve traffic, and pass automated health probes without attaching any debugger.

**Acceptance Scenarios**:

1. **Given** newly started service containers with remote debugging enabled, **When** no debugger is attached, **Then** services initialize immediately without suspension (`suspend=n`) and transition to `ACCEPTING_TRAFFIC`.
2. **Given** a developer detaches their debugger or closes the IDE during or after a debugging session, **Then** the containerized JVM continues running without crashing, throwing unhandled exceptions, or restarting.
3. **Given** all services configured for remote debugging, **When** running end-to-end smoke tests or synthetic traffic, **Then** latency, throughput, and functionality remain equivalent to standard container execution.

---

### User Story 3 - Transparent Developer Documentation in README (Priority: P3)

As a new or existing contributor to the repository,  
I want clear, concise documentation in `README.md` listing the debug ports for each service and instructions on how to attach common IDEs,  
So that I can connect to any service within seconds without guessing port numbers or searching through Docker Compose YAML files.

**Why this priority**: Observability and developer tools are only effective if they are readily discoverable and straightforward to use.

**Independent Test**: Review `README.md` for a dedicated "Remote Debugging" section, follow the documented port table and IDE setup instructions, and verify immediate successful connection to a running container.

**Acceptance Scenarios**:

1. **Given** the project `README.md`, **When** an engineer navigates to the local development documentation, **Then** they find a dedicated Remote Debugging section with a complete mapping table of services and host debug ports.
2. **Given** the documented steps, **When** an engineer configures a remote debug configuration in IntelliJ IDEA, Eclipse, or VS Code, **Then** the instructions provide exact connection parameters (transport mode, host, port, suspend behavior) that work on the first attempt.

---

### Edge Cases

- **Port Collisions**: Host debug ports must not collide with each other or with application HTTP ports (8080-8088), telemetry ports (4318, 9200, 5601), Keycloak (8081), Postgres (5432), Kafka (9092, 29092), Redis (6379), or Mailpit (1025, 8025). Each service must have a dedicated, deterministic host port.
- **Host Port Conflict & Recovery**: If a designated host debug port (e.g., `5085`) is occupied by an external host process, Docker Compose will fail on bind. The developer can resolve the conflict by identifying and terminating the conflicting host process or configuring a localized port override in their local Compose environment.
- **Container Lifecycle & Re-creation**: Injecting or modifying `JAVA_TOOL_OPTIONS` and publishing ports requires container re-creation via `docker compose up -d` rather than an in-place restart (`docker compose restart`), ensuring Docker updates network port forwarding rules and JVM environment variables upon container instantiation.
- **JVM Memory Headroom & Limits**: The JDWP agent introduces a small native memory footprint (~16MB–32MB). All services maintain explicit JVM heap bounds (`-Xms128m -Xmx384m`) within a 512MB container memory limit (`mem_limit: 512m`), ensuring that the 128MB non-heap headroom comfortably accommodates JVM Metaspace, thread stacks, and JDWP debugger buffers without triggering cgroup out-of-memory (OOM) kills.
- **Multiple Simultaneous Debuggers**: Developers must be able to attach independent debuggers to multiple distinct services simultaneously (e.g. debugging `gateway` and `restaurant-service` at the same time) without socket or port binding conflicts.
- **Host Interface Binding**: The in-container JVM must bind JDWP to all container network interfaces (`*:port` or `0.0.0.0:port`) rather than loopback (`127.0.0.1:port`) so Docker's port publishing can route host connections into the container.
- **Production Isolation**: Debug configurations must be restricted to the local development Compose overlay (`infrastructure/docker-compose.apps.yml` / environment overrides) and must not modify base production container images or production properties.
- **Debugger Disconnect During Breakpoint**: If a developer forcibly terminates their IDE debugger session while paused at a breakpoint, the JVM must gracefully release thread suspension and continue normal request processing.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The platform MUST configure the Java runtime environment in local Docker Compose for all application services (`gateway`, `customer-service`, `restaurant-service`, `availability-service`, `reservation-service`, `waiting-list-service`, `analytics-service`, `notification-service`) with JDWP remote debugging enabled.
- **FR-002**: Every service MUST configure JDWP options with non-suspending execution (`suspend=n`), ensuring the JVM boots immediately and services start handling traffic without waiting for a debugger connection.
- **FR-003**: Every service MUST publish an identical 1:1 host-to-container port mapping (e.g. `508x:508x`) bound to all container interfaces (`*:508x`) in `infrastructure/docker-compose.apps.yml`.
- **FR-004**: The assigned debug ports MUST follow a strict 1:1 matching convention with the HTTP service ports (`808x` -> `508x`) across all services:
  - `gateway`: Port `5080:5080` (matching HTTP 8080)
  - `customer-service`: Port `5082:5082` (matching HTTP 8082)
  - `restaurant-service`: Port `5083:5083` (matching HTTP 8083)
  - `availability-service`: Port `5084:5084` (matching HTTP 8084)
  - `reservation-service`: Port `5085:5085` (matching HTTP 8085)
  - `waiting-list-service`: Port `5086:5086` (matching HTTP 8086)
  - `analytics-service`: Port `5087:5087` (matching HTTP 8087)
  - `notification-service`: Port `5088:5088` (matching HTTP 8088)
- **FR-005**: The root `README.md` MUST include a clear and prominent "Remote Debugging" subsection within the Local Development section containing:
  - The complete service-to-debug-port reference table.
  - Step-by-step setup guides for IntelliJ IDEA, Eclipse, and Visual Studio Code.
  - Usage notes on setting breakpoints and resuming execution.
- **FR-006**: Enabling remote debugging MUST NOT alter or degrade existing service business logic, security rules, health probe responses, telemetry exports, or automated tests.
- **FR-007**: All Maven reactor builds (`./mvnw clean test`) and container builds MUST continue to pass with 100% success.

---

### Key Entities

- **JDWP Agent Options**: Java command-line options (`-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:<debug_port>`) injected via `JAVA_TOOL_OPTIONS` in Docker Compose to activate socket debugging.
- **Debug Port Binding**: Docker port forwarding mapping identical host-to-container ports (`508x:508x`) to the container's JDWP socket address.
- **Remote Debug Configuration**: IDE client connection profile specifying host `localhost` and the target service's designated debug port.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of application service containers (8 out of 8) accept incoming remote JDWP debugger connections on their designated host ports.
- **SC-002**: A developer can connect an IDE debugger to any service in under 30 seconds following the instructions documented in `README.md`.
- **SC-003**: Execution successfully pauses at breakpoints, allows stack/variable inspection, and resumes cleanly without dropping connections or crashing the container.
- **SC-004**: 100% of service containers start up and report healthy status within normal startup bounds without requiring debugger attachment.
- **SC-005**: 100% pass rate on full Maven test suite (`./mvnw test`) and zero errors during Docker Compose container startup.

---

## Assumptions

- Developers run Docker Compose locally using `infrastructure/docker-compose.yml` and `infrastructure/docker-compose.apps.yml`.
- The local host machine has standard Java development tools (IntelliJ IDEA, Eclipse, VS Code with Java Extension Pack, or CLI `jdb`).
- Standard JDWP socket transport (`dt_socket`) over TCP is supported by all target developer IDEs.
- Debug ports are exposed on `localhost` for local developer convenience and are not intended for public production deployment.
