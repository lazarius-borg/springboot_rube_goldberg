# Implementation Plan: Restaurant Reservation Platform (Rube Goldberg Showcase)

**Branch**: `001-restaurant-reservation-platform` | **Date**: 2026-08-31 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-restaurant-reservation-platform/spec.md`

## Summary

The Spring Boot Rube Goldberg platform is a distributed restaurant reservation system engineered as a multi-module Maven reactor project. It demonstrates modern Spring Boot capabilities, virtual threads imperative concurrency, transactional outbox messaging via Kafka, Redis read-model caching, Keycloak OIDC authentication, full OpenTelemetry observability, and comprehensive Testcontainers automated testing.

## Technical Context

**Language/Version**: Java 26 (Virtual Threads enabled via `spring.threads.virtual.enabled=true`)

**Primary Dependencies**: Spring Boot 3.4.x+, Spring Security OAuth2 Resource Server, Spring Cloud Gateway, Spring Data JPA, Spring Data Redis, Spring Kafka, Google Jib, Testcontainers, OpenTelemetry Java SDK / Micrometer Tracing.

**Storage**: PostgreSQL (Database-per-Service logical isolation), Redis (Availability read-model caching and Gateway rate-limiting).

**Testing**: JUnit 5, AssertJ, Spring Boot Test Slices (`@WebMvcTest`, `@DataJpaTest`), Testcontainers (`PostgreSQLContainer`, `KafkaContainer`, `GenericContainer`), REST Assured.

**Target Platform**: Multi-platform containerized services via Jib, deployable via Docker Compose locally and standard Kubernetes manifests.

**Project Type**: Distributed Microservices Application organized as a Maven Reactor.

**Performance Goals**: Sub-50ms latency for availability queries (via Redis cache); sub-200ms p95 for reservation creation transactions; instant asynchronous event dispatch via outbox publisher.

**Constraints**: Strict double-booking prevention under concurrent requests via database-level locking and exclusion constraints; guaranteed eventual consistency with zero event loss; RFC 9457 structured errors.

**Scale/Scope**: 7 decoupled business services + 1 API Gateway + minimal web UI + full observability pipeline (OTel Collector, Prometheus, Grafana, OpenSearch, Alertmanager).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] **I. Strict Specification Adherence (NON-NEGOTIABLE)**: Implementation architecture exactly mirrors the specifications and PRD.
- [x] **II. Maven Reactor & Microservices Architecture**: Root POM `groupId: nl.invokedynamic.demo`, `artifactId: springboot.rubegoldberg` with decoupled submodules.
- [x] **III. Modern Spring Boot Feature Showcase**: Demonstrates Actuators, Virtual Threads, Test Slices, declarative HTTP/Kafka clients, and modern configuration patterns.
- [x] **IV. Agentic AI-Aided Development & Traceability**: Clean documentation, explicit contracts ([openapi.yaml](./contracts/openapi.yaml), [events.yaml](./contracts/events.yaml)), and deterministic build steps.
- [x] **V. Comprehensive Testing & Quality Gates (NON-NEGOTIABLE)**: Unit, slice, and Testcontainers integration tests passing cleanly via `./mvnw clean verify`.

## Project Structure

### Documentation (this feature)

```text
specs/001-restaurant-reservation-platform/
├── plan.md              # This implementation plan
├── research.md          # Architectural decisions and technology choices
├── data-model.md        # Entity definitions, schemas, and state transitions
├── quickstart.md        # Runnable verification and Rube Goldberg walkthrough guide
├── contracts/           # API and Event definitions
│   ├── openapi.yaml     # REST API OpenAPI 3.0 specification
│   └── events.yaml      # Kafka Event schemas (AsyncAPI)
├── checklists/
│   └── requirements.md  # Spec quality validation checklist
└── tasks.md             # Implementation tasks (generated via /speckit-tasks)
```

### Source Code (repository root)

```text
springboot-rube-goldberg/
├── pom.xml                                   # Root Maven reactor POM
├── mvnw / mvnw.cmd / .mvn/                   # Maven wrapper
│
├── gateway/                                  # Spring Cloud Gateway service
│   ├── pom.xml
│   └── src/main/java/nl/invokedynamic/demo/gateway/
│
├── services/
│   ├── customer-service/                     # Customer profile management
│   │   ├── pom.xml
│   │   └── src/main/java/nl/invokedynamic/demo/customer/
│   ├── restaurant-service/                   # Restaurant configuration & hours
│   │   ├── pom.xml
│   │   └── src/main/java/nl/invokedynamic/demo/restaurant/
│   ├── reservation-service/                  # Transactional reservation & allocation
│   │   ├── pom.xml
│   │   └── src/main/java/nl/invokedynamic/demo/reservation/
│   ├── availability-service/                 # Fast availability query & Redis cache
│   │   ├── pom.xml
│   │   └── src/main/java/nl/invokedynamic/demo/availability/
│   ├── waiting-list-service/                 # FIFO waiting list & offer lifecycle
│   │   ├── pom.xml
│   │   └── src/main/java/nl/invokedynamic/demo/waitinglist/
│   ├── notification-service/                 # Email notifications & Mailpit adapter
│   │   ├── pom.xml
│   │   └── src/main/java/nl/invokedynamic/demo/notification/
│   └── analytics-service/                    # Read-only event metrics consumer
│       ├── pom.xml
│       └── src/main/java/nl/invokedynamic/demo/analytics/
│
├── ui/                                       # Minimal web portal (HTML/JS/Bootstrap)
│
├── infrastructure/                           # Local docker-compose & configs
│   ├── docker-compose.yml
│   ├── keycloak/
│   ├── opentelemetry/
│   ├── prometheus/
│   ├── grafana/
│   ├── opensearch/
│   └── alertmanager/
│
└── k8s/                                      # Standard Kubernetes manifests
```

**Structure Decision**: Multi-module Maven reactor project separating business services into discrete submodules under `services/`, with central routing via `gateway/`, local environment provisioning under `infrastructure/`, and Kubernetes deployment manifests under `k8s/`.

## Complexity Tracking

> **No violations present; architecture strictly conforms to Constitution and PRD requirements.**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| None | N/A | N/A |
