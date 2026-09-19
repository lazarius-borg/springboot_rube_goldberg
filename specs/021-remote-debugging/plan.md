# Implementation Plan: Local Remote Debugging for Containerized Services

**Branch**: `021-remote-debugging` | **Date**: 2026-09-19 | **Spec**: [specs/021-remote-debugging/spec.md](spec.md)

**Input**: Feature specification from `specs/021-remote-debugging/spec.md`

---

## Summary

Configure the local Docker Compose runtime environment (`infrastructure/docker-compose.apps.yml`) across all 8 microservices to enable remote JDWP socket debugging (`dt_socket`) with immediate, non-blocking container startup (`suspend=n`). Establish an intuitive, collision-free 1:1 port alignment matching HTTP ports (`808x` -> `508x`, with Gateway on `5080` and services on `5082`–`5088`). Document the complete port allocation table and IDE connection procedures for IntelliJ IDEA, Visual Studio Code, and Eclipse in the root `README.md`.

---

## Technical Context

**Language/Version**: Java 26, Docker Compose v2, Java Debug Wire Protocol (JDWP)  
**Primary Dependencies**: OpenJDK 26 HotSpot JVM JDWP agent (`-agentlib:jdwp`), Spring Boot 4.1.x, Docker Compose v2  
**Storage**: N/A (Docker container runtime and environment configuration only)  
**Testing**: Maven reactor test suite (`./mvnw clean test`), Compose port binding verification, TCP socket handshake validation  
**Target Platform**: Local developer environments (macOS, Linux, Windows with Docker Compose)  
**Project Type**: Multi-module Spring Boot microservices reactor  
**Performance Goals**: Zero runtime latency or startup overhead when debugger is disconnected; immediate socket attachment (<1s)  
**Constraints**: 
- Non-suspending JVM startup (`suspend=n`) to prevent container boot freezing and health check timeouts
- Interface binding to `*:<port>` (all interfaces) to allow Docker bridge port forwarding
- 1:1 host-to-container port mapping
- Zero modification to production images or sub-module source logic  
- **JVM Memory Headroom & Bounds**: JDWP agent introduces native overhead (~16MB–32MB). With `-Xms128m -Xmx384m` and container `mem_limit: 512m`, the 128MB non-heap headroom safely supports Metaspace, thread stacks, and JDWP buffers without risk of cgroup OOM termination.
- **Host Port Conflict Recovery**: If host ports are busy, resolve by freeing the port or providing environment-based compose overrides.
- **Container Re-creation Lifecycle**: Environment variable and port mapping changes require container re-creation (`docker compose up -d`) rather than in-place restart (`docker compose restart`).
**Scale/Scope**: 8 application services (`gateway`, `customer-service`, `restaurant-service`, `availability-service`, `reservation-service`, `waiting-list-service`, `analytics-service`, `notification-service`)

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Requirement | Assessment | Status |
|---|---|---|---|
| **I. Strict Specification Adherence** | Must strictly conform to approved specification and clarifications | Direct implementation of FR-001 through FR-007 and Session 2026-09-19 clarification (1:1 port mapping, README docs). | **PASS** |
| **II. Maven Reactor & Microservices** | Preserve reactor structure and independent module boundaries | No module POM or packaging changes; changes isolated to Compose overlay and root documentation. | **PASS** |
| **III. Modern Spring Boot Showcase** | Idiomatic JVM conventions and Actuator visibility | Uses standard `JAVA_TOOL_OPTIONS` and maintains full Actuator health probe visibility without interference. | **PASS** |
| **IV. Agentic AI & Traceability** | Clear documentation, predictable commands, traceable changes | Fully documented port matrix, contracts schema, and quickstart verification guide. | **PASS** |
| **V. Comprehensive Testing & Quality Gates** | Full reactor build must pass cleanly without warnings | `./mvnw clean test` remains 100% green; manual and scriptable socket verification included. | **PASS** |

---

## Project Structure

### Documentation (this feature)

```text
specs/021-remote-debugging/
├── plan.md              # Implementation plan (/speckit-plan output)
├── research.md          # Phase 0 output: JDWP options, port conventions, IDE patterns
├── data-model.md        # Phase 1 output: configuration schema, port matrix, state flow
├── quickstart.md        # Phase 1 output: verification procedures and test scenarios
├── contracts/
│   └── debug-port-allocation.json # Phase 1 output: port matrix schema & contract
└── tasks.md             # Phase 2 output (/speckit-tasks output)
```

### Source Code (repository root)

```text
infrastructure/
└── docker-compose.apps.yml   # Append JDWP agent to JAVA_TOOL_OPTIONS and publish ports 5080, 5082-5088

README.md                     # Add Remote Debugging section under Local Development
```

**Structure Decision**:
Configuration changes are restricted strictly to `infrastructure/docker-compose.apps.yml` and developer documentation in `README.md`. No Java source files or Maven build scripts require modification, ensuring 100% isolation between developer debugging capabilities and production container logic.

---

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *None* | N/A | All architectural gates and constitutional constraints passed cleanly. |
