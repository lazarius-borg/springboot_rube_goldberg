# Phase 1: Quickstart Validation Guide

**Feature**: `018-jib-docker-containers`  
**Date**: 2026-09-18  

## Scenario: End-to-End Jib Build and Compose Verification

### 1. Prerequisites
- Docker daemon running locally (`docker info`).
- JDK 26 installed and available (`JAVA_HOME` configured).
- Maven wrapper `./mvnw` present in project root.

---

### 2. Build Container Images via Jib
Execute the single reactor command to compile and containerize all microservices:

```bash
./mvnw compile jib:dockerBuild -DskipTests
```

**Expected Outcome**:
- Root module and `common/event-contracts` report Jib skipped.
- All 8 application microservices build successfully.
- Command exits with `BUILD SUCCESS`.

---

### 3. Verify Images in Local Docker Daemon
Confirm that both versioned (`1.0.0-SNAPSHOT`) and `latest` tags exist in the local Docker daemon:

```bash
docker images | grep springboot-rube-goldberg
```

**Expected Output**:
Rows for:
- `springboot-rube-goldberg/gateway` (`latest`, `1.0.0-SNAPSHOT`)
- `springboot-rube-goldberg/customer-service` (`latest`, `1.0.0-SNAPSHOT`)
- `springboot-rube-goldberg/restaurant-service` (`latest`, `1.0.0-SNAPSHOT`)
- `springboot-rube-goldberg/availability-service` (`latest`, `1.0.0-SNAPSHOT`)
- `springboot-rube-goldberg/reservation-service` (`latest`, `1.0.0-SNAPSHOT`)
- `springboot-rube-goldberg/waiting-list-service` (`latest`, `1.0.0-SNAPSHOT`)
- `springboot-rube-goldberg/notification-service` (`latest`, `1.0.0-SNAPSHOT`)
- `springboot-rube-goldberg/analytics-service` (`latest`, `1.0.0-SNAPSHOT`)

---

### 4. Start Platform via Docker Compose
Start infrastructure and application services:

```bash
docker compose -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.apps.yml up -d
```

**Expected Outcome**:
- Compose starts containers without building any Dockerfiles.
- `docker ps` shows all containers running.

---

### 5. Validate Health Actuators
Test health endpoints across the stack:

```bash
curl -f http://localhost:8080/actuator/health
curl -f http://localhost:8082/actuator/health
curl -f http://localhost:8083/actuator/health
curl -f http://localhost:8084/actuator/health
curl -f http://localhost:8085/actuator/health
curl -f http://localhost:8086/actuator/health
curl -f http://localhost:8087/actuator/health
curl -f http://localhost:8088/actuator/health
```

**Expected Outcome**:
All endpoints return `{"status":"UP",...}` with HTTP 200.

---

### 6. Teardown
```bash
docker compose -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.apps.yml down
```
