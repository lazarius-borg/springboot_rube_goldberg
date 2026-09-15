# Feature Specification: Document Keycloak Access Token Acquisition in README

**Feature Branch**: `005-document-keycloak-token`

**Created**: 2026-09-13

**Status**: Ready for Planning

**Input**: User description: "keycloak access token - The README instructions on obtaining keycloak access token in step 3 under \"How to Use Swagger UI for Manual Inspection\" point to 'option-a-local-development-docker-compose-infrastructure--local-java-services'. It should point to README section where obtaining keycloack access tokent is described, which itself is missing. The missing info should be added and the link should be updated to point to the correct info."

## Clarifications

### Session 2026-09-13
- Q: What format should the README provide for the Keycloak token acquisition commands? → A: Option A (Provide a primary one-liner using `curl` + `jq` to extract the raw token directly into an environment variable, along with raw `curl` fallback and examples for `customer1`, `manager1`, and `admin1`).
- Q: Should the token documentation include a quick verification `curl` example demonstrating how to invoke a protected API endpoint with the Bearer token? → A: Option A (Include a quick CLI verification example, e.g., `curl -H "Authorization: Bearer $TOKEN" http://localhost:8082/api/v1/customers/me`, showing successful 200 OK response with profile data).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Obtain Keycloak Access Token via README Instructions (Priority: P1)

As a developer testing microservice APIs via Swagger UI or command-line tools, I want a clear, copy-pasteable guide in the README showing how to request a Keycloak JWT access token for each user role, so that I can authenticate and invoke protected endpoints without guessing credentials, grant types, or endpoint URLs.

**Why this priority**: Without documented instructions on obtaining a valid JWT token, developers cannot authenticate via Swagger UI's "Authorize" dialog or execute secured endpoints described in the project walkthrough.

**Independent Test**: A developer copies the documented command, executes it against a running Keycloak instance, and receives a valid JWT access token that unlocks protected microservice endpoints.

**Acceptance Scenarios**:

1. **Given** Keycloak running locally via Docker Compose, **When** a developer executes the documented token acquisition command for `customer1`, **Then** Keycloak returns a 200 OK response with a valid JWT `access_token` containing the `CUSTOMER` realm role.
2. **Given** Keycloak running locally via Docker Compose, **When** a developer executes the documented token acquisition command for `manager1` or `admin1`, **Then** Keycloak returns a valid JWT `access_token` containing the respective `RESTAURANT_MANAGER` or `ADMIN` realm role.
3. **Given** the project README, **When** a reader navigates to the Keycloak security section, **Then** it clearly provides endpoint details, client ID, grant type, credentials, and one-liner token extraction commands.
4. **Given** a valid JWT access token acquired per the instructions, **When** a developer executes the verification `curl` command against `GET http://localhost:8082/api/v1/customers/me`, **Then** the service responds with HTTP 200 OK and customer profile JSON.

---

### User Story 2 - Accurate Link from Swagger UI Instructions to Token Documentation (Priority: P2)

As a developer reading the "How to Use Swagger UI for Manual Inspection" section in the README, I want the link in Step 3 to navigate directly to the section explaining how to obtain a Keycloak access token, so that I am not redirected to an irrelevant section.

**Why this priority**: Broken or misleading navigation links degrade documentation credibility and create unnecessary friction during developer onboarding.

**Independent Test**: Clicking the link in Step 3 under "How to Use Swagger UI for Manual Inspection" jumps directly to the newly added token acquisition subsection.

**Acceptance Scenarios**:

1. **Given** Step 3 under "How to Use Swagger UI for Manual Inspection" in the README, **When** a developer clicks the `[Keycloak Access Token]` link, **Then** the browser viewport scrolls to the exact heading where token acquisition commands are documented.
2. **Given** the README document, **When** validating anchor target identifiers, **Then** the target section heading matches the anchor href without dead links.

---

### Edge Cases

- **Keycloak not yet started or still initializing**: The documentation notes that Keycloak must be running and healthy (`docker compose -f infrastructure/docker-compose.yml up -d`).
- **Developer environment lacks `jq`**: The documentation provides both a raw `curl` request and an optional `jq` pipeline to extract only the raw token string.
- **Token expiration**: The documentation clarifies that JWT tokens have a time-limited validity and mentions how to re-issue the command when tokens expire.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The README MUST include a dedicated subsection titled `### Obtaining a Keycloak Access Token` under `## 🔐 Keycloak Security Configuration`.
- **FR-002**: The token acquisition documentation MUST document the OpenID Connect token endpoint URL (`http://localhost:8081/realms/rube-goldberg/protocol/openid-connect/token`), realm name (`rube-goldberg`), client ID (`rube-goldberg-app`), and grant type (`password`).
- **FR-003**: The documentation MUST provide copy-pasteable CLI commands to obtain tokens for all three seeded accounts: `customer1`, `manager1`, and `admin1`.
- **FR-004**: The documentation MUST provide a primary one-liner using `curl` + `jq` to extract the raw `access_token` directly into a shell environment variable (`export TOKEN=...`), alongside a fallback raw `curl` snippet showing the complete JSON response payload.
- **FR-005**: Step 3 in the Swagger UI inspection section (`README.md`) MUST update its markdown hyperlink from `#option-a-local-development-docker-compose-infrastructure--local-java-services` to `#obtaining-a-keycloak-access-token`.
- **FR-006**: The documentation MUST explain how to paste the acquired token into Swagger UI's "Authorize" modal.
- **FR-007**: The documentation MUST include a CLI verification example demonstrating how to call a protected microservice endpoint (e.g., `GET http://localhost:8082/api/v1/customers/me`) with the Bearer token in the `Authorization` header to confirm authorization.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of internal anchor links in the Swagger UI inspection section resolve to existing, active anchor headings in the README.
- **SC-002**: A developer following the README instructions can obtain an access token and authenticate in Swagger UI in under 1 minute.
- **SC-003**: Zero documentation-related 401/404 issues reported when testing secured endpoints per the updated README guide.

## Assumptions

- Keycloak is exposed on port `8081` on localhost as defined in `infrastructure/docker-compose.yml`.
- The `rube-goldberg` realm and `rube-goldberg-app` client allow the direct access grant (`grant_type=password`) as configured in `infrastructure/keycloak/realm-export.json`.
- The pre-seeded credentials `customer1` / `password`, `manager1` / `password`, and `admin1` / `password` are present in the realm export.
