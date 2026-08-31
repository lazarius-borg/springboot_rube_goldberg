# Data Model & Configuration Architecture: Upgrade Spring Boot Version

## Overview
This architectural change upgrades the framework layer while preserving all existing PostgreSQL relational schemas, Flyway migrations, and Kafka event contract models.

## Impacted Components & Configuration Model

### 1. Root Reactor Dependency Model
- `pom.xml`:
  - `<spring-boot.version>`: Updated to `4.1.1`
  - `<spring-cloud.version>`: Aligned to matching release train
  - `<springdoc.version>`: Aligned to modern release
  - `<surefire.argLine>`: Retains `-Dnet.bytebuddy.experimental=true -XX:+EnableDynamicAgentLoading -Dspring.classformat.ignore=true` for preview/modern JDK execution.

### 2. Service Submodule Starter Bindings
- Submodules (`gateway`, `customer-service`, `restaurant-service`, `availability-service`, `reservation-service`, `waiting-list-service`, `notification-service`, `analytics-service`):
  - Inherit updated starter parent BOMs.
  - Zero schema or table modifications.

### 3. Test Configuration & Annotation Model
- Slices using mock beans transition to:
  ```java
  import org.springframework.test.context.bean.override.mockito.MockitoBean;
  ```
  replacing `org.springframework.boot.test.mock.mockito.MockBean`.
