# Quickstart & Verification Guide: JWT Issuer Validation

**Feature**: `006-fix-jwt-issuer-validation` | **Date**: 2026-09-14

This guide provides step-by-step verification procedures to validate that token issuer validation operates seamlessly across both host machine access (Swagger UI / cURL) and containerized execution.

---

## 1. Automated Test Verification (Unit & Slice Tests)

Run the security test suite across the secured microservices:

```bash
mvn test -Dtest=*SecurityTest
```

Expected Outcome:
- Tests verify public documentation endpoints return non-401.
- Tests verify requests without tokens return 401.
- Tests verify tokens issued by `http://localhost:8081/realms/rube-goldberg` decode and validate cleanly.
- Tests verify tokens issued by `http://keycloak:8080/realms/rube-goldberg` decode and validate cleanly.
- Tests verify tokens with unauthorized issuers (e.g. `http://malicious.org`) are rejected with `The iss claim is not valid`.

---

## 2. End-to-End Verification with Docker Stack

### Step 1: Start Infrastructure & Applications
```bash
docker compose -f infrastructure/docker-compose.yml up -d
docker compose -f infrastructure/docker-compose.apps.yml up -d restaurant-service
```

### Step 2: Acquire Token from Host
```bash
export TOKEN=$(curl -s -X POST "http://localhost:8081/realms/rube-goldberg/protocol/openid-connect/token" \
  -d "client_id=rube-goldberg-app" \
  -d "grant_type=password" \
  -d "username=manager1" \
  -d "password=password" | jq -r .access_token)
```

### Step 3: Invoke Protected Endpoint on Restaurant Service
```bash
curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8083/api/v1/restaurants
```

**Expected Outcome**:
- Status: `HTTP/1.1 200 OK`
- Header `www-authenticate` is NOT returned.
- Response body contains restaurant JSON array.

### Step 4: Verify Swagger UI
1. Open `http://localhost:8083/swagger-ui.html`.
2. Click **Authorize**, paste `$TOKEN`, and click **Authorize**.
3. Execute `GET /api/v1/restaurants`.
4. Receive `200 OK` with JSON response.
