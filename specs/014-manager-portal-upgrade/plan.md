# Implementation Plan: Manager Portal Upgrade & Authentication

**Branch**: `014-manager-portal-upgrade` | **Date**: 2026-09-16 | **Spec**: [spec.md](file:///Users/lazolazarev/projects/springboot_rube_goldberg/specs/014-manager-portal-upgrade/spec.md)

**Input**: Feature specification from `specs/014-manager-portal-upgrade/spec.md`

---

## Summary

Upgrade the frontend Restaurant Manager Portal from a broken, single-view analytics card into a secure, comprehensive operational dashboard. The portal will gate access behind Keycloak OpenID Connect (OIDC) Authorization Code Flow with PKCE, enforcing `ROLE_RESTAURANT_MANAGER` and `ROLE_ADMIN` role checks and presenting unauthenticated visitors with a sign-in screen. It resolves the broken `/api/v1/analytics/summary` integration by forwarding bearer tokens and handling loading/error states. It empowers managers to execute all system management functions: interactive restaurant scoping with an "All Restaurants" global toggle, reservation lifecycle tracking (arrival, completion, no-show, cancellation) and walk-in back-filling, dining floor and table configuration, real-time availability checks, and waiting list queue oversight backed by a new manager query endpoint (`GET /api/v1/waiting-list`) in `waiting-list-service`.

---

## Technical Context

**Language/Version**: Java 26, JavaScript (ES2022+), HTML5, CSS3  
**Primary Dependencies**: Spring Boot 4.1.1, Spring Cloud Gateway, Keycloak JS (v26.1.0), Bootstrap 5.3.3  
**Storage**: PostgreSQL 17 (`waiting_list_db`), Redis 7  
**Testing**: JUnit 5, Mockito, Spring `@WebMvcTest`, MockMvc  
**Target Platform**: Docker Compose / Modern Desktop & Tablet Web Browsers  
**Project Type**: Multi-module Maven Web Application / SPA Frontend + Microservices  
**Performance Goals**: Portal first-render under 500ms; sub-second live analytics and reservation status transitions  
**Constraints**: Backend microservices frozen except targeted `GET /api/v1/waiting-list` oversight endpoint and CORS/OIDC config; zero compilation errors across Maven reactor  
**Scale/Scope**: Unified manager portal SPA, 1 new microservice REST endpoint, 2 test suites  

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I: Strict Specification Adherence (NON-NEGOTIABLE)**: PASS. Plan strictly adheres to `spec.md`, implementing all 13 functional requirements, handling all edge cases, and satisfying all acceptance scenarios.
- **Principle II: Maven Reactor & Microservices Architecture**: PASS. Retains all module boundaries (`gateway`, `services/waiting-list-service`, `ui/`). No circular dependencies.
- **Principle III: Modern Spring Boot Feature Showcase**: PASS. Demonstrates Spring Security OAuth2 resource server RBAC, Spring Web MVC, declarative OpenAPI annotations, and Spring Cloud Gateway routing.
- **Principle IV: Agentic AI-Aided Development & Traceability**: PASS. Design artifacts (`research.md`, `data-model.md`, `contracts/`, `quickstart.md`) provide complete traceability.
- **Principle V: Comprehensive Testing & Quality Gates (NON-NEGOTIABLE)**: PASS. Includes slice tests (`@WebMvcTest`) for the new waiting list query endpoint validating both authorized manager access and forbidden customer/unauthenticated access. Full reactor `mvn clean test` required.

---

## Project Structure

### Documentation (this feature)

```text
specs/014-manager-portal-upgrade/
├── plan.md              # This implementation plan
├── research.md          # Architecture decisions (Keycloak PKCE, scoping, API integration)
├── data-model.md        # Session, RestaurantView, DiningTable, Reservation, WaitingList data schemas
├── quickstart.md        # Verification scenarios and test execution guide
├── contracts/           # API and interface contracts
│   └── manager-portal-api-contract.md
├── checklists/
│   └── requirements.md  # Specification quality checklist
└── tasks.md             # Generated in Phase 2 via /speckit-tasks
```

### Source Code Layout

```text
ui/src/manager/
├── index.html                                        # Manager portal SPA dashboard structure
└── app.js                                            # Manager portal logic, Keycloak OIDC, API client

gateway/src/main/resources/static/ui/manager/
├── index.html                                        # Synchronized static asset for gateway distribution
└── app.js                                            # Synchronized static asset for gateway distribution

services/waiting-list-service/
├── src/main/java/nl/invokedynamic/demo/waitinglist/
│   ├── api/WaitingListController.java                # Add GET /api/v1/waiting-list endpoint
│   ├── service/WaitingListService.java               # Add getWaitingList() service method
│   ├── repository/WaitingListEntryRepository.java    # Add findByRestaurantId... repository query
│   └── config/SecurityConfig.java                    # Update RBAC for GET /api/v1/waiting-list
└── src/test/java/nl/invokedynamic/demo/waitinglist/
    ├── api/WaitingListControllerWebMvcTest.java      # Slice tests for GET /api/v1/waiting-list
    └── WaitingListSecurityTest.java                  # Security tests for RBAC enforcement
```

**Structure Decision**: Multi-module reactor where the frontend manager portal assets live in `ui/src/manager/` and are mirrored in `gateway/src/main/resources/static/ui/manager/` for production container packaging, while the backend waitlist query capability is encapsulated in `services/waiting-list-service`.

---

## Complexity Tracking

> *No constitutional violations. All implementations stay within existing architectural patterns.*

| Item | Why Needed | Alternative Rejected |
|------|------------|----------------------|
| Keycloak JS via CDN | Native browser PKCE OIDC authorization code flow without backend secret | Backend-for-frontend (BFF) session proxy rejected to preserve microservice statelessness |
| Dedicated `GET /api/v1/waiting-list` | Real-time queue inspection of active waitlist entries for floor managers | Reusing aggregated counts only rejected per user decision during clarification |
