# Implementation Plan: Customer Portal Upgrade

**Branch**: `015-customer-portal-upgrade` | **Date**: 2026-09-17 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/015-customer-portal-upgrade/spec.md`

## Summary

The Customer Portal Upgrade introduces an intuitive, authenticated, real-time single-page web interface for restaurant customers. Key capabilities include:
1. Secure Keycloak OIDC gatekeeping with PKCE and customer role verification (`CUSTOMER`, `ROLE_CUSTOMER`, or `ADMIN`).
2. An adaptive table search and reservation flow:
   - When available, allows instant booking with customer contact details automatically populated.
   - When fully booked, presents a guided opt-in prompt to join the fair FIFO waiting list with an auto-populated ±1 hour seating window.
3. Self-service management of upcoming reservations with confirmation-guarded cancellation.
4. Active waiting list tracking with self-service queue removal.
5. Real-time push updates via Server-Sent Events (SSE) hosted by `notification-service` and routed through Spring Cloud Gateway, enabling instant notifications and a 15-minute countdown timer with a one-click "Accept Offer" action when a table opens up.

---

## Technical Context

**Language/Version**: Java 26, Spring Boot 3.4+ / 4.x baseline, HTML5 / ECMAScript 2022+ (Vanilla JS).

**Primary Dependencies**:
- Spring Cloud Gateway (reactive routing & static asset hosting)
- Spring Boot Starter Web (`notification-service` SSE controller)
- Spring Boot Starter Security & OAuth2 Resource Server (`notification-service` SSE endpoint protection)
- Spring Kafka (`notification-service` domain event ingestion)
- Bootstrap 5.3 + Bootstrap Icons (UI responsive styling)
- Bundled Keycloak JavaScript Client (`keycloak.js`)

**Storage**: PostgreSQL (existing `notification_db`, `reservation_db`, `waiting_list_db`), Redis.

**Testing**: JUnit 5, Mockito, Spring `@WebMvcTest`, Spring `@SpringBootTest`, Testcontainers, Playwright / curl integration checks.

**Target Platform**: Docker containerized microservices running on Linux / macOS, modern evergreen desktop and mobile browsers.

**Project Type**: Multi-module Maven reactor microservices application with static single-page frontend.

**Performance Goals**:
- Sub-second UI initial render (< 500ms).
- Immediate SSE push notification latency from Kafka event ingestion (< 200ms).
- Sub-60 second end-to-end table search to reservation completion.

**Constraints**:
- Core reservation, table allocation, and waiting list business logic remains unaffected.
- Backend additions are strictly scoped to the `notification-service` SSE streaming endpoint and Spring Cloud Gateway routing.
- Microservice memory constraints preserved (JAVA_TOOL_OPTIONS `-Xms128m -Xmx384m`, 512MB container limit).

**Scale/Scope**:
- Customer portal interface (`ui/src/customer/` mirrored to `gateway/src/main/resources/static/ui/customer/`).
- `notification-service` SSE emitter service, controller, and security configuration.
- Spring Cloud Gateway routing rules for notifications and customer UI.

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Requirement | Assessment | Status |
|-----------|-------------|------------|--------|
| **I. Strict Specification Adherence** | Implement exact features in spec without arbitrary shortcuts | Spec and user clarifications (Keycloak, SSE in notification-service, ±1h window, offer countdown) adhered to 100%. | **PASS** |
| **II. Maven Reactor Architecture** | Multi-module structure under `nl.invokedynamic.demo` | Changes confined to `gateway`, `notification-service`, and UI directory; root reactor preserved. | **PASS** |
| **III. Modern Spring Boot Standards** | Idiomatic Spring Boot features, Actuator, metrics, declarative config | SseEmitter in Spring MVC, declarative OAuth2 resource server, structured logging. | **PASS** |
| **IV. Agentic AI-Aided Traceability** | Predictable build commands, clear specs, traceable commits | Clear artifacts (`spec.md`, `research.md`, `data-model.md`, `contracts`, `quickstart.md`). | **PASS** |
| **V. Comprehensive Testing & Quality Gates** | Automated unit, slice, and integration tests passing cleanly | WebMvc and unit tests added for new SSE endpoint and security config; full reactor `./mvnw test` verified. | **PASS** |

---

## Project Structure

### Documentation (this feature)

```text
specs/015-customer-portal-upgrade/
├── spec.md              # Feature specification & clarifications
├── plan.md              # Implementation plan (this file)
├── research.md          # Technical research & architectural decisions
├── data-model.md        # Entity definitions & state machines
├── contracts/
│   └── customer-portal-api-contract.md # Complete API & SSE specification
├── quickstart.md        # End-to-end testing & verification guide
├── checklists/
│   └── requirements.md  # Requirements quality checklist
└── tasks.md             # Work package task breakdown (generated via /speckit-tasks)
```

### Source Code (repository root)

```text
gateway/
├── src/main/resources/
│   ├── application.yml                             # Add notification-service route & gateway route rules
│   └── static/ui/customer/
│       ├── index.html                              # Customer Portal HTML single-page layout
│       ├── app.js                                  # Customer Portal JavaScript application logic
│       └── keycloak.js                             # Self-hosted Keycloak JS client library

services/notification-service/
├── pom.xml                                         # Add spring-boot-starter-security & oauth2-resource-server
├── src/main/java/nl/invokedynamic/demo/notification/
│   ├── api/
│   │   └── NotificationSseController.java          # GET /api/v1/notifications/stream endpoint
│   ├── config/
│   │   └── SecurityConfig.java                     # Security configuration for SSE stream
│   ├── service/
│   │   └── CustomerSseEmitterService.java          # SseEmitter registration & customer event broadcast
│   └── events/
│       └── NotificationEventListener.java          # Forward Kafka events to CustomerSseEmitterService
└── src/test/java/nl/invokedynamic/demo/notification/
    └── api/
        ├── NotificationSseControllerWebMvcTest.java# WebMvc slice test for SSE endpoint
        └── NotificationSecurityTest.java           # Security test for SSE authorization

ui/src/customer/
├── index.html                                      # Source customer portal template
├── app.js                                          # Source customer portal JS logic
└── keycloak.js                                     # Source Keycloak adapter
```

---

## Complexity Tracking

> No constitution violations detected. Backend scope additions are authorized specifically for real-time SSE streaming.
