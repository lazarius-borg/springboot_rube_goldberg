# 🍽️ Spring Boot Rube Goldberg Restaurant Reservation Platform

> A production-grade, event-driven microservices showcase built with **Java 26**, **Spring Boot 4.1.1**, **Spring Cloud 2024**, **Apache Kafka**, **Redis**, **PostgreSQL 17**, **Keycloak 26**, and **OpenTelemetry**.

[![Java 26](https://img.shields.io/badge/Java-26-orange.svg)](https://openjdk.org/)
[![Spring Boot 4.1.1](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring Cloud 2024.0.0](https://img.shields.io/badge/Spring%20Cloud-2024.0.0-blue.svg)](https://spring.io/projects/spring-cloud)
[![Apache Kafka](https://img.shields.io/badge/Kafka-3.9-black.svg)](https://kafka.apache.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-blue.svg)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-red.svg)](https://redis.io/)
[![Keycloak](https://img.shields.io/badge/Keycloak-26.1-purple.svg)](https://www.keycloak.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

---

## 📖 Executive Summary & Domain Problem

The **Spring Boot Rube Goldberg Platform** is a distributed restaurant reservation system designed to solve high-concurrency dining logistics while showcasing modern, idiomatic Spring Boot and distributed systems engineering patterns.

### The Real-World Dilemma
1. **Extreme Concurrency & Double-Booking Risk**: Simultaneous booking requests for peak time slots must guarantee deterministic, atomic table allocation without race conditions.
2. **Dynamic Combinable Physical Inventory**: Tables can serve individuals or combine physically (e.g., Table 1 + Table 2) to accommodate larger parties.
3. **Sub-50ms Discovery vs. Authoritative Consistency**: Search operations must be blazingly fast (via Redis read projections) while booking mutations must be strictly consistent and ACID-compliant.
4. **Fair Demand Management**: When cancellations occur, freed slots must not trigger an unfair public "race condition"; instead, they must be offered fairly to the oldest matching candidate on a **FIFO Waiting List** with time-limited expirations.
5. **Decoupled Reliability**: Notifications and operational analytics must never degrade core reservation transaction latency or reliability.

### Why the "Rube Goldberg" Metaphor?
In engineering, a *Rube Goldberg machine* executes a complex chain reaction triggered by a single initial movement. In this platform, **a single customer event** (such as confirming or cancelling a reservation) triggers a choreographed, decoupled cascade across the ecosystem:

```mermaid
flowchart TD
    Customer([Customer / API Gateway]) -->|1. Book Table| ResSvc[Reservation Service]
    ResSvc -->|2. ACID Transaction| ResDB[(PostgreSQL + Outbox)]
    ResDB -->|3. Poller Dispatch| Kafka{{Apache Kafka: reservation.events}}
    
    Kafka -->|4a. Invalidate Slot Cache| AvailSvc[Availability Service]
    AvailSvc -->|Evict / Update| RedisCache[(Redis Read Projections)]
    
    Kafka -->|4b. Trigger Fair Match| WaitSvc[Waiting List Service]
    WaitSvc -->|Create 15-min Offer| WaitDB[(Waiting List DB + Outbox)]
    
    Kafka -->|4c. Render Template| NotifSvc[Notification Service]
    NotifSvc -->|Deliver Email| Mailpit[Mailpit SMTP Server]
    
    Kafka -->|4d. Incremental Aggregation| AnalyticsSvc[Analytics Service]
    AnalyticsSvc -->|Real-Time KPI| AnalyticsDB[(Analytics DB)]
```

---

## 🏛️ Project Architecture & Structure

The repository is organized as a standard Maven Reactor multi-module project under `groupId: nl.invokedynamic.demo`, `artifactId: springboot.rubegoldberg`:

```
springboot_rube_goldberg/
├── common/
│   └── event-contracts/        # Shared domain event records (ReservationCreatedEvent, etc.)
├── gateway/                    # Spring Cloud Gateway (Port 8080) with Rate Limiting & Web UI
├── services/
│   ├── customer-service/       # Customer Profiles & Keycloak identity mapping (Port 8082)
│   ├── restaurant-service/     # Establishments, opening hours & table combinations (Port 8083)
│   ├── availability-service/   # CQRS read projections & Redis caching (Port 8084)
│   ├── reservation-service/    # Authoritative table allocator & transactional outbox (Port 8085)
│   ├── waiting-list-service/   # Fair FIFO waitlist & cascading offer matchmaker (Port 8086)
│   ├── analytics-service/      # Asynchronous KPI & conversion rate metrics (Port 8087)
│   └── notification-service/   # Mailpit email notifications & 24h reminder scheduler (Port 8088)
├── ui/                         # Lightweight browser portals (Customer & Manager)
├── infrastructure/             # Docker Compose, Keycloak, Prometheus, Grafana, OpenSearch configs
└── k8s/                        # Production Kubernetes manifests and Kustomize overlays
```

---

## 🔌 Microservices Matrix & Port Reference

Every microservice exposes **Micrometer Metrics** and **Spring Boot Actuator Probes**, and domain services with REST APIs expose interactive **OpenAPI / Swagger UI**:

| Service | Port | Database | Swagger UI URL | Health Endpoint |
| :--- | :--- | :--- | :--- | :--- |
| **API Gateway** | `8080` | Redis (Caching) | [Gateway UI](http://localhost:8080/ui/customer/index.html) | `http://localhost:8080/actuator/health` |
| **Customer Service** | `8082` | `customer_db` | [Customer Swagger UI](http://localhost:8082/swagger-ui.html) | `http://localhost:8082/actuator/health` |
| **Restaurant Service** | `8083` | `restaurant_db` | [Restaurant Swagger UI](http://localhost:8083/swagger-ui.html) | `http://localhost:8083/actuator/health` |
| **Availability Service** | `8084` | `availability_db` | [Availability Swagger UI](http://localhost:8084/swagger-ui.html) | `http://localhost:8084/actuator/health` |
| **Reservation Service** | `8085` | `reservation_db` | [Reservation Swagger UI](http://localhost:8085/swagger-ui.html) | `http://localhost:8085/actuator/health` |
| **Waiting List Service**| `8086` | `waiting_list_db` | [Waiting List Swagger UI](http://localhost:8086/swagger-ui.html) | `http://localhost:8086/actuator/health` |
| **Analytics Service** | `8087` | `analytics_db` | [Analytics Swagger UI](http://localhost:8087/swagger-ui.html) | `http://localhost:8087/actuator/health` |
| **Notification Service**| `8088` | `notification_db` | _N/A (Event consumer — no REST APIs)_ | `http://localhost:8088/actuator/health` |

### Supporting Infrastructure Port Reference:
- **Keycloak OIDC**: `http://localhost:8081` (Admin: `admin` / `admin`)
- **Mailpit Web UI**: `http://localhost:8025` (SMTP: `localhost:1025`)
- **Grafana Dashboards**: `http://localhost:3000` (Admin: `admin` / `admin`)
- **Prometheus UI**: `http://localhost:9090`
- **OpenSearch Dashboards**: `http://localhost:5601`

---

## 📑 Interactive OpenAPI & Swagger UI Exploration

Each microservice providing REST APIs automatically exposes a rich, interactive **Swagger UI** powered by `springdoc-openapi` and Spring Boot autoconfiguration.

### How OpenAPI is Generated
1. **Dependency Integration**: Each service exposing REST endpoints includes `org.springdoc:springdoc-openapi-starter-webmvc-ui` (managed in root `pom.xml`).
2. **Declarative Annotations**:
   - Configuration classes (`OpenApiConfig.java`) are annotated with `@OpenAPIDefinition` and `@SecurityScheme` (HTTP Bearer JWT) to configure service metadata, titles, descriptions, and the Swagger UI "Authorize" dialog.
   - Controllers are annotated with `@Tag`, `@Operation`, `@ApiResponse`, `@ApiResponses`, and `@Parameter` to document query parameters, request bodies, and response codes (`200`, `201`, `400`, `404`, `409`, `410`).
3. **Endpoint Exposure**:
   - **Interactive UI**: `http://localhost:<PORT>/swagger-ui.html` (e.g. `http://localhost:8085/swagger-ui.html` for Reservation Service).
   - **Raw OpenAPI 3.0 JSON Specification**: `http://localhost:<PORT>/v3/api-docs`
   - **Public Access**: Swagger UI and OpenAPI documentation endpoints are publicly accessible without authentication. Domain business endpoints and Actuator management paths remain protected by OAuth2 Resource Server JWT validation.

### How to Use Swagger UI for Manual Inspection:
1. Start the desired service (or the full stack with Docker Compose).
2. Open the service's Swagger UI in your browser (e.g. `http://localhost:8083/swagger-ui.html` for Restaurant Service). The API documentation and schema models are immediately visible without authentication.
3. **Authorize for Protected Requests**:
   - To invoke secured business endpoints via **"Try it out"**, obtain a Keycloak JWT access token (see [Keycloak Access Token](#obtaining-a-keycloak-access-token)).
   - Click the **"Authorize"** button (lock icon) at the top right of the Swagger UI page.
   - Paste your JWT access token into the `bearerAuth` value field and click **Authorize**, then **Close**.
4. Click on any endpoint (e.g., `POST /api/v1/restaurants`), click **"Try it out"**, fill in the example JSON payload, and click **"Execute"**.
5. You will receive live HTTP response status codes, response headers, and response bodies formatted as JSON or RFC 9457 Problem Details. Requests without authorization on secured endpoints will return `401 Unauthorized`.

## 💡 Key Design Decisions & Technology Rationale

1. **Virtual Threads (Java 26 / Project Loom)**:
   - All services enable `spring.threads.virtual.enabled=true`, allowing high-throughput blocking database (JPA/JDBC) operations to scale without thread-pool exhaustion.
2. **Transactional Outbox Pattern**:
   - Core services (`reservation-service`, `restaurant-service`, `waiting-list-service`) write domain events directly to an `outbox_events` table inside the same local DB transaction as the business entity update. A background poller publishes them to Kafka with at-least-once guarantees, preventing distributed partial commits.
3. **Idempotent Consumers**:
   - Event consumers record processed `event_id` keys in a `processed_events` table to safely ignore duplicate Kafka deliveries.
4. **Deterministic Table Allocation Algorithm**:
   - Single tables are evaluated first (choosing the smallest table that accommodates the party size). If no single table suffices, configured table combinations are evaluated.
5. **RFC 7807 / RFC 9457 Problem Details & Input Constraints**:
   - All REST APIs reject malformed, out-of-bounds, or invalid inputs at the controller boundary with standard `application/problem+json` error responses (HTTP 400 Bad Request).
   - **Type-Safe Model Binding & Validation**:
     - Endpoints with JSON payloads (e.g. `POST /api/v1/reservations`, `POST /api/v1/restaurants`) bind and validate requests via `@Valid @RequestBody`.
     - Query-parameter endpoints (such as `GET /api/v1/availability`) use Spring MVC `@Valid @ModelAttribute AvailabilityQuery query` to map HTTP GET query parameters into a strongly-typed, immutable record and enforce Jakarta validation constraints before entering the service layer.
   - Validation failures provide structured field-level errors via the `invalidParams` array with `name` and `reason` pairs:
     ```json
     {
       "type": "https://example.invalid/problems/validation-error",
       "title": "Validation Failed",
       "status": 400,
       "detail": "One or more request fields failed validation",
       "invalidParams": [
         { "name": "partySize", "reason": "Party size cannot exceed 50 guests" },
         { "name": "durationMinutes", "reason": "Duration cannot exceed 480 minutes (8 hours)" }
       ]
     }
     ```
   - **Lifecycle Transitions**: Type-safe transitions on `PATCH /api/v1/reservations/{id}/status` enforce `ReservationStatus` enum values (`CONFIRMED`, `ARRIVED`, `COMPLETED`, `NO_SHOW`, `CANCELLED`). Unrecognized values return a descriptive error with allowed statuses.
   - **Authoritative Cancellation Policy**: Restaurants define `cancellationWindowHours`, which is snapshotted onto bookings upon creation. Client-supplied query parameters on cancellation are deprecated and ignored by `ReservationService` to guarantee that restaurant policies cannot be bypassed.
   - **Comprehensive Input Constraints Reference**:

     | Microservice | Endpoint / DTO | Parameter / Field | Type & Constraints | Default / Notes |
     | :--- | :--- | :--- | :--- | :--- |
     | **availability-service** | `GET /api/v1/availability`<br>`(@ModelAttribute AvailabilityQuery)` | `restaurantId`<br>`date`<br>`time`<br>`partySize` | UUID, `@NotNull`<br>LocalDate, `@NotNull`<br>LocalTime, `@NotNull`<br>Integer, `@Min(1) @Max(50)` | Required<br>Required (`YYYY-MM-DD`)<br>Required (`HH:mm:ss`)<br>Defaults to `2` |
     | **reservation-service** | `POST /api/v1/reservations`<br>`(@RequestBody CreateReservationRequest)` | `restaurantId`<br>`customerId`<br>`customerName`<br>`customerEmail`<br>`partySize`<br>`startTime`<br>`durationMinutes`<br>`cancellationWindowHours` | UUID, `@NotNull`<br>UUID<br>String, `@Size(max = 200)`<br>String, `@Email @Size(max = 255)`<br>Integer, `@Min(1) @Max(50)`<br>Instant (ISO-8601)<br>Integer, `@Min(15) @Max(480)`<br>Integer, `@Min(0) @Max(168)` | Required<br>Defaults to generated UUID<br>Defaults to `"Customer"`<br>Defaults to `"customer@example.com"`<br>Defaults to `2`<br>Role-based: customer must be future/clock-skew<br>Defaults to `90` min<br>Defaults to `2` or restaurant policy |
     | | `PATCH /api/v1/reservations/{id}/status`<br>`(@RequestBody UpdateStatusRequest)` | `status` | `ReservationStatus` enum, `@NotNull` | `CONFIRMED`, `ARRIVED`, `COMPLETED`, `NO_SHOW`, `CANCELLED` |
     | | `DELETE /api/v1/reservations/{id}` | `cancellationWindowHours`<br>`reason` | int (Deprecated / Ignored)<br>String (Optional) | Evaluated strictly against snapshotted window<br>Returns HTTP 409 if window has passed |
     | **restaurant-service** | `POST /api/v1/restaurants`<br>`(@RequestBody CreateRestaurantRequest)` | `name`<br>`address`<br>`timezone`<br>`defaultReservationDurationMinutes`<br>`minBookingAdvanceMinutes`<br>`maxBookingHorizonDays`<br>`cancellationWindowHours` | String, `@NotBlank @Size(max = 150)`<br>String, `@NotBlank @Size(max = 1000)`<br>String, `@NotBlank @Size(max = 50)`<br>Integer, `@Min(15) @Max(480)`<br>Integer, `@Min(0) @Max(10080)`<br>Integer, `@Min(1) @Max(365)`<br>Integer, `@Min(0) @Max(168)` | Required<br>Required<br>Valid IANA timezone (`Europe/Amsterdam`)<br>Defaults to `90` min<br>Defaults to `30` min<br>Defaults to `60` days<br>Defaults to `2` hours |
     | | `POST /api/v1/restaurants/{id}/tables`<br>`(@RequestBody CreateTableRequest)` | `tableNumber`<br>`capacity` | String, `@NotBlank @Size(max = 50)`<br>Integer, `@NotNull @Min(1) @Max(50)` | Table label (e.g. `"T1"`)<br>Guest seating capacity |
     | | `POST /api/v1/restaurants/{id}/table-combinations`<br>`(@RequestBody CreateCombinationRequest)` | `name`<br>`tableIds` | String, `@NotBlank @Size(max = 100)`<br>List&lt;UUID&gt;, `@NotEmpty @Size(min = 2, max = 10)` | Combination name (e.g. `"Party Hall 1"`)<br>Must contain &ge; 2 distinct table IDs |
     | | `PUT /api/v1/restaurants/{id}/opening-hours`<br>`(@RequestBody OpeningHoursConfigDto)` | `schedules[].dayOfWeek`<br>`schedules[].openTime`<br>`schedules[].closeTime`<br>`schedules[].isClosed` | Integer, `@Min(1) @Max(7)`<br>LocalTime, `@NotNull`<br>LocalTime, `@NotNull`<br>boolean | 1 = Monday, 7 = Sunday<br>Daily opening time<br>Must be strictly after `openTime`<br>Flag for scheduled closures |
     | **waiting-list-service** | `POST /api/v1/waiting-list`<br>`(@RequestBody JoinWaitingListRequest)` | `restaurantId`<br>`customerEmail`<br>`targetDate`<br>`earliestTime`<br>`latestTime`<br>`partySize` | UUID, `@NotNull`<br>String, `@NotBlank @Email @Size(max = 255)`<br>LocalDate, `@NotNull`<br>LocalTime, `@NotNull`<br>LocalTime, `@NotNull`<br>int, `@Min(1) @Max(50)` | Required restaurant UUID<br>Required customer email<br>Must be current or future (`>= today`)<br>Must be &le; `latestTime`<br>Must be &ge; `earliestTime`<br>Party size between 1 and 50 |
     | **customer-service** | `PUT /api/v1/customers/me`<br>`(@RequestBody UpdateCustomerRequest)` | `firstName`<br>`lastName`<br>`phoneNumber` | String, `@NotBlank @Size(max = 50)`<br>String, `@NotBlank @Size(max = 50)`<br>String, `@NotBlank @Size(min = 5, max = 25)` | Required<br>Required<br>Pattern: `^[+0-9() -]+$` |

---

## 🚀 Running the Application

### Option A: Local Development (Docker Compose Infrastructure + Local Java Services)
This is the recommended workflow when developing or debugging individual microservices in your IDE:

1. **Start Supporting Infrastructure**:
   ```bash
   docker compose -f infrastructure/docker-compose.yml up -d
   ```
2. **Build and Test the Entire Reactor**:
   ```bash
   ./mvnw clean test
   ```
3. **Run Desired Service** (e.g. Reservation Service):
   ```bash
   ./mvnw spring-boot:run -pl services/reservation-service
   ```

---

### Option B: Full-Stack Docker Compose (Infrastructure + All Microservices)
Run the entire platform including all 8 microservices and frontend portals in Docker.

> [!IMPORTANT]
> The service Dockerfiles copy the pre-built Spring Boot executable JARs from each module's `target/` directory (e.g. `services/restaurant-service/target/restaurant-service-1.0.0-SNAPSHOT.jar`). Therefore, you **must package the Maven artifacts first** before invoking `docker compose` to build the container images:

1. **Package All Microservices (Build Executable JARs)**:
   ```bash
   ./mvnw clean package -DskipTests
   ```
   *(Or `./mvnw clean package` to run all unit and slice tests during the packaging).*

2. **Build Container Images and Start Full Stack**:
   ```bash
   docker compose -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.apps.yml up --build -d
   ```

3. **Check Running Containers**:
   ```bash
   docker compose -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.apps.yml ps
   ```

4. **Stop Full Stack**:
   ```bash
   docker compose -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.apps.yml down
   ```

---

### Option C: Kubernetes (k8s) Deployment
The repository includes standard Kubernetes manifests with ConfigMaps, Secrets, Deployments, ClusterIP Services, and Actuator Liveness/Readiness probes:

1. **Deploy to Minikube, Kind, or a Remote Kubernetes Cluster**:
   ```bash
   kubectl apply -k k8s/
   ```
2. **Verify Pod Rollout**:
   ```bash
   kubectl get pods -n rube-goldberg -w
   ```
3. **Access API Gateway via Port-Forward**:
   ```bash
   kubectl port-forward svc/gateway -n rube-goldberg 8080:8080
   ```

---

## 🧪 Interactive End-to-End Walkthrough

Follow these steps via `curl` to experience the full **Rube Goldberg Event Chain Reaction** directly against the assigned microservice ports using Role-Based Access Control (RBAC):

### Step 0: Acquire Role Tokens (Keycloak Direct Grants)

Secure endpoints require Bearer authentication. Acquire tokens for **Restaurant Manager** (`manager1`) and **Customer** (`customer1`):

```bash
# 1. Restaurant Manager Token (manager1 - RESTAURANT_MANAGER)
MANAGER_TOKEN=$(curl -s -X POST "http://localhost:8081/realms/rube-goldberg/protocol/openid-connect/token" \
  -d "client_id=rube-goldberg-app" \
  -d "grant_type=password" \
  -d "username=manager1" \
  -d "password=password" | jq -r .access_token)

# 2. Customer Token (customer1 - CUSTOMER)
CUSTOMER_TOKEN=$(curl -s -X POST "http://localhost:8081/realms/rube-goldberg/protocol/openid-connect/token" \
  -d "client_id=rube-goldberg-app" \
  -d "grant_type=password" \
  -d "username=customer1" \
  -d "password=password" | jq -r .access_token)
```

### Step 1: Register a Restaurant & Tables (`restaurant-service: 8083`)
*Requires `RESTAURANT_MANAGER` or `ADMIN` role.*
```bash
# 1. Create Restaurant with authoritative operational boundaries
REST_ID=$(curl -s -X POST http://localhost:8083/api/v1/restaurants \
  -H "Authorization: Bearer $MANAGER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "The Gourmet Goldberg",
    "address": "100 Reactor Way, Amsterdam",
    "timezone": "Europe/Amsterdam",
    "defaultReservationDurationMinutes": 90,
    "minBookingAdvanceMinutes": 30,
    "maxBookingHorizonDays": 60,
    "cancellationWindowHours": 2
  }' | jq -r '.id')
echo "Created Restaurant ID: $REST_ID"

# 2. Add Table 1 (Capacity: 4 guests, bounded 1-50)
curl -s -X POST http://localhost:8083/api/v1/restaurants/$REST_ID/tables \
  -H "Authorization: Bearer $MANAGER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"tableNumber": "T1", "capacity": 4}' | jq .
```
> [!NOTE]
> `CreateRestaurantRequest` validates that `name` (&le; 150 chars) and `address` (&le; 1000 chars) are non-blank, `timezone` is a valid IANA zone ID, `defaultReservationDurationMinutes` is between 15 and 480, `minBookingAdvanceMinutes` is between 0 and 10,080 (7 days), `maxBookingHorizonDays` is between 1 and 365, and `cancellationWindowHours` is between 0 and 168 hours.

### Step 2: Query Real-Time Availability (`availability-service: 8084`)
*Accessible to all platform roles (`CUSTOMER`, `RESTAURANT_MANAGER`, `ADMIN`).*

The `availability-service` binds GET query parameters into a strongly-typed `@Valid @ModelAttribute AvailabilityQuery query` record:
```bash
curl -s -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  "http://localhost:8084/api/v1/availability?restaurantId=$REST_ID&date=2026-09-01&time=19:00:00&partySize=4" | jq .
```
*Expected: `isAvailable: true`*

> [!TIP]
> **Model Attribute Binding & Bounds**:
> The `AvailabilityQuery` record components are bound directly from HTTP GET query parameters:
> - `restaurantId` (UUID, `@NotNull`, required)
> - `date` (`LocalDate` in `YYYY-MM-DD` ISO format, `@NotNull`, required)
> - `time` (`LocalTime` in `HH:mm:ss` ISO format, `@NotNull`, required)
> - `partySize` (`Integer`, bounded `@Min(1) @Max(50)`, optional — defaults to `2` if omitted)
>
> If validation fails (e.g. `partySize=99` or missing required fields), Spring MVC and `ValidationExceptionHandler` automatically return an RFC 7807 `400 Bad Request` with field-level details in `invalidParams`.

### Step 3: Book the Table (Guaranteed Reservation) (`reservation-service: 8085`)
*Requires `CUSTOMER` role (managers/admins without the customer role receive 403 Forbidden).*
```bash
RES_ID=$(curl -s -X POST http://localhost:8085/api/v1/reservations \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"restaurantId\": \"$REST_ID\",
    \"customerId\": \"11111111-1111-1111-1111-111111111111\",
    \"customerName\": \"Alice\",
    \"customerEmail\": \"alice@example.com\",
    \"partySize\": 4,
    \"startTime\": \"2026-09-01T17:00:00Z\",
    \"durationMinutes\": 90
  }" | jq -r '.id')
echo "Confirmed Reservation: $RES_ID"
```
*Observe: Outbox publishes `ReservationCreatedEvent` → Notification service renders email → Check Mailpit at `http://localhost:8025`!*

> [!NOTE]
> `CreateReservationRequest` validates that `restaurantId` is present, `partySize` is bounded 1–50, `durationMinutes` is bounded 15–480, and `customerEmail` is a valid format (&le; 255 chars). For customer bookings, `startTime` is strictly checked against the server clock to ensure it is not in the past (with a 5-minute clock-skew grace period) and does not exceed the 365-day booking horizon.

### Step 4: Check Availability Again (Cache Invalidation) (`availability-service: 8084`)
```bash
curl -s -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  "http://localhost:8084/api/v1/availability?restaurantId=$REST_ID&date=2026-09-01&time=19:00:00&partySize=4" | jq .
```
*Expected: `isAvailable: false` (the only 4-top table is now booked).*

### Step 5: Join the Fair FIFO Waiting List (`waiting-list-service: 8086`)
*Requires `CUSTOMER` role.*
```bash
WAIT_ID=$(curl -s -X POST http://localhost:8086/api/v1/waiting-list \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"restaurantId\": \"$REST_ID\",
    \"customerId\": \"22222222-2222-2222-2222-222222222222\",
    \"customerEmail\": \"bob@example.com\",
    \"targetDate\": \"2026-09-01\",
    \"earliestTime\": \"18:00:00\",
    \"latestTime\": \"21:00:00\",
    \"partySize\": 4
  }" | jq -r '.id')
echo "Joined Waiting List: $WAIT_ID"
```
> [!NOTE]
> `JoinWaitingListRequest` validates that `partySize` is bounded 1–50, `customerEmail` is valid, `targetDate` is current or future (`>= today`), and `earliestTime` is earlier than or equal to `latestTime`.

### Step 6: Trigger the Cancellation Chain Reaction (`reservation-service: 8085`)
*Accessible to `CUSTOMER`, `RESTAURANT_MANAGER`, or `ADMIN`.*
```bash
curl -s -X DELETE "http://localhost:8085/api/v1/reservations/$RES_ID?reason=Change%20of%20plans" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" | jq .
```
> [!IMPORTANT]
> **Authoritative Cancellation Window**:
> The `cancellationWindowHours` query parameter is **deprecated and ignored** by the service. Cancellation deadlines are authoritatively evaluated against the restaurant's `cancellationWindowHours` snapshotted on the reservation entity when booked (`deadline = startTime - cancellationWindowHours`). If the deadline has already passed, the server responds with `HTTP 409 Conflict` (`https://example.invalid/problems/cancellation-window-passed`).

*The Goldberg Cascade in Motion:*
1. `reservation-service` marks reservation `CANCELLED`, releases Table T1, and emits `ReservationCancelledEvent`.
2. `waiting-list-service` receives the cancellation event, matches Bob as the oldest candidate, and creates a 15-minute time-limited offer.
3. `notification-service` emails Bob via Mailpit with his exclusive claim link.
4. `availability-service` updates Redis projections.
5. `analytics-service` records cancellation KPI.

### Step 6b: Accept Waiting List Cancellation Offer (`waiting-list-service: 8086`)
*Requires `CUSTOMER` role.*
```bash
# Bob accepts the exclusive 15-minute offer:
curl -s -X POST "http://localhost:8086/api/v1/waiting-list/offers/$OFFER_ID/accept" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" | jq .
```
*Expected: `200 OK` on timely acceptance. If expired past 15 minutes, returns `HTTP 410 Gone` (`https://example.invalid/problems/offer-expired`).*

### Step 6c: Update Reservation Lifecycle Status (`reservation-service: 8085`)
*Requires `RESTAURANT_MANAGER` or `ADMIN` role.*
```bash
# Restaurant staff updates customer arrival status:
curl -s -X PATCH "http://localhost:8085/api/v1/reservations/$RES_ID/status" \
  -H "Authorization: Bearer $MANAGER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status": "ARRIVED"}' | jq .
```
> [!NOTE]
> `UpdateStatusRequest` strictly enforces the `ReservationStatus` enum: `CONFIRMED`, `ARRIVED`, `COMPLETED`, `NO_SHOW`, `CANCELLED`. Providing an invalid status string returns an RFC 7807 `400 Bad Request` listing all permitted values.

### Step 6d: Query Paginated Reservations List (`reservation-service: 8085`)
*Requires `RESTAURANT_MANAGER` or `ADMIN` role.*
```bash
# Query paginated reservations list using standard Spring Data Pageable query parameters:
curl -s -H "Authorization: Bearer $MANAGER_TOKEN" \
  "http://localhost:8085/api/v1/reservations?restaurantId=$REST_ID&page=0&size=10&sort=startTime,desc" | jq .
```
> [!TIP]
> **Pagination & Sorting Query Parameters**:
> - `restaurantId` (UUID, required): Target restaurant UUID.
> - `page` (integer, optional, default: `0`): Zero-based page index.
> - `size` (integer, optional, default: `20`): Number of records per page.
> - `sort` (string, optional, e.g. `startTime,desc` or `startTime,asc`): Sort field and direction.

### Step 7: Inspect Real-Time Business Analytics (`analytics-service: 8087`)
*Requires `RESTAURANT_MANAGER` or `ADMIN` role.*
```bash
curl -s -H "Authorization: Bearer $MANAGER_TOKEN" \
  "http://localhost:8087/api/v1/analytics/summary?restaurantId=$REST_ID" | jq .
```

---

## 🖥️ Web Portals

The application includes lightweight, interactive responsive web interfaces served via the API Gateway:
- **Customer Portal**: `http://localhost:8080/ui/customer/index.html` (Check availability, book tables, join waiting list)
- **Manager Portal**: `http://localhost:8080/ui/manager/index.html` (Real-time live operational analytics)

---

## 🔐 Keycloak Security Configuration

The realm export file is pre-configured at `infrastructure/keycloak/realm-export.json`:
- **Realm**: `rube-goldberg`
- **Client**: `rube-goldberg-app`
- **Pre-seeded Users**:
  - `customer1` / `password` (Role: `CUSTOMER`)
  - `manager1` / `password` (Role: `RESTAURANT_MANAGER`)
  - `admin1` / `password` (Role: `ADMIN`)

### 🛡️ Role-Based Access Control (RBAC) & Responsibilities

The platform enforces strict Role-Based Access Control on all microservice endpoints using realm roles encoded in Keycloak JWT tokens under `realm_access.roles`. Each role defines specific boundaries and responsibilities:

#### Role Responsibilities

1. **`CUSTOMER` (Dining Customer)**:
   - **Allowed**: Browse restaurant catalog listings and schedules, query real-time table availability, book guaranteed table reservations (`POST /api/v1/reservations`), join fair FIFO waiting lists (`POST /api/v1/waiting-list`), accept waiting list cancellation offers, inspect and cancel personal reservations (`GET`/`DELETE /api/v1/reservations/{id}`), and manage personal customer profiles (`/api/v1/customers/me`).
   - **Forbidden**: Registering restaurants, configuring tables or combinations, updating opening hour schedules, querying restaurant-wide reservation lists, updating reservation lifecycle statuses, or viewing platform business analytics.
2. **`RESTAURANT_MANAGER` (Restaurant Operator)**:
   - **Allowed**: Register restaurant establishments (`POST /api/v1/restaurants`), manage dining tables and combinable tables, configure operating hours schedules, query restaurant-wide reservation lists (`GET /api/v1/reservations?restaurantId=...`), update reservation lifecycle statuses (`PATCH /api/v1/reservations/{id}/status`), view individual reservations, cancel reservations, and access operational business analytics (`GET /api/v1/analytics/**`).
   - **Forbidden**: Booking dining reservations or joining waiting lists unless explicitly granted the `CUSTOMER` role.
3. **`ADMIN` (Platform Administrator)**:
   - **Allowed**: Full platform management capabilities across all establishments, table inventories, schedules, reservation statuses, and operational analytics. Like managers, administrators cannot create dining reservations or join waiting lists unless also granted the `CUSTOMER` role.

#### Endpoint Permission Matrix

| Microservice | Direct Port | Endpoint Path | Method | Minimum Required Role(s) | Unauthenticated | Unauthorized Role |
|---|---|---|---|---|---|---|
| **restaurant-service** | `8083` | `/api/v1/restaurants` | `POST` | `RESTAURANT_MANAGER`, `ADMIN` | 401 Unauthorized | 403 Forbidden |
| | | `/api/v1/restaurants/*/tables` | `POST` | `RESTAURANT_MANAGER`, `ADMIN` | 401 Unauthorized | 403 Forbidden |
| | | `/api/v1/restaurants/*/table-combinations` | `POST` | `RESTAURANT_MANAGER`, `ADMIN` | 401 Unauthorized | 403 Forbidden |
| | | `/api/v1/restaurants/*/opening-hours` | `PUT` | `RESTAURANT_MANAGER`, `ADMIN` | 401 Unauthorized | 403 Forbidden |
| | | `/api/v1/restaurants/**` | `GET` | `CUSTOMER`, `RESTAURANT_MANAGER`, `ADMIN` | 401 Unauthorized | 403 Forbidden |
| **availability-service** | `8084` | `/api/v1/availability/**` | `GET` | `CUSTOMER`, `RESTAURANT_MANAGER`, `ADMIN` | 401 Unauthorized | 403 Forbidden |
| **reservation-service** | `8085` | `/api/v1/reservations` | `POST` | `CUSTOMER` | 401 Unauthorized | 403 Forbidden |
| | | `/api/v1/reservations/*` | `GET`, `DELETE` | `CUSTOMER`, `RESTAURANT_MANAGER`, `ADMIN` | 401 Unauthorized | 403 Forbidden |
| | | `/api/v1/reservations` | `GET` | `RESTAURANT_MANAGER`, `ADMIN` | 401 Unauthorized | 403 Forbidden |
| | | `/api/v1/reservations/*/status` | `PATCH` | `RESTAURANT_MANAGER`, `ADMIN` | 401 Unauthorized | 403 Forbidden |
| **waiting-list-service** | `8086` | `/api/v1/waiting-list/**` | `POST` | `CUSTOMER` | 401 Unauthorized | 403 Forbidden |
| **customer-service** | `8082` | `/api/v1/customers/**` | `GET`, `PUT` | `CUSTOMER` | 401 Unauthorized | 403 Forbidden |
| **analytics-service** | `8087` | `/api/v1/analytics/**` | `GET` | `RESTAURANT_MANAGER`, `ADMIN` | 401 Unauthorized | 403 Forbidden |
| **All Services** | Any | `/swagger-ui/**`, `/v3/api-docs/**` | `GET` | None (`permitAll`) | 200 OK | N/A |

### Obtaining a Keycloak Access Token

To interact with secured endpoints or authenticate in Swagger UI, obtain a JWT access token from the Keycloak OpenID Connect token endpoint using Direct Access Grants (`grant_type=password`).

- **Token Endpoint**: `http://localhost:8081/realms/rube-goldberg/protocol/openid-connect/token` (requires HTTP **POST**)
- **Client ID**: `rube-goldberg-app` (public client)
- **OIDC Discovery Endpoint** (browser GET): `http://localhost:8081/realms/rube-goldberg/.well-known/openid-configuration`

> [!NOTE]
> The token endpoint strictly accepts HTTP **POST** requests (per OAuth 2.0 / OIDC RFC 6749). Navigating to `.../protocol/openid-connect/token` in a browser or sending a `GET` request returns `405 Method Not Allowed`. Similarly, the prefix path `.../protocol/openid-connect` is not a standalone resource and returns `404 Not Found`. To inspect realm metadata in a browser, use the **OIDC Discovery Endpoint** above.

#### 1. Export Token as Environment Variable (`curl` + `jq`)

Use the one-liner below to request an access token for **`customer1`** and export it directly to `TOKEN`:

```bash
export TOKEN=$(curl -s -X POST "http://localhost:8081/realms/rube-goldberg/protocol/openid-connect/token" \
  -d "client_id=rube-goldberg-app" \
  -d "grant_type=password" \
  -d "username=customer1" \
  -d "password=password" | jq -r .access_token)
```

To obtain a token for other seeded roles, substitute the `username`:
- **Restaurant Manager**:
  ```bash
  export TOKEN=$(curl -s -X POST "http://localhost:8081/realms/rube-goldberg/protocol/openid-connect/token" \
    -d "client_id=rube-goldberg-app" \
    -d "grant_type=password" \
    -d "username=manager1" \
    -d "password=password" | jq -r .access_token)
  ```
- **Administrator**:
  ```bash
  export TOKEN=$(curl -s -X POST "http://localhost:8081/realms/rube-goldberg/protocol/openid-connect/token" \
    -d "client_id=rube-goldberg-app" \
    -d "grant_type=password" \
    -d "username=admin1" \
    -d "password=password" | jq -r .access_token)
  ```

#### 2. Fallback (Raw `curl` without `jq`)

If `jq` is not installed, inspect the raw JSON response containing `access_token`, `expires_in`, and `refresh_token`:

```bash
curl -X POST "http://localhost:8081/realms/rube-goldberg/protocol/openid-connect/token" \
  -d "client_id=rube-goldberg-app" \
  -d "grant_type=password" \
  -d "username=customer1" \
  -d "password=password"
```

#### 3. Verify Token Authenticity & Manage Customer Profile (`customer-service: 8082`)

Verify that your token is valid by invoking the protected Customer Service endpoints:

```bash
# 1. Inspect authenticated profile (auto-provisions or retrieves Keycloak identity)
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8082/api/v1/customers/me | jq .

# 2. Update customer contact details with bounded validation
curl -s -X PUT http://localhost:8082/api/v1/customers/me \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Alice",
    "lastName": "Smith",
    "phoneNumber": "+31612345678"
  }' | jq .
```

A valid token returns `HTTP/1.1 200 OK` with updated customer profile data. Unauthenticated requests or invalid tokens return `HTTP/1.1 401 Unauthorized`.

> [!NOTE]
> `UpdateCustomerRequest` validates that `firstName` (&le; 50 chars) and `lastName` (&le; 50 chars) are non-blank, and `phoneNumber` (5 to 25 chars) strictly conforms to pattern `^[+0-9() -]+$`. Malformed inputs are rejected with RFC 7807 `400 Bad Request` (`invalidParams`).

### Multi-Issuer JWT Validation & Environment Topology

The platform supports profile-based token validation across different network topologies (local development vs containerized Docker Compose):
- **External Host Issuer**: `http://localhost:8081/realms/rube-goldberg` (tokens minted from host browser, Swagger UI, or host CLI)
- **Internal Docker Issuer**: `http://keycloak:8080/realms/rube-goldberg` (tokens minted inside the Docker network or between microservices)

All secured domain microservices (`customer-service`, `restaurant-service`, `availability-service`, `reservation-service`, `waiting-list-service`, `analytics-service`) dynamically configure their `JwtDecoder` based on the active Spring profile:
- In **Docker Compose** (`docker` profile via `SPRING_PROFILES_ACTIVE=docker`): The `@Profile("docker")` bean `multiIssuerJwtDecoder` is activated. It uses `JwtMultiIssuerValidator` with `security.jwt.accepted-issuers` to accept tokens minted against both the internal Keycloak network (`http://keycloak:8080/...`) and external host requests via Swagger UI (`http://localhost:8081/...`).
- In **Local Development** (when the `docker` profile is not active): The `@Profile("!docker")` bean `singleIssuerJwtDecoder` is active as a fallback. It uses standard Spring Security single-issuer validation strictly against `spring.security.oauth2.resourceserver.jwt.issuer-uri` (`http://localhost:8081/...`).

---

## 📊 Observability & Telemetry

- **Prometheus Metrics**: Scraped from Spring Boot `/actuator/prometheus` via OpenTelemetry Collector.
- **Alertmanager Rules**: Defined in `infrastructure/prometheus/alert-rules.yml` for automated error rate and downtime alerts.
- **Grafana Platform Dashboard**: Pre-configured JSON dashboard in `infrastructure/grafana/dashboards/rube-goldberg-dashboard.json`.
