# Quickstart & Validation Guide: Keycloak Token Documentation

**Feature**: `005-document-keycloak-token` | **Date**: 2026-09-13

This guide provides runnable verification steps to validate the newly added README instructions and anchor link integrity.

---

## 1. Prerequisites

- Supporting infrastructure running (or Keycloak specifically):
  ```bash
  docker compose -f infrastructure/docker-compose.yml up -d keycloak
  ```
- Keycloak is healthy and listening on `http://localhost:8081`.

---

## 2. Validation Scenarios

### Scenario A: Obtain Customer Token (CLI One-Liner)
```bash
export TOKEN=$(curl -s -X POST "http://localhost:8081/realms/rube-goldberg/protocol/openid-connect/token" \
  -d "client_id=rube-goldberg-app" \
  -d "grant_type=password" \
  -d "username=customer1" \
  -d "password=password" | jq -r .access_token)

echo "Token length: ${#TOKEN}"
```
**Expected Outcome**: A non-empty JWT token string is printed (typically ~800-1200 characters).

---

### Scenario B: Verify Token Against Protected Microservice Endpoint
```bash
curl -s -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer $TOKEN" http://localhost:8082/api/v1/customers/me
```
**Expected Outcome**: Returns `200` (or `404`/`400` if customer record not yet created, but strictly NOT `401 Unauthorized`).

---

### Scenario C: Validate README Anchor Link Integrity
Inspect the markdown link in Step 3 under "How to Use Swagger UI for Manual Inspection":
- Target anchor: `#obtaining-a-keycloak-access-token`
- Section heading in README: `### Obtaining a Keycloak Access Token`
- GitHub markdown renders heading `### Obtaining a Keycloak Access Token` with id `obtaining-a-keycloak-access-token`.

Verify via grep:
```bash
grep -n "### Obtaining a Keycloak Access Token" README.md
grep -n "#obtaining-a-keycloak-access-token" README.md
```
**Expected Outcome**: Both patterns match cleanly, confirming the link points directly to the existing section.
