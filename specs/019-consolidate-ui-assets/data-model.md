# Data Model: Consolidated Frontend UI Assets and Gateway Static Routing

**Feature**: `019-consolidate-ui-assets`  
**Date**: 2026-09-18  

---

## 1. Static UI Asset Catalog

The frontend architecture consists of two single-page applications (SPAs) delivered directly from the API Gateway's classpath resource tree:

| Portal | Canonical Path | MIME Type | Purpose | External Dependencies |
| :--- | :--- | :--- | :--- | :--- |
| **Customer Portal** | `gateway/src/main/resources/static/ui/customer/index.html` | `text/html; charset=utf-8` | Single-page UI shell for diners | Bootstrap 5, Bootstrap Icons |
| | `gateway/src/main/resources/static/ui/customer/app.js` | `application/javascript` | Customer SPA logic, availability search, booking, waiting list, SSE | Keycloak JS, Gateway API routes |
| | `gateway/src/main/resources/static/ui/customer/keycloak.js` | `application/javascript` | Self-hosted Keycloak OIDC client adapter (v26.1.0) | Keycloak Realm (`rube-goldberg`) |
| **Manager Portal** | `gateway/src/main/resources/static/ui/manager/index.html` | `text/html; charset=utf-8` | Single-page UI shell for restaurant staff | Bootstrap 5, Bootstrap Icons |
| | `gateway/src/main/resources/static/ui/manager/app.js` | `application/javascript` | Manager SPA logic, table combinations, schedules, live waitlist, metrics | Keycloak JS, Gateway API routes |
| | `gateway/src/main/resources/static/ui/manager/keycloak.js` | `application/javascript` | Self-hosted Keycloak OIDC client adapter (v26.1.0) | Keycloak Realm (`rube-goldberg`) |

---

## 2. Gateway Static Resource Routing Model

Spring Cloud Gateway inherits Spring Boot WebFlux's static resource handler (`ResourceHandlerRegistry`).

```
Browser Request
      │
      ▼
┌───────────────────────────────────────────────┐
│ Spring Cloud Gateway (Port 8080)             │
│                                               │
│ Match /ui/** → Classpath: /static/ui/        │
└───────┬───────────────────────────────┬───────┘
        │                               │
        ▼                               ▼
/ui/customer/**                 /ui/manager/**
        │                               │
        ▼                               ▼
gateway/src/main/resources/     gateway/src/main/resources/
  static/ui/customer/             static/ui/manager/
```

### Path Mapping Table

| Public URL Path | Classpath Resource Location | Cache Control / Headers |
| :--- | :--- | :--- |
| `/ui/customer/index.html` | `classpath:/static/ui/customer/index.html` | `Content-Type: text/html` |
| `/ui/customer/app.js` | `classpath:/static/ui/customer/app.js` | `Content-Type: application/javascript` |
| `/ui/customer/keycloak.js` | `classpath:/static/ui/customer/keycloak.js` | `Content-Type: application/javascript` |
| `/ui/manager/index.html` | `classpath:/static/ui/manager/index.html` | `Content-Type: text/html` |
| `/ui/manager/app.js` | `classpath:/static/ui/manager/app.js` | `Content-Type: application/javascript` |
| `/ui/manager/keycloak.js` | `classpath:/static/ui/manager/keycloak.js` | `Content-Type: application/javascript` |

---

## 3. Directory Lifecycle & State Transition

### Before Consolidation
- Source 1: `ui/src/{customer,manager}/*` (Unmanaged duplicate)
- Source 2: `gateway/src/main/resources/static/ui/{customer,manager}/*` (Runtime active)
- State: Redundant, requires manual sync tasks in every feature.

### After Consolidation
- Source 1: `gateway/src/main/resources/static/ui/{customer,manager}/*` (Single canonical source)
- Root `ui/`: Deleted and untracked in Git.
- State: Unified, zero sync overhead, zero drift possibility.
