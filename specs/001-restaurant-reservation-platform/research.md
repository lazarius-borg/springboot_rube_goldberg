# Technical Research & Architecture Decisions

## Core Architecture & Ecosystem Decisions

### 1. Language & Runtime
- **Decision**: Java 26 with Virtual Threads enabled (`spring.threads.virtual.enabled=true`).
- **Rationale**: Virtual threads provide high-throughput imperative concurrency without reactive complexity (`Mono`/`Flux`), eliminating callback hell and reactive trace discontinuity while supporting high concurrent request loads.
- **Alternatives Considered**: 
  - Reactive / WebFlux: Rejected per Constitution & PRD (imperative code with virtual threads is cleaner, more debuggable, and idiomatic).
  - Java 21 LTS: While standard, Java 26 allows utilizing the latest language features and virtual thread optimizations per the PRD specification.

### 2. Framework & Dependency Management
- **Decision**: Spring Boot 3.4.x+ / 3.x baseline with root Maven reactor dependency management (`spring-boot-dependencies` BOM).
- **Rationale**: Provides automatic dependency alignment across all microservices without version mismatch.
- **Alternatives Considered**:
  - Standalone service POMs: Rejected due to maintenance overhead and drift across modules.

### 3. Build & Containerization
- **Decision**: Maven multi-module reactor with Maven Wrapper (`./mvnw`) and Google Jib Maven Plugin (`jib-maven-plugin`).
- **Rationale**: Jib builds daemonless, reproducible, multi-layer OCI container images directly from Maven without requiring Docker daemon during builds or maintaining Dockerfiles for each Java microservice.
- **Alternatives Considered**:
  - Spring Boot Buildpacks (`spring-boot:build-image`): Requires active Docker daemon and longer build cycles.
  - Dockerfiles per service: Rejected per PRD (Jib provides faster, standardized image creation).

### 4. Service Boundaries & Decomposition
- **Decision**: 7 Business Services + 1 API Gateway:
  1. `gateway`: Spring Cloud Gateway (routing, rate limiting, token relay, correlation ID injection).
  2. `customer-service`: Customer profile & preferences management (Keycloak subject mapping).
  3. `restaurant-service`: Restaurant configuration, opening hours, tables, table combinations, booking policies.
  4. `reservation-service`: Authoritative reservation lifecycle, optimistic locking, deterministic table allocation.
  5. `availability-service`: High-speed availability queries, read model projections, Redis caching.
  6. `waiting-list-service`: FIFO waiting list management, cancellation event matching, time-limited offer state machine.
  7. `notification-service`: Email notifications via Mailpit, template rendering, retry handling, reminder scheduling.
  8. `analytics-service`: Read-only event consumption, metric projections, reporting endpoints.
- **Rationale**: Directly aligns with PRD specification, demonstrating clean single-responsibility boundaries, asynchronous event-driven integration, and eventual consistency.
- **Alternatives Considered**:
  - Monolithic architecture: Rejected; the goal is explicitly a microservices showcase.
  - Fewer coarser services: Merging availability or waiting list into reservation would undermine the distributed event showcase.

### 5. Persistence & Data Isolation
- **Decision**: PostgreSQL with Database-per-Service logical isolation. Schema migrations managed via Flyway or Liquibase in each submodule.
- **Rationale**: Enforces microservice autonomy; services cannot query other services' database tables directly.
- **Alternatives Considered**:
  - Shared database schemas: Violates microservice independence and data encapsulation.
  - Polyglot NoSQL databases: Unnecessary operational complexity; PostgreSQL handles relational constraints and JSON payloads cleanly.

### 6. Asynchronous Messaging & Transactional Outbox
- **Decision**: Apache Kafka with JSON/CloudEvents structured payloads, coupled with a Transactional Outbox pattern implemented in PostgreSQL.
- **Rationale**: Guarantees atomic database updates and event publication, completely eliminating dual-write failure windows where database commit succeeds but Kafka publication fails.
- **Alternatives Considered**:
  - Direct synchronous REST calls across all services: Introduces temporal coupling and cascading service failures.
  - Two-phase commit (XA / 2PC): High latency, coordinator failure bottleneck, not supported by Kafka.

### 7. Caching & Rate Limiting
- **Decision**: Redis (via `spring-boot-starter-data-redis` and Jedis/Lettuce).
- **Rationale**: Used for caching non-authoritative read models in Availability Service and distributed rate-limiting counters in the API Gateway.
- **Alternatives Considered**:
  - Hazelcast / Infinispan: Adding another cache engine adds unnecessary complexity per PRD technology selection principle.

### 8. Authentication & Authorization
- **Decision**: Keycloak OIDC Identity Provider with Spring Security OAuth2 Resource Server (`spring-boot-starter-oauth2-resource-server`) and JWT validation across services.
- **Rationale**: Standards-compliant token-based security separating identity management from application business profiles.
- **Alternatives Considered**:
  - Custom JWT generation / authentication filter: High security risk, reinvents standard security infrastructure.

### 9. Observability & Telemetry
- **Decision**: OpenTelemetry (OTel Java agent / Micrometer Tracing with OTLP exporter) -> OpenTelemetry Collector -> Prometheus & OpenSearch -> Grafana & OpenSearch Dashboards.
- **Rationale**: Provides unified distributed tracing, log aggregation with trace-context injection (`traceId`, `spanId`), and metrics scraping without vendor lock-in.
- **Alternatives Considered**:
  - Zipkin / OpenTracing: Outdated; OpenTelemetry is the industry standard.
  - Direct scraping without OTel Collector: Couples application configuration to specific storage backends.

### 10. Automated Testing Strategy
- **Decision**: JUnit 5, AssertJ, Spring Boot Test Slices (`@WebMvcTest`, `@DataJpaTest`), Testcontainers (`PostgreSQLContainer`, `KafkaContainer`, `GenericContainer` for Redis/Mailpit), and REST Assured for end-to-end API verification.
- **Rationale**: Real integration testing against containerized dependencies ensures zero-drift between test environment and production infrastructure.
