# Product Requirements Document — Spring Boot Rube Goldberg

**Status:** Draft / Initial Implementation  
**Project type:** Educational / portfolio application  
**Architecture:** Microservices  
**Primary audience:** Software developers  
**Primary purpose:** Demonstrate practical Spring Boot and Spring ecosystem capabilities through a coherent, usable distributed application.

---

## 1. Product Overview

**Spring Boot Rube Goldberg** is a small but realistic restaurant reservation platform designed specifically to showcase the capabilities of Spring Boot and selected Spring ecosystem technologies.

The application allows customers to discover restaurants, check availability, make and manage reservations, and join waiting lists when no suitable table is available. Restaurant managers can configure restaurants, opening hours, tables, table combinations, and reservation policies.

The application is intentionally designed as a **microservices-based distributed system**. Its business domain remains deliberately understandable while its architecture provides natural opportunities to demonstrate:

- REST APIs
- OpenAPI
- OAuth2/OIDC
- Spring Security
- relational persistence
- caching
- asynchronous messaging
- event-driven architecture
- transactional outbox
- scheduled processing
- distributed tracing
- metrics
- centralized logging
- resilience patterns
- health/readiness/liveness monitoring
- containerization
- Kubernetes deployment
- automated testing

The project should be a **real, usable application**, not a collection of disconnected Spring Boot feature demonstrations.

The "Rube Goldberg" aspect comes from intentionally using a distributed architecture to solve real application problems, rather than from adding technologies with no meaningful purpose.

---

# 2. Goals

## 2.1 Primary Goals

The project shall:

1. Demonstrate a broad but carefully selected set of Spring Boot capabilities.
2. Use a realistic microservices architecture.
3. Remain understandable to a developer unfamiliar with the project.
4. Be runnable locally with minimal effort.
5. Provide excellent REST APIs documented through OpenAPI.
6. Provide a minimal web UI for demonstrating end-to-end functionality.
7. Provide comprehensive automated testing.
8. Demonstrate real distributed-system concerns such as:
   - asynchronous processing
   - eventual consistency
   - retries
   - idempotency
   - service failures
   - concurrent operations
9. Provide comprehensive observability.
10. Be deployable to Kubernetes.
11. Be suitable for development primarily through coding agents.

---

# 3. Non-Goals

The following are explicitly outside the scope:

- Restaurant payment processing.
- Food ordering.
- Delivery.
- Menu management.
- Loyalty programs.
- Complex customer relationship management.
- Accounting.
- Full restaurant point-of-sale functionality.
- Native mobile applications.
- Complex restaurant floor-plan design.
- Production-grade multi-region deployment.
- Supporting every database technology supported by Spring.
- Demonstrating every Spring project merely for completeness.

The project should **not become a technology zoo**.

---

# 4. Technology Selection Principle

A technology should be included when it satisfies both of these criteria:

1. It demonstrates a useful capability of Spring Boot or its ecosystem.
2. It has a legitimate role in the application.

Technologies should **not** be introduced solely because Spring supports them.

The project should favor using one technology for multiple meaningful purposes rather than introducing multiple technologies that solve essentially the same problem.

For example, Redis should serve several justified purposes rather than introducing Redis, Hazelcast and another caching technology independently.

---

# 5. Target Technology Stack

## 5.1 Java

- Java **26**.
- Virtual threads are the preferred concurrency model.
- Conventional imperative programming should be preferred over reactive programming.
- Java Streams and Gatherers may be used where functional programming improves clarity.

Reactive programming should not be introduced merely because Spring supports it.

---

## 5.2 Spring Boot

The project shall use the **latest officially released Spring Boot version available when implementation begins**.

All services should use the same Spring Boot version.

Dependencies should preferably be managed through the Spring Boot dependency-management facilities rather than manually specifying versions.

---

## 5.3 Build

The project shall use:

- Maven.
- Maven Wrapper.
- A root Maven reactor build.
- Independently buildable Maven modules/services.

The repository root shall contain:

```text
mvnw
mvnw.cmd
.mvn/
pom.xml
```

The canonical full-project build shall be:

```bash
./mvnw clean verify
```

Individual services should also be independently buildable.

---

## 5.4 Containerization

Container images shall be built using the **Maven Jib plugin**.

Dockerfiles should not be required for the Java services.

Each deployable service should have an independently buildable container image.

---

# 6. High-Level Architecture

The application shall consist of seven business services:

1. Customer Service
2. Restaurant Service
3. Reservation Service
4. Availability Service
5. Waiting List Service
6. Notification Service
7. Analytics Service

Additionally, the system shall contain infrastructure components including:

- API Gateway
- Keycloak
- PostgreSQL
- Redis
- Kafka
- Mailpit
- OpenTelemetry Collector
- Prometheus
- Grafana
- OpenSearch
- OpenSearch Dashboards
- Alertmanager

---

# 7. Logical Architecture

```text
                                  ┌──────────────┐
                                  │   Keycloak   │
                                  └──────┬───────┘
                                         │
                                         │ OIDC
                                         ▼
┌──────────────┐                   ┌──────────────┐
│  Minimal UI  │──────────────────►│ API Gateway  │
└──────────────┘                   └──────┬───────┘
                                         │
                  ┌──────────────────────┼──────────────────────┐
                  │                      │                      │
                  ▼                      ▼                      ▼
          Customer Service      Restaurant Service      Reservation Service
                  │                      │                      │
                  ▼                      ▼                      ▼
             PostgreSQL             PostgreSQL             PostgreSQL
                                                                 │
                                                                 │
                                                                 ▼
                                                          Availability Service
                                                                 │
                                                                 ▼
                                                               Redis


                         Reservation / Business Events
                                     │
                                     ▼
                                ┌─────────┐
                                │  Kafka  │
                                └────┬────┘
                                     │
                   ┌─────────────────┼─────────────────┐
                   │                 │                 │
                   ▼                 ▼                 ▼
           Waiting List        Notification        Analytics
              Service             Service            Service
                   │                 │
                   ▼                 ▼
              PostgreSQL         PostgreSQL
                                     │
                                     ▼
                                  Mailpit
```

Observability is cross-cutting:

```text
Services
   │
   ▼
OpenTelemetry
   │
   ▼
OpenTelemetry Collector
   │
   ├──► Prometheus ──► Grafana
   │
   └──► OpenSearch ──► OpenSearch Dashboards

Prometheus ──► Alertmanager
```

---

# 8. Service Responsibilities

## 8.1 Customer Service

### Responsibility

Manage customer profiles and application-specific customer information.

Identity itself is managed by Keycloak.

The Customer Service is responsible for application-level information associated with an authenticated user.

### Example data

- Customer ID
- Identity-provider subject ID
- Name
- Email
- Telephone number
- Communication preferences
- Account creation date
- Account status

### Important principle

Keycloak is the identity provider.

The Customer Service must not become a second authentication system.

---

# 9. Restaurant Service

The Restaurant Service owns restaurant configuration.

### Responsibilities

- Restaurant creation and management.
- Restaurant information.
- Restaurant address.
- Restaurant timezone.
- Opening hours.
- Special opening-hours exceptions where supported.
- Reservation policies.
- Tables.
- Table capacity.
- Allowed table combinations.

### Restaurant configuration

Each restaurant shall have at least:

```text
name
address
timezone
defaultReservationDuration
minimumBookingAdvance
maximumBookingAdvance
cancellationWindow
openingHours
```

### Tables

Each table has:

```text
tableId
restaurantId
capacity
```

Tables may belong to one or more explicitly configured table combinations.

---

# 10. Restaurant Opening Hours

Restaurant managers shall be able to configure opening hours through the API and minimal UI.

A restaurant may have multiple opening periods per day.

Example:

```text
Monday:
  12:00–15:00
  18:00–23:00

Tuesday:
  12:00–15:00
  18:00–23:00
```

The design should support exceptional dates where appropriate, such as a restaurant being closed on an otherwise normal business day.

The system shall use the restaurant's configured **IANA timezone** when evaluating opening hours.

---

# 11. Restaurant Reservation Policies

Each restaurant configures:

### Minimum advance booking time

Example:

```text
30 minutes
```

A customer cannot create a reservation closer to the current time than this configured value.

### Maximum booking horizon

Example:

```text
60 days
```

Reservations beyond the configured horizon are rejected.

### Cancellation window

Example:

```text
2 hours
```

A customer may cancel only until this amount of time before the reservation.

### Reservation duration

Each restaurant has one default reservation duration.

Example:

```text
90 minutes
```

A reservation starting at 19:00 therefore occupies its allocated table until 20:30.

---

# 12. Reservation Service

The Reservation Service is the authoritative owner of reservations.

### Responsibilities

- Create reservations.
- Modify reservations.
- Cancel reservations.
- Track reservation status.
- Validate reservation operations.
- Allocate tables.
- Ensure reservation consistency.
- Publish reservation domain events.

### Reservation lifecycle

```text
                    ┌──────────────┐
                    │   CONFIRMED  │
                    └──────┬───────┘
                           │
              ┌────────────┼─────────────┐
              │            │             │
              ▼            ▼             ▼
          CANCELLED      ARRIVED       NO_SHOW
                           │
                           ▼
                       COMPLETED
```

A reservation is immediately confirmed when a valid table allocation is successfully created.

There is no restaurant-side manual confirmation process.

---

# 13. Table Allocation

When a reservation is requested, the system shall attempt to allocate a suitable table.

Allocation rules:

1. Find a single available table whose capacity is sufficient.
2. Prefer the smallest sufficient table.
3. If no single table is available, attempt an explicitly allowed table combination.
4. Do not arbitrarily combine tables that have not been configured as combinable.
5. If no valid allocation exists, the reservation request fails and the customer may join the waiting list.

Example:

```text
Requested party size: 4

Available:
  Table A — 2
  Table B — 4
  Table C — 6

Result:
  Table B
```

If Table B is unavailable:

```text
Table A — 2
Table D — 2

If A + D is an allowed combination:

Result:
  A + D
```

The allocation algorithm should remain deliberately simple and deterministic.

---

# 14. Concurrency and Reservation Consistency

The system must account for concurrent reservation attempts.

Example:

```text
Customer A ──► availability ──► Table 7 available
Customer B ──► availability ──► Table 7 available

A ──► reservation
B ──► reservation
```

The Availability Service is not the ultimate authority for reservation ownership.

The Reservation Service must enforce the actual consistency constraint.

The implementation should use appropriate database concurrency mechanisms, such as:

- optimistic locking
- transaction isolation
- appropriate database constraints
- retry where appropriate

The exact mechanism is an implementation decision, but double-booking must not be possible.

---

# 15. Availability Service

The Availability Service provides fast availability queries.

### Responsibility

Answer questions such as:

> What can I book at Restaurant X for four people on Friday at 19:00?

It should maintain a suitable read-oriented representation of restaurant configuration and reservation occupancy rather than requiring every availability request to synchronously query multiple services.

The service may consume:

- restaurant configuration events
- reservation events

to maintain its state.

---

# 16. Availability Caching

Redis shall be used for caching where beneficial.

Potential cached information includes:

- restaurant availability queries
- frequently requested restaurant information
- other short-lived read models

Cache entries must have appropriate expiration/invalidation behaviour.

Caching must never compromise reservation correctness.

The authoritative reservation decision remains the responsibility of the Reservation Service.

---

# 17. Waiting List Service

The Waiting List Service manages customers who cannot currently obtain a suitable reservation.

### Waiting-list entry

A waiting-list request contains:

```text
restaurant
date
earliestTime
latestTime
partySize
customer
createdAt
```

### Matching

Matching shall use:

- restaurant
- date
- requested time range
- party size
- table availability

Eligible requests are processed **FIFO**.

The system must not prioritize customers based on:

- customer history
- number of guests
- perceived customer value
- restaurant popularity
- other business-value assumptions

FIFO is deliberately chosen as the fairness policy.

---

# 18. Waiting-list Offers

When an appropriate opening becomes available:

1. The Waiting List Service identifies the oldest compatible waiting-list entry.
2. It creates a reservation offer.
3. The Notification Service is asked to notify the customer.
4. The offer remains valid for a configured period.
5. The customer may accept or reject it.
6. If it expires or is rejected, the next eligible customer may receive the offer.

Example:

```text
Table becomes available
          │
          ▼
Waiting List Service
          │
          ▼
Oldest compatible request
          │
          ▼
Reservation Offer
          │
          ▼
Notification
          │
          ▼
Customer
       ┌──┴──┐
       ▼     ▼
    Accept  Expire/Reject
       │       │
       ▼       ▼
 Reservation  Next customer
```

The waiting-list offer expiration must be handled reliably even if a service is temporarily unavailable.

---

# 19. Notification Service

The Notification Service handles communication with customers.

The initial supported channel is **email**.

### Notification types

At minimum:

- Reservation confirmed.
- Reservation modified.
- Reservation cancelled.
- Reservation reminder.
- Waiting-list offer.
- Waiting-list offer expired/rejected where appropriate.
- Post-reservation feedback request.

The service should be provider-independent internally so additional channels could be added later.

The first implementation only needs email.

---

# 20. Email Development Environment

The Docker Compose environment shall include **Mailpit**.

Emails generated by the application shall be delivered to Mailpit during local development.

This means a developer can:

```bash
docker compose up
```

and test the complete notification workflow without configuring external email credentials.

The Notification Service should still be structured around an email-provider abstraction so that a real SMTP provider could be configured later.

---

# 21. Reservation Reminders

The system shall send a reminder before a reservation.

The reminder interval should be configurable at the appropriate level.

Reminder processing shall be asynchronous/background work.

The system must ensure that restarting a service does not cause an excessive number of duplicate reminders.

---

# 22. Reservation Completion

Restaurant operators can update a reservation's status.

Supported transitions include:

```text
CONFIRMED → ARRIVED
ARRIVED   → COMPLETED

CONFIRMED → NO_SHOW

CONFIRMED → CANCELLED
```

Business rules shall prevent invalid transitions.

Corresponding domain events should be published where useful.

---

# 23. Analytics Service

The Analytics Service is deliberately **read-only from a business perspective**.

It consumes domain events from Kafka and builds simple analytical information.

Potential metrics include:

- reservations per restaurant
- reservations by time
- cancellations
- no-shows
- average party size
- waiting-list entries
- waiting-list conversion
- waiting-list offer expiration
- notification success/failure

The analytics service should not participate synchronously in reservation processing.

If Analytics Service is unavailable:

> Reservations must continue to work.

This demonstrates independent event consumers and eventual consistency.

---

# 24. Event-Driven Architecture

Kafka shall be used for asynchronous business events.

Events represent **business events**, not raw database changes.

Examples:

```text
CustomerRegistered

RestaurantCreated
RestaurantUpdated
RestaurantHoursChanged
RestaurantTableChanged

ReservationCreated
ReservationModified
ReservationCancelled
ReservationArrived
ReservationCompleted
ReservationNoShow

WaitingListEntryCreated
WaitingListOfferCreated
WaitingListOfferAccepted
WaitingListOfferExpired

NotificationRequested
NotificationSent
NotificationFailed
```

The exact event catalogue may evolve during implementation.

Events shall contain sufficient metadata for:

- event type
- event ID
- timestamp
- source service
- correlation/trace information
- aggregate/entity identifier
- event version

---

# 25. Transactional Outbox

Services that need to atomically persist business state and publish events shall use a **transactional outbox pattern**.

For example:

```text
BEGIN TRANSACTION

  save Reservation
  save ReservationCreated to outbox

COMMIT
```

A separate publisher then delivers the event to Kafka.

The implementation must avoid the failure scenario:

```text
Database commit succeeds
Kafka publication fails
```

leaving the system with a reservation that never generates its corresponding event.

The outbox publisher must support safe retries.

---

# 26. Idempotent Event Processing

All event consumers must be designed to tolerate duplicate delivery.

Processing the same event multiple times must not result in unintended duplicate business effects.

This is particularly important for:

- Notification Service
- Waiting List Service
- Analytics Service
- Availability Service

Where necessary, consumers shall maintain processed-event information.

---

# 27. API Gateway

A Spring Cloud Gateway-based API Gateway shall provide the external entry point.

Responsibilities include:

- routing
- common cross-cutting concerns
- authentication-related gateway functionality where appropriate
- rate limiting
- request correlation
- external API topology abstraction

The Gateway must not become a business-logic service.

---

# 28. Security

Keycloak shall be used as the local OIDC identity provider.

Spring Security shall be used by the services.

The system shall demonstrate:

- OAuth2/OIDC
- JWT validation
- authentication
- role-based authorization
- protected REST endpoints
- service-specific authorization

At minimum, the system shall distinguish:

```text
CUSTOMER
RESTAURANT_MANAGER
ADMIN
```

Customers may manage their own information and reservations.

Restaurant managers may manage restaurants for which they have appropriate permissions.

Administrators may perform system-level management operations.

---

# 29. REST API

REST is the primary application interface.

APIs shall follow a consistent versioning strategy, such as:

```text
/api/v1/...
```

The API should follow conventional REST principles and use appropriate HTTP methods and status codes.

APIs must support:

- pagination where appropriate
- filtering where appropriate
- sorting where appropriate
- validation
- consistent error responses

---

# 30. OpenAPI

Every REST service shall provide OpenAPI documentation.

The documentation shall describe:

- endpoints
- request models
- response models
- validation rules
- authentication requirements
- possible errors
- examples where useful

Swagger UI should be available for developers.

The OpenAPI interface is expected to be the **primary way developers interact with and explore the backend**.

---

# 31. REST Error Handling

REST APIs shall use **RFC 9457 Problem Details** for structured errors.

Errors should contain useful information such as:

- problem type
- title
- HTTP status
- detail
- instance
- trace/correlation identifier where appropriate

Example:

```json
{
  "type": "https://example.invalid/problems/restaurant-closed",
  "title": "Restaurant is closed",
  "status": 409,
  "detail": "The requested reservation time is outside the restaurant's opening hours.",
  "instance": "/api/v1/reservations",
  "traceId": "..."
}
```

The exact problem types and URI strategy are implementation details.

---

# 32. Minimal Web UI

A minimal web UI shall be provided.

The UI is **not** intended to be a second showcase project.

Its purpose is to demonstrate that the application is a real, usable system.

The UI should allow at least:

### Customer

- login
- restaurant browsing
- restaurant details
- availability search
- reservation creation
- viewing reservations
- modifying reservations
- cancelling reservations
- joining the waiting list
- viewing waiting-list offers

### Restaurant manager

- login
- restaurant configuration
- opening-hours management
- table management
- viewing reservations
- changing reservation status

The UI should remain deliberately simple.

OpenAPI/Swagger UI remains the primary developer-facing interface.

---

# 33. Time and Timezones

Timezone handling is a first-class requirement.

Every restaurant shall have an IANA timezone.

Examples:

```text
Europe/Amsterdam
Europe/London
America/New_York
```

Business operations such as:

- opening hours
- booking restrictions
- reservation duration
- cancellation windows
- reminders

must be evaluated using the restaurant's local timezone.

The system must not rely on the server's local timezone.

Persisted timestamps should use a timezone-safe representation appropriate for distributed systems.

---

# 34. Observability

Observability is a core feature of the project rather than an optional add-on.

Every service shall provide:

- structured logs
- metrics
- health information
- distributed traces

---

# 35. OpenTelemetry

The project shall use **OpenTelemetry**.

OpenTracing shall not be used.

Telemetry should include:

- HTTP requests
- inter-service calls
- database operations where appropriate
- Kafka publishing
- Kafka consumption
- significant application operations

Trace context must propagate across synchronous and asynchronous boundaries where technically appropriate.

A developer should be able to follow a reservation through multiple services.

---

# 36. OpenTelemetry Collector

An OpenTelemetry Collector shall act as the telemetry aggregation point.

Applications should preferably send telemetry to the collector rather than having every service be tightly coupled to individual observability backends.

This allows the backend topology to evolve independently of the applications.

---

# 37. Logging

Services shall emit structured logs.

Logs should contain useful fields such as:

- timestamp
- service
- log level
- logger
- message
- trace ID
- span ID
- correlation information where appropriate
- relevant business identifiers where safe

Sensitive information must not be logged.

Logs shall be collected centrally.

---

# 38. OpenSearch

OpenSearch shall be used for centralized log and trace exploration.

OpenSearch Dashboards shall provide the user interface.

The local Docker Compose environment shall make it possible to inspect application logs centrally.

---

# 39. Metrics

Spring Boot Actuator shall be enabled on all services.

Actuator shall provide:

- liveness information
- readiness information
- application metrics
- JVM metrics
- HTTP metrics
- relevant service-specific metrics

Prometheus shall scrape metrics from the services.

---

# 40. Service Health

### Liveness

> Is the process alive?

A temporary dependency failure should not necessarily cause liveness to fail.

### Readiness

> Can this service currently accept requests?

Readiness may depend on required infrastructure such as:

- database
- Kafka
- required external dependencies

The exact dependency policy should be carefully chosen to prevent cascading failures during infrastructure outages.

---

# 41. Application Metrics

In addition to standard Spring Boot metrics, business metrics should be exposed where useful.

Examples:

```text
reservations.created
reservations.cancelled
reservations.failed
reservations.no_show

waiting_list.entries
waiting_list.offers
waiting_list.expired_offers

notifications.sent
notifications.failed

kafka.consumer.lag
```

Metric naming should follow the conventions of the selected monitoring stack.

---

# 42. Grafana

Grafana shall be used to visualize Prometheus metrics.

The project should provide useful preconfigured dashboards for:

### Infrastructure

- CPU
- memory
- JVM
- container health

### HTTP

- request rate
- response time
- errors
- status codes

### Messaging

- Kafka throughput
- consumer lag

### Application

- reservations
- notifications
- waiting-list activity

---

# 43. Alerting

Prometheus and Alertmanager shall be used for alerting.

The system should detect at least:

- service unavailable
- service not ready
- elevated HTTP error rate
- elevated latency
- excessive resource utilization
- Kafka consumer lag
- database connectivity problems
- excessive notification failures
- waiting-list processing backlog

Alert thresholds should be configurable.

---

# 44. Alert Destinations

Alertmanager should support configurable receivers.

The development environment should demonstrate at least:

- email
- generic webhook

The application itself must not contain hardcoded integrations with Slack, Microsoft Teams, Discord, etc.

Additional channels can be configured through Alertmanager later.

---

# 45. Resilience

Distributed communication shall account for partial failure.

Where appropriate, the project should demonstrate:

- timeouts
- retries
- circuit breakers
- graceful degradation

Resilience mechanisms should only be applied where they make sense.

Retries must not accidentally create duplicate business operations.

For example, a retry of an HTTP request that creates a reservation must not result in two reservations.

Idempotency keys or equivalent mechanisms should be considered for externally initiated operations where necessary.

---

# 46. Caching and Rate Limiting

Redis shall be used for:

- selected caching use cases
- distributed rate limiting where appropriate
- other short-lived data where it provides clear value

Caching must not be used for authoritative reservation state.

The Gateway should provide sensible protection against excessive request rates.

---

# 47. Testing Strategy

Testing is a first-class project requirement.

All business functionality must be covered by automated tests.

Testing should be divided into:

1. Unit tests.
2. Integration tests.
3. API tests.
4. Event-driven integration tests.
5. End-to-end tests where appropriate.

---

# 48. Unit Tests

Business logic should be testable without requiring infrastructure.

Unit tests should cover:

- validation
- reservation rules
- table allocation
- opening-hours logic
- cancellation policy
- waiting-list FIFO matching
- waiting-list offer expiration
- reservation state transitions
- event handling
- error conditions

Tests should be fast and deterministic.

---

# 49. Integration Tests

**Testcontainers** shall be used for integration tests involving external infrastructure.

Potential containers include:

- PostgreSQL
- Kafka
- Redis
- Keycloak
- other infrastructure where required

Tests should exercise the actual technology rather than replacing it with mocks whenever integration behaviour is what is being tested.

---

# 50. Integration Test Principles

Integration tests should verify scenarios such as:

### Reservation

```text
create reservation
      ↓
database state
      ↓
outbox event
      ↓
Kafka
```

### Notification

```text
Kafka event
      ↓
Notification Service
      ↓
Mailpit
      ↓
email received
```

### Waiting list

```text
reservation cancellation
      ↓
Kafka
      ↓
Waiting List Service
      ↓
offer created
      ↓
notification event
```

### Authentication

```text
Keycloak
      ↓
JWT
      ↓
protected API
```

---

# 51. End-to-End Testing

A limited number of end-to-end tests should validate critical user journeys.

At minimum:

### Successful reservation

```text
login
→ find restaurant
→ find availability
→ create reservation
→ receive confirmation
```

### Cancellation

```text
create reservation
→ cancel reservation
→ reservation cancelled
```

### Waiting list

```text
no availability
→ join waiting list
→ reservation becomes available
→ offer generated
→ notification sent
→ customer accepts
```

The E2E suite should remain small enough to execute reliably.

---

# 52. Docker Compose

A complete Docker Compose environment shall be provided.

The goal is:

> A developer should be able to start the complete demonstration environment without manually starting individual services.

The environment should include:

- API Gateway
- all seven business services
- PostgreSQL instances/databases
- Redis
- Kafka
- Keycloak
- Mailpit
- OpenTelemetry Collector
- Prometheus
- Grafana
- OpenSearch
- OpenSearch Dashboards
- Alertmanager
- minimal UI

Where practical, configuration and initialization should happen automatically.

---

# 53. Developer Experience

The README shall explain a simple startup process such as:

```bash
./mvnw clean verify

docker compose up
```

The README shall provide URLs for:

- application UI
- Swagger UI
- Keycloak
- Mailpit
- Grafana
- OpenSearch Dashboards
- Prometheus
- Alertmanager

It shall also explain how to:

- obtain/login with demo users
- create a restaurant
- make a reservation
- trigger a waiting-list scenario
- inspect an email
- inspect metrics
- inspect logs
- inspect a distributed trace

---

# 54. Demonstration Scenario

The project should provide a documented "Rube Goldberg" walkthrough.

For example:

```text
1. Customer authenticates through Keycloak.
2. Customer searches restaurant availability.
3. API Gateway routes the request.
4. Availability Service checks its data/cache.
5. Customer creates reservation.
6. Reservation Service validates the request.
7. Table allocation occurs.
8. Reservation is persisted.
9. Outbox event is created.
10. Event is published to Kafka.
11. Notification Service consumes the event.
12. Email is generated.
13. Mailpit receives the email.
14. Analytics Service consumes the same event.
15. Metrics are updated.
16. OpenTelemetry records the distributed trace.
17. Logs are available in OpenSearch.
```

A developer should be able to follow this entire process using the provided tooling.

This is the project's central demonstration.

---

# 55. Kubernetes

The project shall include Kubernetes deployment artifacts.

The target is **standard Kubernetes**, not a vendor-specific distribution.

Application services must have Kubernetes deployment definitions including appropriate:

- Deployments
- Services
- ConfigMaps
- Secrets references
- readiness probes
- liveness probes
- resource requests/limits
- environment configuration

---

# 56. Kubernetes Health Probes

Kubernetes probes shall use Spring Boot Actuator health endpoints.

The deployment must distinguish:

```text
livenessProbe
readinessProbe
```

and configure them appropriately.

---

# 57. Kubernetes Infrastructure

The repository should provide deployment artifacts for the infrastructure required to demonstrate the system.

Infrastructure deployment should favor established Helm charts/operators or other standard mechanisms where appropriate rather than implementing complex infrastructure controllers.

The project is demonstrating **Spring Boot applications on Kubernetes**, not Kubernetes infrastructure engineering.

---

# 58. Configuration

Application configuration shall be externalized.

Environment-specific configuration must not be hardcoded into application binaries.

Configuration should support:

- local development
- Docker Compose
- Kubernetes

Secrets must not be committed to the repository.

Development-only credentials may be provided through clearly marked example configuration where safe.

---

# 59. Repository Structure

The repository should approximately follow:

```text
springboot-rube-goldberg/
│
├── .mvn/
│
├── mvnw
├── mvnw.cmd
├── pom.xml
├── README.md
├── LICENSE
│
├── gateway/
│   ├── pom.xml
│   └── src/
│
├── services/
│   ├── customer-service/
│   │   ├── pom.xml
│   │   └── src/
│   │
│   ├── restaurant-service/
│   │   ├── pom.xml
│   │   └── src/
│   │
│   ├── reservation-service/
│   │   ├── pom.xml
│   │   └── src/
│   │
│   ├── availability-service/
│   │   ├── pom.xml
│   │   └── src/
│   │
│   ├── waiting-list-service/
│   │   ├── pom.xml
│   │   └── src/
│   │
│   ├── notification-service/
│   │   ├── pom.xml
│   │   └── src/
│   │
│   └── analytics-service/
│       ├── pom.xml
│       └── src/
│
├── ui/
│
├── infrastructure/
│   ├── docker-compose.yml
│   ├── kafka/
│   ├── keycloak/
│   ├── postgres/
│   ├── redis/
│   ├── mailpit/
│   ├── opentelemetry/
│   ├── prometheus/
│   ├── grafana/
│   ├── opensearch/
│   └── alertmanager/
│
├── k8s/
│
└── docs/
```

The exact structure may be adjusted during implementation.

---

# 60. Maven Architecture

The root `pom.xml` shall act as a Maven reactor.

It should define modules for:

- Gateway
- all business services
- potentially shared non-business build modules where genuinely useful

Shared application libraries should be kept to a minimum.

In particular, services should **not share domain entities** or database models.

Sharing small technical libraries can be considered when it reduces genuine duplication without coupling services together.

---

# 61. Microservice Independence

Each business service must:

- own its data
- expose its own API
- have its own configuration
- be independently deployable
- be independently testable

Services must not directly access another service's database.

Database-per-service is a logical ownership rule even if several databases are physically hosted by the same PostgreSQL instance during local development.

---

# 62. API vs Event Communication

The project shall deliberately distinguish between:

### REST

Use REST when the caller requires an immediate response.

Examples:

```text
Get restaurant
Get availability
Create reservation
Modify reservation
Cancel reservation
Accept waiting-list offer
```

### Events

Use Kafka when other services need to react asynchronously.

Examples:

```text
ReservationCreated
ReservationCancelled
ReservationCompleted
WaitingListOfferCreated
NotificationRequested
```

A synchronous REST call should not be introduced simply because it is easier if the business interaction is naturally asynchronous.

---

# 63. Database Strategy

PostgreSQL shall be the primary persistent database technology.

The project does not need multiple relational database technologies.

Each service owns its schema/data.

Potential mapping:

```text
Customer Service       → PostgreSQL
Restaurant Service     → PostgreSQL
Reservation Service   → PostgreSQL
Availability Service   → PostgreSQL
Waiting List Service   → PostgreSQL
Notification Service   → PostgreSQL
Analytics Service      → PostgreSQL
```

Physical deployment may consolidate PostgreSQL instances for local convenience.

---

# 64. Technologies Explicitly Excluded

The following are intentionally not included in the initial implementation:

### Reactive programming / WebFlux

Excluded because Java 26 virtual threads provide a cleaner imperative concurrency model for this application.

### GraphQL

Interesting technology, but no business requirement justifies introducing it.

### Spring Batch

No sufficiently meaningful batch-processing requirement.

### Spring Integration

Kafka provides the required asynchronous messaging capabilities.

### Spring Modulith

The project is explicitly microservice-oriented.

### Hazelcast

Redis provides sufficient caching and distributed short-lived-state capabilities without adding another infrastructure technology.

### Payment providers

Outside the application's business scope.

### Multiple database technologies

No justification for demonstrating several database engines.

---

# 65. Performance and Scalability

The project is primarily a demonstration application, but services should nevertheless follow sound scalability practices.

Requirements include:

- virtual-thread based concurrency
- connection-pool configuration appropriate for the application
- efficient database access
- caching where appropriate
- asynchronous processing for long-running/non-critical operations
- pagination for potentially large collections
- reasonable resource limits

The system should avoid blocking operations that unnecessarily undermine the virtual-thread model.

---

# 66. Security Requirements

The project shall:

- never commit real credentials
- never log passwords or tokens
- validate JWT signatures
- validate token issuer/audience as appropriate
- enforce authorization server-side
- validate all externally supplied input
- avoid exposing internal infrastructure unnecessarily
- use secure defaults where practical

Development credentials must be clearly identified as development-only.

---

# 67. Documentation

The repository shall contain documentation sufficient for a developer to understand:

1. What the project does.
2. Why it exists.
3. Architecture.
4. Service responsibilities.
5. Technology choices.
6. How to build it.
7. How to run it.
8. How to run tests.
9. How to use the APIs.
10. How to inspect observability information.
11. How to deploy to Kubernetes.
12. Why particular technologies were deliberately excluded.

Architecture diagrams should be included where they improve understanding.

---

# 68. Coding-Agent Considerations

The project should be structured so that coding agents can work on individual services independently.

Each service should have:

- clear README/documentation where useful
- clear responsibility boundaries
- local tests
- integration tests
- independently understandable Maven configuration
- explicit API contracts
- explicit event contracts

Agents should not need to understand the entire system to implement a localized feature.

The root build must nevertheless provide an integration point that verifies the entire repository.

---

# 69. Code Quality

The project should demonstrate modern Java and Spring Boot development practices.

Code should favor:

- clear domain models
- immutability where practical
- constructor injection
- explicit boundaries
- meaningful names
- small cohesive components
- appropriate use of records
- Java Streams/Gatherers where they improve clarity
- straightforward imperative code where it is easier to understand

The project should avoid:

- excessive abstraction
- unnecessary design patterns
- generic "framework" layers
- premature optimization
- speculative extensibility

---

# 70. Acceptance Criteria

The project is considered complete when:

### Build

- [ ] `./mvnw clean verify` successfully builds the project.
- [ ] Maven Wrapper is present.
- [ ] All services participate in the root Maven reactor.
- [ ] Individual services can be built independently.
- [ ] Jib builds container images.

### Application

- [ ] Users can authenticate.
- [ ] Restaurants can be managed.
- [ ] Opening hours can be configured.
- [ ] Tables can be managed.
- [ ] Table combinations can be configured.
- [ ] Customers can search availability.
- [ ] Customers can create reservations.
- [ ] Reservations are immediately confirmed.
- [ ] Customers can modify reservations.
- [ ] Customers can cancel reservations.
- [ ] Restaurant-specific cancellation policies are enforced.
- [ ] Reservation duration is restaurant-specific.
- [ ] Minimum and maximum booking windows are enforced.
- [ ] Restaurant timezones are respected.
- [ ] Customers can join waiting lists.
- [ ] Waiting lists use FIFO matching.
- [ ] Waiting-list offers expire.
- [ ] Customers can accept waiting-list offers.
- [ ] Reservation lifecycle can be managed.
- [ ] Notifications are generated.
- [ ] Reservation reminders are generated.
- [ ] Analytics consume reservation events.

### APIs

- [ ] REST APIs are available.
- [ ] APIs are documented with OpenAPI.
- [ ] Swagger UI is available.
- [ ] API errors use Problem Details.
- [ ] Authentication/authorization is enforced.

### Messaging

- [ ] Kafka is used for asynchronous business events.
- [ ] Events use explicit contracts.
- [ ] Transactional outbox is implemented where required.
- [ ] Consumers are idempotent.
- [ ] Duplicate events do not produce duplicate business effects.

### Testing

- [ ] Business logic has unit tests.
- [ ] Integration tests use Testcontainers.
- [ ] Kafka interactions are integration-tested.
- [ ] Database interactions are integration-tested.
- [ ] Redis integration is tested.
- [ ] Security integration is tested.
- [ ] Critical end-to-end flows are covered.

### Observability

- [ ] Actuator is enabled.
- [ ] Liveness endpoints work.
- [ ] Readiness endpoints work.
- [ ] Prometheus can scrape metrics.
- [ ] Grafana dashboards are provided.
- [ ] OpenTelemetry traces are generated.
- [ ] Trace context propagates between services.
- [ ] Logs are centrally available.
- [ ] OpenSearch Dashboards is available.
- [ ] Alerts are configured.
- [ ] Alertmanager is available.

### Deployment

- [ ] Docker Compose starts the complete development environment.
- [ ] Mailpit is included.
- [ ] Keycloak is included.
- [ ] Kubernetes deployment artifacts exist.
- [ ] Kubernetes health probes are configured.
- [ ] Configuration can be supplied externally.
- [ ] Secrets are not committed.

---

# 71. Suggested Implementation Phases

The project should be implemented incrementally.

## Phase 1 — Foundation

- Root Maven project.
- Maven Wrapper.
- Java 26.
- Spring Boot baseline.
- Jib.
- Coding standards.
- Test infrastructure.
- Basic CI build.

## Phase 2 — Core Domain

- Restaurant Service.
- Customer Service.
- Reservation Service.
- PostgreSQL.
- Basic REST APIs.
- OpenAPI.
- Unit tests.
- Testcontainers.

## Phase 3 — Availability

- Availability Service.
- Table allocation.
- Table combinations.
- Restaurant policies.
- Timezone handling.
- Redis caching.
- Concurrency handling.

## Phase 4 — Security

- Keycloak.
- OAuth2/OIDC.
- Spring Security.
- Roles and authorization.
- API Gateway.

## Phase 5 — Events

- Kafka.
- Domain events.
- Transactional outbox.
- Idempotent consumers.

## Phase 6 — Waiting List

- Waiting-list service.
- FIFO matching.
- Reservation offers.
- Expiration.
- Acceptance/rejection.

## Phase 7 — Notifications

- Notification Service.
- Email.
- Mailpit.
- Reminders.
- Retry handling.

## Phase 8 — Analytics

- Analytics Service.
- Event consumption.
- Basic reporting metrics.

## Phase 9 — Observability

- OpenTelemetry.
- OTel Collector.
- Prometheus.
- Grafana.
- OpenSearch.
- OpenSearch Dashboards.
- Structured logging.
- Alertmanager.

## Phase 10 — Minimal UI

- Customer workflow.
- Restaurant manager workflow.
- Authentication.
- Basic visual polish.

## Phase 11 — Docker Compose

- Complete environment.
- Initialization.
- Seed data.
- Demo users.
- Documentation.

## Phase 12 — Kubernetes

- Application deployments.
- Infrastructure.
- Configuration.
- Secrets.
- Probes.
- Resource definitions.
- Documentation.

---

# 72. Definition of Success

The project succeeds if a developer can clone the repository and, without extensive explanation:

```text
./mvnw clean verify
docker compose up
```

then:

1. Open the application.
2. Authenticate.
3. Find a restaurant.
4. Check availability.
5. Make a reservation.
6. Receive an email.
7. Cancel the reservation.
8. Join the waiting list.
9. Trigger a newly available table.
10. Receive a waiting-list offer.
11. Accept the offer.
12. Open Grafana and inspect metrics.
13. Open OpenSearch Dashboards and inspect logs.
14. Follow the reservation's distributed trace.
15. Open Swagger UI and explore the APIs.
16. Inspect the architecture and understand why each service exists.

The final impression should be:

> **"This is a small restaurant reservation application—but look at how many real Spring Boot and distributed-system capabilities are demonstrated by making it work."**

rather than:

> **"This is a collection of Spring Boot feature demos that happens to have restaurants in it."**

That distinction is the central design principle of **Spring Boot Rube Goldberg**.
