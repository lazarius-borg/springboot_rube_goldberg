# Quickstart & Validation Guide: OpenAPI and Swagger UI Endpoints

**Feature**: `004-fix-swagger-endpoints` | **Date**: 2026-09-13

This guide provides runnable scenarios to validate that Swagger UI and OpenAPI specification endpoints are publicly accessible across microservices, while domain endpoints remain properly secured.

---

## 1. Prerequisites

- **Java Version**: Java 26 preview (`JAVA_HOME=/Users/lazolazarev/.sdkman/candidates/java/current`)
- **Maven Wrapper**: `./mvnw` at project root
- **Docker Compose** (optional for live runtime testing): `docker compose -f infrastructure/docker-compose.yml up -d`

---

## 2. Validation Scenarios

### Scenario A: Automated Reactor Build & Test Suite
Run the full Maven reactor test suite to verify that all existing tests and new security slice/integration tests pass cleanly:

```bash
./mvnw clean test
```
**Expected Outcome**: `BUILD SUCCESS` across all 10 modules with zero test failures.

---

### Scenario B: Swagger UI Unauthenticated Access Verification (Live / Integration)
Start a domain service (e.g., `restaurant-service` on port 8083) or execute its security integration test:

```bash
# Verify Swagger UI HTML landing page responds with 200 / 302
curl -I http://localhost:8083/swagger-ui/index.html

# Verify raw OpenAPI specification schema responds with 200
curl -s http://localhost:8083/v3/api-docs | grep -o '"openapi":"3\.'
```
**Expected Outcome**:
- `HTTP/1.1 200 OK` for `/swagger-ui/index.html`.
- `HTTP/1.1 200 OK` for `/v3/api-docs` containing valid OpenAPI 3.x schema.

---

### Scenario C: Verify Security Enforcement on Protected Endpoints
Confirm that unauthenticated calls to business APIs and actuator endpoints continue to be rejected:

```bash
# Actuator health endpoint
curl -I http://localhost:8083/actuator/health

# Business API endpoint
curl -I -X POST http://localhost:8083/api/v1/restaurants
```
**Expected Outcome**:
- Both commands return `HTTP/1.1 401 Unauthorized` with `WWW-Authenticate: Bearer`.

---

### Scenario D: Verify Swagger UI "Authorize" Button in OpenAPI Schema
Verify that the `bearerAuth` security scheme is declared in the OpenAPI output:

```bash
curl -s http://localhost:8083/v3/api-docs | grep -o '"bearerAuth":{"type":"http"'
```
**Expected Outcome**: Match found (`"bearerAuth":{"type":"http"`).
