# Research: Local Remote Debugging for Containerized Services

**Feature**: `021-remote-debugging`  
**Date**: 2026-09-19  
**Status**: Completed  

---

## 1. JDWP Configuration for Modern JVMs (Java 21+)

### Decision
Configure the JVM in each service container via the `JAVA_TOOL_OPTIONS` environment variable with:
```text
-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:<port>
```
where `<port>` matches the dedicated service debug port (`5080` for Gateway, `5082`–`5088` for the other services).

### Rationale
1. **Network Interface Binding (`address=*:<port>`)**: In Java 9 and newer (the project uses Java 21), omitting the hostname or setting `address=<port>` defaults to binding exclusively to the loopback interface (`127.0.0.1`). Inside a Docker container, binding to `127.0.0.1` prevents host-to-container port forwarding from reaching the debugger socket. Setting `address=*:<port>` (or `0.0.0.0:<port>`) binds the JDWP agent to all container network interfaces, permitting Docker bridge traffic to route seamlessly from `localhost:<port>` on the host into the container.
2. **Non-Suspending Mode (`suspend=n`)**: Setting `suspend=n` tells the JVM to start the application immediately without waiting for a debugger to connect. This is strictly required so that containers boot normally, pass Docker health checks, and serve HTTP/Kafka traffic without developer intervention.
3. **Environment Variable Delivery (`JAVA_TOOL_OPTIONS`)**: The project's `infrastructure/docker-compose.apps.yml` already uses `JAVA_TOOL_OPTIONS=-Xms128m -Xmx384m` for memory tuning. OpenJDK automatically detects and appends arguments in `JAVA_TOOL_OPTIONS` on startup without needing alterations to the `Dockerfile`, `ENTRYPOINT`, or base container images.

### Alternatives Considered
- **Setting `suspend=y`**: Rejected because containers would block during startup until a debugger attaches, breaking automated compose workflows, health probes, and integration tests.
- **Custom entrypoint wrapper script**: Rejected because `JAVA_TOOL_OPTIONS` is an OpenJDK standard that works natively across all containers without maintaining custom scripts or rebuilding images.
- **Single debug port with host forwarding (e.g. `5005` inside all containers)**: Evaluated during clarification and rejected in favor of 1:1 matching (`508x:508x`), ensuring container logs (`Listening for transport dt_socket at address: 508x`) match host IDE connection ports with zero translation confusion.

---

## 2. Port Allocation & Collision Prevention

### Decision
Standardize on a 1:1 mapping with the HTTP port numbers offset to `508x`:

| Service | Container Name | HTTP Port | Host Debug Port | In-Container JDWP Port |
|---|---|---|---|---|
| `gateway` | `rube-gateway` | `8080` | `5080` | `5080` |
| `customer-service` | `rube-customer-service` | `8082` | `5082` | `5082` |
| `restaurant-service` | `rube-restaurant-service` | `8083` | `5083` | `5083` |
| `availability-service` | `rube-availability-service` | `8084` | `5084` | `5084` |
| `reservation-service` | `rube-reservation-service` | `8085` | `5085` | `5085` |
| `waiting-list-service` | `rube-waiting-list-service` | `8086` | `5086` | `5086` |
| `analytics-service` | `rube-analytics-service` | `8087` | `5087` | `5087` |
| `notification-service` | `rube-notification-service` | `8088` | `5088` | `5088` |

### Rationale
- **Zero Collision with Existing Infrastructure**: The project uses ports `1025`/`8025` (Mailpit), `4317`/`4318` (OTel Collector), `5432` (PostgreSQL), `5601` (OpenSearch Dashboards), `6379` (Redis), `8081` (Keycloak), `9092`/`29092` (Kafka), and `9200` (OpenSearch). The range `5080`–`5088` is completely unoccupied and non-conflicting.
- **Intuitive Mental Model**: Developers map `808x` to `508x` effortlessly (e.g., reservation service is HTTP 8085 → Debug 5085).

### Alternatives Considered
- **Standard `5005` for Gateway, `5082`–`5088` for others**: Evaluated and discarded because the symmetry of `5080` matching HTTP `8080` simplifies documentation and mental mapping.

---

## 3. IDE Connection Patterns

### Decision
Document concise, copy-paste-ready remote debugging configurations for IntelliJ IDEA, Visual Studio Code, and Eclipse.

#### IntelliJ IDEA
- Run → Edit Configurations... → Add New Configuration (`+`) → **Remote JVM Debug**
- Transport: `Socket`
- Debugger mode: `Attach to remote JVM`
- Host: `localhost`
- Port: `<port>` (e.g. `5080` for Gateway, `5085` for Reservation Service)
- Use module classpath: Select the relevant module

#### Visual Studio Code
Add to `.vscode/launch.json`:
```json
{
  "type": "java",
  "name": "Debug Service (Attach)",
  "request": "attach",
  "hostName": "localhost",
  "port": 5085
}
```

#### Eclipse
- Run → Debug Configurations... → **Remote Java Application** → New
- Project: Select submodule (e.g., `reservation-service`)
- Connection Type: `Standard (Socket Attach)`
- Host: `localhost`
- Port: `<port>`

---

## 4. Verification & Testing Strategy

### Decision
Validate remote debugging across three levels:
1. **Socket Handshake Verification**: Automated TCP check sending the JDWP handshake string (`JDWP-Handshake\n`) to `localhost:<port>` to verify the agent responds with `JDWP-Handshake`.
2. **Container Health Verification**: Confirm all 8 containers achieve ready/healthy state without debugger connection (`suspend=n`).
3. **Reactor Regression Verification**: Full `./mvnw test` execution verifying 100% pass rate across all reactor modules.
