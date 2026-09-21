# Contract: Gateway Web Portal Static Serving Contract

**Feature**: `019-consolidate-ui-assets`  
**Date**: 2026-09-18  

---

## 1. Overview

This contract specifies the HTTP interface exposed by the API Gateway (`gateway`) for serving static single-page application assets to client browsers.

---

## 2. Endpoints & Response Contracts

### 2.1 Customer Portal Shell
- **Method**: `GET`
- **Path**: `/ui/customer/index.html`
- **Headers**:
  - `Accept`: `text/html`
- **Responses**:
  - `200 OK`:
    - `Content-Type`: `text/html;charset=UTF-8`
    - Body: Contains HTML structure with Bootstrap 5 navbar, `#authGate`, `#customerPortal`, and script references:
      ```html
      <script src="keycloak.js"></script>
      <script src="app.js"></script>
      ```

### 2.2 Customer Portal Application Script
- **Method**: `GET`
- **Path**: `/ui/customer/app.js`
- **Responses**:
  - `200 OK`:
    - `Content-Type`: `application/javascript` (or `text/javascript`)
    - Body: Customer SPA logic (Keycloak PKCE initialization, SSE consumer, reservation form).

### 2.3 Customer Keycloak JS Adapter
- **Method**: `GET`
- **Path**: `/ui/customer/keycloak.js`
- **Responses**:
  - `200 OK`:
    - `Content-Type`: `application/javascript`
    - Body: Keycloak client adapter script (v26.1.0).

---

### 2.4 Manager Portal Shell
- **Method**: `GET`
- **Path**: `/ui/manager/index.html`
- **Headers**:
  - `Accept`: `text/html`
- **Responses**:
  - `200 OK`:
    - `Content-Type`: `text/html;charset=UTF-8`
    - Body: Contains HTML structure with manager tabs (Live Analytics, Reservations, Floor & Tables, Availability & Waiting List, Settings) and script references:
      ```html
      <script src="keycloak.js"></script>
      <script src="app.js"></script>
      ```

### 2.5 Manager Portal Application Script
- **Method**: `GET`
- **Path**: `/ui/manager/app.js`
- **Responses**:
  - `200 OK`:
    - `Content-Type`: `application/javascript`
    - Body: Manager SPA logic (Keycloak manager role check, analytics refresh, table combinations, schedules).

### 2.6 Manager Keycloak JS Adapter
- **Method**: `GET`
- **Path**: `/ui/manager/keycloak.js`
- **Responses**:
  - `200 OK`:
    - `Content-Type`: `application/javascript`
    - Body: Keycloak client adapter script (v26.1.0).

---

### 2.7 Non-Existent Asset Handling
- **Method**: `GET`
- **Path**: `/ui/**` (any unmapped path, e.g. `/ui/missing.html`, `/ui/customer/unknown.js`)
- **Responses**:
  - `404 Not Found`:
    - Body: Standard Spring Boot WebFlux error response or empty body, with zero classpath / filesystem path leakage.

---

## 3. Security & Access Control Contract

- **Public Static Access**: The Spring Cloud Gateway security configuration MUST allow unauthenticated `GET` requests to `/ui/**`.
- **Client-Side Auth Gating**: Security is initiated client-side by `keycloak.js` redirecting unauthenticated users to Keycloak's login flow (`/realms/rube-goldberg/protocol/openid-connect/auth`).
- **Server-Side Protected APIs**: API calls made by `app.js` to `/api/v1/**` are secured via Bearer tokens passed through Gateway routing to the individual resource servers.

---

## 4. Performance & Latency Contract

- **Local Latency**: Static asset requests served from classpath (`static/ui/`) MUST complete with a response latency under 10ms (p95) under standard JVM execution.
- **Container Latency**: Under nominal containerized execution within Docker, static asset response time MUST remain under 50ms (p99).
