# Implementation Plan: Consolidate Frontend UI Assets into Gateway

**Branch**: `019-consolidate-ui-assets` | **Date**: 2026-09-18 | **Spec**: [spec.md](file:///Users/lazolazarev/projects/springboot_rube_goldberg/specs/019-consolidate-ui-assets/spec.md)

**Input**: Feature specification from `specs/019-consolidate-ui-assets/spec.md`

---

## Summary

Consolidate all frontend static web assets (Customer Portal and Restaurant Manager Portal) into `gateway/src/main/resources/static/ui/` as the single canonical source of truth and delete the redundant top-level `ui/` directory. Align active living documentation (`README.md`) while preserving historical spec records, ensuring zero regressions across reactor builds, Jib container packaging, and runtime static asset delivery from the API Gateway.

---

## Technical Context

**Language/Version**: Java 26, HTML5 / Modern JavaScript (ES6+), Bootstrap 5.3  
**Primary Dependencies**: Spring Cloud Gateway (Spring Boot 4.1.1, Spring Cloud 2025.1.3), Keycloak JavaScript Adapter 26.1.0, Jib Maven Plugin 3.4.4  
**Storage**: N/A (Static files served directly from classpath)  
**Testing**: JUnit 5, Spring WebFlux slice testing, curl verification  
**Target Platform**: JVM on macOS/Linux/Windows, Docker container runtime (`eclipse-temurin:26-jre-alpine`)  
**Project Type**: Multi-module Maven reactor (API Gateway microservice)  
**Performance Goals**: Sub-10ms (p95) static asset delivery from embedded Gateway server locally, and sub-50ms (p99) in container runtime  
**Constraints**: Zero duplicate asset maintenance; zero broken relative script references; strict `./mvnw` usage  
**Scale/Scope**: 2 frontend portals (`customer`, `manager`), 6 static files consolidated into `gateway`  

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Requirement | Compliance Status | Notes |
| :--- | :--- | :---: | :--- |
| **I. Strict Specification Adherence** | Follow approved feature specification without unauthorized deviations. | **PASS** | Plan strictly implements FR-001 through FR-005 and SC-001 through SC-004. |
| **II. Maven Reactor & Microservices** | Maintain clean submodule boundaries in Maven reactor. | **PASS** | Colocates static assets into the responsible gateway module (`gateway/src/main/resources/static/ui/`). |
| **III. Modern Spring Boot Showcase** | Use idiomatic Spring Boot conventions. | **PASS** | Uses standard Spring Boot classpath static resource location (`classpath:/static/`). |
| **IV. Agentic Traceability** | Optimize structure for deterministic pair programming and documentation. | **PASS** | Eliminates duplicate source directories, preventing confusion and sync drift for agents and humans. |
| **V. Comprehensive Quality Gates** | All tests and builds must pass cleanly. | **PASS** | Verified with `./mvnw clean test` and Jib container builds. |

---

## Project Structure

### Documentation (this feature)

```text
specs/019-consolidate-ui-assets/
├── spec.md              # Feature specification
├── plan.md              # This file (implementation plan)
├── research.md          # Technical analysis & decisions
├── data-model.md        # Static asset catalog & routing model
├── quickstart.md        # Step-by-step verification guide
├── contracts/
│   └── portal-serving-contract.md # Gateway static serving HTTP contract
└── checklists/
    └── requirements.md  # Specification quality checklist
```

### Source Code Layout

```text
springboot_rube_goldberg/
├── pom.xml                                   # Root Maven reactor POM
├── README.md                                 # Active developer documentation (updated)
├── gateway/                                  # API Gateway submodule
│   ├── pom.xml
│   └── src/
│       └── main/
│           └── resources/
│               ├── application.yml
│               └── static/                   # CANONICAL STATIC ASSET ROOT
│                   └── ui/
│                       ├── customer/         # Customer Portal SPA
│                       │   ├── app.js
│                       │   ├── index.html
│                       │   └── keycloak.js
│                       └── manager/          # Manager Portal SPA
│                           ├── app.js
│                           ├── index.html
│                           └── keycloak.js
├── services/                                 # Backend microservices
│   ├── customer-service/
│   ├── restaurant-service/
│   ├── availability-service/
│   ├── reservation-service/
│   ├── waiting-list-service/
│   ├── notification-service/
│   └── analytics-service/
├── common/                                   # Shared contracts
│   └── event-contracts/
├── infrastructure/                           # Docker Compose & Keycloak
└── ui/                                       # [DELETED] Retired redundant folder
```

**Structure Decision**: Retain `gateway/src/main/resources/static/ui/` as the single canonical location. Remove `ui/` completely.

---

## Complexity Tracking

> *No constitutional violations. No additional complexity introduced.*

| Violation | Why Needed | Simpler Alternative Rejected Because |
| :--- | :--- | :--- |
| None | N/A | N/A |
