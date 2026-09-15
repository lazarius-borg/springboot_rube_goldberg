# Research: Document Keycloak Access Token Acquisition in README

**Feature**: `005-document-keycloak-token` | **Date**: 2026-09-13

## Technical Decisions & Rationale

### 1. Keycloak Token Endpoint & OAuth2 Grant Type
- **Decision**: Use OAuth2 Resource Owner Password Credentials (`grant_type=password`) against `http://localhost:8081/realms/rube-goldberg/protocol/openid-connect/token` with `client_id=rube-goldberg-app`.
- **Rationale**:
  - `realm-export.json` explicitly enables `directAccessGrantsEnabled: true` and `publicClient: true` for the `rube-goldberg-app` client.
  - Unlike Authorization Code flow with PKCE, password grant allows direct, instantaneous command-line execution without requiring an interactive browser callback or local redirect server.
  - Matches the developer ergonomics needed when using Swagger UI's "Authorize" modal.
- **Alternatives Considered**:
  - *Authorization Code Grant*: Requires browser redirect flow, unsuitable for terminal `curl` one-liners.
  - *Client Credentials Grant*: Not enabled on `rube-goldberg-app` and would issue service account tokens rather than user role tokens (`CUSTOMER`, `RESTAURANT_MANAGER`, `ADMIN`).

---

### 2. Token Extraction and Shell Export Pattern
- **Decision**: Document a primary one-liner using `curl` and `jq` to extract the `access_token` directly into `export TOKEN=...`, supplemented by a raw `curl` snippet showing the complete JSON response payload.
- **Rationale**:
  - Developers can copy-paste a single line into their terminal to export `$TOKEN` for immediate use with `curl` or copy to the clipboard for Swagger UI.
  - Showing the raw JSON response helps developers without `jq` understand the structure and locate the token manually.
- **Alternatives Considered**:
  - *Python/Node script*: Adds unnecessary toolchain dependencies.
  - *Only raw curl*: Requires manual JSON parsing and manual copying of lengthy JWT strings.

---

### 3. Immediate Token Verification Command
- **Decision**: Include a quick verification request against `customer-service`:
  ```bash
  curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8082/api/v1/customers/me
  ```
- **Rationale**:
  - Provides instant confirmation that the token was signed correctly by Keycloak and validated by Spring Security's OAuth2 Resource Server.
  - Proves the distinction between unauthenticated (401) and authenticated (200) requests before testing in Swagger UI.
- **Alternatives Considered**:
  - *Verifying with JWT CLI (jwt.io)*: Requires external tool/site; does not test actual microservice validation.

---

### 4. Anchor Link Resolution
- **Decision**: Update step 3 of "How to Use Swagger UI for Manual Inspection" to reference `#obtaining-a-keycloak-access-token`.
- **Rationale**:
  - GitHub Markdown generates heading anchors by lowercasing and replacing spaces with hyphens. The heading `### Obtaining a Keycloak Access Token` automatically produces the anchor `#obtaining-a-keycloak-access-token`.
  - Fixes the current broken link (`#option-a-local-development-docker-compose-infrastructure--local-java-services`).
