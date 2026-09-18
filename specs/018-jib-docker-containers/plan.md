# Implementation Plan: Rely on Jib Container Images in Docker Compose

**Branch**: `018-jib-docker-containers` | **Date**: 2026-09-18 | **Spec**: [spec.md](file:///Users/lazolazarev/projects/springboot_rube_goldberg/specs/018-jib-docker-containers/spec.md)

**Input**: Feature specification from `/specs/018-jib-docker-containers/spec.md`

## Summary

Migrate the multi-module Maven reactor containerization workflow to Google Jib (`jib-maven-plugin`). Configure parent POM and runnable submodule POMs with explicit `mainClass` declarations to bypass Java 26 ASM bytecode scanning limitations, skip non-runnable library modules cleanly, and produce dual tags (`${project.version}` and `latest`). Update `infrastructure/docker-compose.apps.yml` to directly consume these Jib-built images via `image: springboot-rube-goldberg/<service>:${IMAGE_TAG:-latest}` instead of building from `docker/Dockerfile.service`, and delete `docker/Dockerfile.service`.

## Technical Context

**Language/Version**: Java 26 (Eclipse Adoptium OpenJDK 26.0.2)  
**Primary Dependencies**: Maven 3.9+, Spring Boot 3.4.3, `com.google.cloud.tools:jib-maven-plugin:3.4.4`  
**Storage**: PostgreSQL 17, Redis 7 (via infrastructure Compose)  
**Testing**: JUnit 5, Mockito, Spring Boot Test (`./mvnw test`)  
**Target Platform**: Linux containers (`eclipse-temurin:26-jre-alpine` on Docker Engine / Docker Desktop aarch64 & x86_64)  
**Project Type**: Multi-module Maven reactor (8 Spring Boot microservices, 1 shared library, 1 parent POM)  
**Performance Goals**: Container image build time < 45 seconds incremental; sub-50ms layer caching  
**Constraints**: Zero runtime port, memory (`mem_limit: 512m`), or environment profile drift; mandatory use of `./mvnw`  
**Scale/Scope**: 8 application microservices in `docker-compose.apps.yml`  

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I: Strict Specification Adherence**: PASS. Implements all functional requirements (`FR-001` through `FR-009`) and user scenarios defined in `spec.md`.
- **Principle II: Maven Reactor & Microservices Architecture**: PASS. Preserves reactor boundaries, leverages parent `pom.xml` plugin management, skips non-runnable libraries, and configures submodules declaratively.
- **Principle III: Modern Spring Boot Feature Showcase**: PASS. Preserves Spring Boot application entrypoints and native Actuator health probes.
- **Principle IV: Agentic AI-Aided Development & Traceability**: PASS. Standardizes a single deterministic build command (`./mvnw compile jib:dockerBuild -DskipTests`) with clear documentation.
- **Principle V: Comprehensive Testing & Quality Gates**: PASS. All unit and slice tests remain 100% passing across the reactor.

## Project Structure

### Documentation (this feature)

```text
specs/018-jib-docker-containers/
├── spec.md              # Feature specification
├── plan.md              # Implementation plan (this file)
├── research.md          # Phase 0 research & technical decisions
├── data-model.md        # Phase 1 container image & compose mapping
├── quickstart.md        # Phase 1 end-to-end validation guide
├── contracts/           # Phase 1 CLI & compose schemas
│   └── compose-and-build-contracts.md
├── checklists/          # Quality checklists
│   └── requirements.md
└── tasks.md             # Phase 2 implementation task list (generated via /speckit-tasks)
```

### Source Code (repository root)

```text
pom.xml                                          # Root POM: jib plugin management, properties (jib.skip=true)
common/
└── event-contracts/
    └── pom.xml                                  # Shared library (inherits jib.skip=true)
gateway/
    └── pom.xml                                  # jib.skip=false, container.mainClass=GatewayApplication
services/
├── customer-service/pom.xml                     # jib.skip=false, CustomerServiceApplication
├── restaurant-service/pom.xml                   # jib.skip=false, RestaurantServiceApplication
├── availability-service/pom.xml                 # jib.skip=false, AvailabilityServiceApplication
├── reservation-service/pom.xml                  # jib.skip=false, ReservationServiceApplication
├── waiting-list-service/pom.xml                 # jib.skip=false, WaitingListServiceApplication
├── notification-service/pom.xml                 # jib.skip=false, NotificationServiceApplication
└── analytics-service/pom.xml                    # jib.skip=false, AnalyticsServiceApplication
infrastructure/
└── docker-compose.apps.yml                      # Replaces build: with image: tags for all 8 services
docker/
└── Dockerfile.service                           # Deleted (retired in favor of Jib)
README.md                                        # Updated build & run instructions
```

**Structure Decision**: Multi-module Maven reactor project where containerization configuration is managed via Maven POMs and runtime orchestration via Docker Compose.

## Complexity Tracking

> No constitutional violations or unwarranted complexity introduced. Jib eliminates custom Dockerfiles and multi-stage manual builds while improving layer caching and build speed.
