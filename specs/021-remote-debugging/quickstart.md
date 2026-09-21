# Quickstart & Verification Guide: Local Remote Debugging

**Feature**: `021-remote-debugging`  
**Date**: 2026-09-19  

This guide describes how to verify that containerized services in the local Docker Compose environment expose functional JDWP remote debugging sockets and operate without disruption.

---

## 1. Prerequisites

- Docker Engine & Docker Compose v2 installed and running
- OpenJDK 21 installed locally
- IDE with Java debugger support (IntelliJ IDEA, VS Code with Java Extension Pack, or Eclipse)
- Network utility such as `nc` (netcat) or `curl`

---

## 2. Startup Verification (Non-Blocking Boot)

### Step 2.1: Start the Infrastructure and Applications
Run the Compose stack with the updated debug configuration:
```bash
docker compose -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.apps.yml up -d
```

### Step 2.2: Verify Zero Startup Suspension
Confirm that all 8 service containers initialize immediately without waiting for a debugger:
```bash
docker compose -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.apps.yml ps
```
**Expected Outcome**: All services (`rube-gateway`, `rube-customer-service`, `rube-restaurant-service`, etc.) transition to `Up` and report healthy status within normal startup duration.

### Step 2.3: Check JDWP Ingress in Container Logs
Inspect the startup logs for any service (e.g., `reservation-service`):
```bash
docker compose -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.apps.yml logs reservation-service | head -n 10
```
**Expected Outcome**:
```text
Picked up JAVA_TOOL_OPTIONS: -Xms128m -Xmx384m -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5085
Listening for transport dt_socket at address: 5085
```

---

## 3. TCP Socket Handshake Verification

Verify that all 8 host debug ports are listening and respond to the standard JDWP handshake protocol:

```bash
for port in 5080 5082 5083 5084 5085 5086 5087 5088; do
  printf "Testing port %s: " "$port"
  if nc -z -w2 localhost "$port" 2>/dev/null; then
    echo "OPEN"
  else
    echo "CLOSED/UNREACHABLE"
  fi
done
```

**Expected Outcome**: All 8 ports (`5080`, `5082`, `5083`, `5084`, `5085`, `5086`, `5087`, `5088`) report `OPEN`.

---

## 4. IDE Debugger Attachment & Breakpoint Test

Verify active remote debugging against a target service (e.g., `reservation-service` on port `5085`):

### In IntelliJ IDEA:
1. Open **Run** → **Edit Configurations...**
2. Click **+** → **Remote JVM Debug**
3. Configure:
   - **Name**: `Debug Reservation Service`
   - **Host**: `localhost`
   - **Port**: `5085`
   - **Use module classpath**: `reservation-service`
4. Click **Debug**. The Debugger console should display:
   ```text
   Connected to the target VM, address: 'localhost:5085', transport: 'socket'
   ```
5. Set a breakpoint inside `ReservationController.java` (e.g., in `createReservation`).
6. Issue an HTTP request triggering the endpoint (e.g. via Postman, curl, or the automated test suite).
7. **Expected Outcome**: IntelliJ IDEA pauses thread execution at the breakpoint. Variables, call stack, and thread states can be inspected.
8. Click **Resume Program** (`F9`).
9. **Expected Outcome**: Request completes successfully and returns HTTP 200/201.

### Debugger Detach Test:
1. Disconnect the debugger from the IDE.
2. Send another HTTP request to the endpoint.
3. **Expected Outcome**: Request completes immediately without error; container JVM remains running normally.

---

## 5. Regression & Build Verification

Run the full project test suite locally to guarantee that Docker/JDWP changes do not affect Maven builds or slice/integration tests:

```bash
./mvnw clean test
```

**Expected Outcome**: All 10 reactor modules build and pass 100% of unit and integration tests with `BUILD SUCCESS`.
