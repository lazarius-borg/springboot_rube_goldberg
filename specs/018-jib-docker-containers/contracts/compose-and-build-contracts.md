# Phase 1: Interface & CLI Contracts

**Feature**: `018-jib-docker-containers`  
**Date**: 2026-09-18  

## 1. Build CLI Contract

### Reactor Image Build Command
```bash
./mvnw compile jib:dockerBuild -DskipTests
```

- **Execution Scope**: Root reactor POM and all submodules.
- **Preconditions**: Local Docker daemon running; Java 26 SDK installed.
- **Behavior**:
  - `springboot-rube-goldberg` (parent): skipped (`jib.skip=true`).
  - `common/event-contracts`: skipped (`jib.skip=true`).
  - `gateway`: built → `springboot-rube-goldberg/gateway:1.0.0-SNAPSHOT` & `springboot-rube-goldberg/gateway:latest`.
  - `services/customer-service`: built → `springboot-rube-goldberg/customer-service:1.0.0-SNAPSHOT` & `springboot-rube-goldberg/customer-service:latest`.
  - `services/restaurant-service`: built → `springboot-rube-goldberg/restaurant-service:1.0.0-SNAPSHOT` & `springboot-rube-goldberg/restaurant-service:latest`.
  - `services/availability-service`: built → `springboot-rube-goldberg/availability-service:1.0.0-SNAPSHOT` & `springboot-rube-goldberg/availability-service:latest`.
  - `services/reservation-service`: built → `springboot-rube-goldberg/reservation-service:1.0.0-SNAPSHOT` & `springboot-rube-goldberg/reservation-service:latest`.
  - `services/waiting-list-service`: built → `springboot-rube-goldberg/waiting-list-service:1.0.0-SNAPSHOT` & `springboot-rube-goldberg/waiting-list-service:latest`.
  - `services/notification-service`: built → `springboot-rube-goldberg/notification-service:1.0.0-SNAPSHOT` & `springboot-rube-goldberg/notification-service:latest`.
  - `services/analytics-service`: built → `springboot-rube-goldberg/analytics-service:1.0.0-SNAPSHOT` & `springboot-rube-goldberg/analytics-service:latest`.

---

## 2. Docker Compose Invocation Contract

### Launch Command
```bash
docker compose -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.apps.yml up -d
```

- **Image Resolution**:
  - By default, references tag `:latest` (e.g. `springboot-rube-goldberg/gateway:latest`).
  - Overridable via `IMAGE_TAG` environment variable (e.g. `IMAGE_TAG=1.0.0-SNAPSHOT docker compose ...`).
- **No Dockerfile Build Context**:
  - No `build:` section in `docker-compose.apps.yml`.
  - No dependency on pre-existing fat `.jar` files in `target/` directories.
  - `docker/Dockerfile.service` is deleted.
