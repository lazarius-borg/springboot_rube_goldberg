# Phase 0: Research & Technical Decisions

**Feature**: `018-jib-docker-containers`  
**Date**: 2026-09-18  

## Decision 1: Jib Multi-Module Reactor Configuration & Skipping Non-Runnable Modules

### Context
The repository is a multi-module Maven reactor consisting of:
- The parent POM (`springboot-rube-goldberg`) with `<packaging>pom</packaging>`
- Shared library module (`common/event-contracts`) with `<packaging>jar</packaging>` but no main class or runnable container entrypoint
- 8 runnable Spring Boot microservices (`gateway` + 7 domain services under `services/`)

### Decision
1. Define the base Jib configuration in root `pom.xml` under `<pluginManagement>` specifying base image `eclipse-temurin:26-jre-alpine` and target image `springboot-rube-goldberg/${project.artifactId}:${project.version}` with tag `<tag>latest</tag>`.
2. In root `pom.xml` `<properties>`, set `<jib.skip>true</jib.skip>`.
3. Declare `<plugin><groupId>com.google.cloud.tools</groupId><artifactId>jib-maven-plugin</artifactId></plugin>` in root `pom.xml` `<build><plugins>` so that invoking `compile jib:dockerBuild` at the reactor root traverses all submodules.
4. The root project and `common/event-contracts` automatically inherit `<jib.skip>true</jib.skip>` and are skipped cleanly during image builds.
5. In each of the 8 runnable service POMs, set `<jib.skip>false</jib.skip>` and define `<configuration><container><mainClass>...</mainClass></container></configuration>`.

### Alternatives Considered
- *Explicitly invoking Jib with `-pl` list on command line*: Rejected because developers and CI would need to maintain an 8-module list in CLI scripts.
- *Creating a custom shell script to iterate submodules*: Rejected because it bypasses Maven reactor parallel lifecycle capabilities.

---

## Decision 2: Java 26 Bytecode & ASM ClassReader Compatibility Workaround

### Context
The project runs on Java 26 (`<java.version>26</java.version>`). Running `jib:dockerBuild` without an explicit main class causes Jib 3.4.4's internal ASM library to scan compiled `.class` files to discover `@SpringBootApplication` or main methods. Because Java 26 emits bytecode with major version 70, ASM throws `Unsupported class file major version 70`.

### Decision
Explicitly configure `<container><mainClass>` in each of the 8 service POMs:
- `gateway`: `nl.invokedynamic.demo.gateway.GatewayApplication`
- `customer-service`: `nl.invokedynamic.demo.customer.CustomerServiceApplication`
- `restaurant-service`: `nl.invokedynamic.demo.restaurant.RestaurantServiceApplication`
- `availability-service`: `nl.invokedynamic.demo.availability.AvailabilityServiceApplication`
- `reservation-service`: `nl.invokedynamic.demo.reservation.ReservationServiceApplication`
- `waiting-list-service`: `nl.invokedynamic.demo.waitinglist.WaitingListServiceApplication`
- `notification-service`: `nl.invokedynamic.demo.notification.NotificationServiceApplication`
- `analytics-service`: `nl.invokedynamic.demo.analytics.AnalyticsServiceApplication`

Explicit main class definition completely bypasses Jib's ASM class scanning and successfully builds the container image directly to the Docker daemon.

### Alternatives Considered
- *Downgrading Java version*: Rejected; project constitution requires Java 21+ and current baseline is Java 26.
- *Overriding ASM dependency inside plugin declaration*: Tested; Jib shades its dependencies (`com.google.cloud.tools.jib...asm`), so overriding the Maven classpath does not replace Jib's shaded scanner. Explicit `mainClass` is the canonical, recommended Jib solution.

---

## Decision 3: Image Tagging & Docker Compose Integration

### Context
`docker-compose.apps.yml` currently builds images via:
```yaml
build:
  context: ..
  dockerfile: docker/Dockerfile.service
  args:
    JAR_FILE: ...
```
This forces redundant image builds, requires pre-built fat JARs, and does not leverage Jib's layer caching.

### Decision
1. Configure Jib to produce dual tags:
   - Primary: `springboot-rube-goldberg/${project.artifactId}:${project.version}`
   - Additional: `<tag>latest</tag>`
2. In `docker-compose.apps.yml`, replace the `build:` block on all 8 services with:
   ```yaml
   image: springboot-rube-goldberg/<service-name>:${IMAGE_TAG:-latest}
   ```
3. Remove `docker/Dockerfile.service` to enforce Jib as the single canonical image builder.
4. Keep all existing environment variables, ports, volume mounts, `mem_limit: 512m`, and `depends_on` blocks untouched.

### Alternatives Considered
- *Keeping both `build:` and `image:` in Compose*: Rejected because `docker compose up --build` would still trigger Dockerfile builds and fail if `Dockerfile.service` were removed or fat JARs were out-of-date.

---

## Decision 4: CLI Workflow & Documentation Updates

### Decision
- The standard one-step command to compile and build all container images locally is:
  ```bash
  ./mvnw compile jib:dockerBuild -DskipTests
  ```
- To start the platform using the Jib images:
  ```bash
  docker compose -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.apps.yml up -d
  ```
- Update root `README.md` to document this workflow and remove references to `docker/Dockerfile.service`.
