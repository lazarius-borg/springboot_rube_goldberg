# Feature Specification: Fix JWT Issuer Validation Across Deployment Environments

**Feature Branch**: `006-fix-jwt-issuer-validation`

**Created**: 2026-09-14

**Status**: Draft

**Input**: User description: "troubleshoot jwt - There is a problem with the JWT tokens that are issued. For example, using the restaurant service swagger, when getting a token for the manager (with username manager1) and authorizing via the swagger UI, although the token is still valid, and the user is declared as authorized, when trying to access an endpoint, for example '/api/v1/restaurants' endpoint, the service responds with 401 and error message 'www-authenticate: Bearer error=\"invalid_token\",error_description=\"An error occurred while attempting to decode the Jwt: The iss claim is not valid\",error_uri=\"https://tools.ietf.org/html/rfc6750#section-3.1\",resource_metadata=\"http://localhost:8083/.well-known/oauth-protected-resource\"'. Same happens with the rest of the endpoints, I used this one as an example. I don't know why it is saying that the iss is invalid when it is present in the JWT payload section."

## Clarifications

### Session 2026-09-14

- Q: How should the microservices validate the token issuer (`iss`) claim across local and containerized environments? → A: Option A with Spring profiles: Implement dual-issuer validation in Spring Security accepting both external (`http://localhost:8081/realms/rube-goldberg`) and internal (`http://keycloak:8080/realms/rube-goldberg`) issuers, configurable and enabled cleanly via Spring profiles (e.g., default/local and docker environments).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Authenticate Protected Operations with Valid Identity Tokens (Priority: P1)

As an authorized user (such as a restaurant manager, customer, or administrator),
I want to use my issued authentication token to access protected service operations (both directly and via interactive documentation interfaces),
so that my requests are accepted and processed without token issuer validation failures.

**Why this priority**: Without reliable token acceptance, all protected business operations, portal actions, and interactive API exploration workflows fail completely with 401 Unauthorized errors despite presenting legitimate credentials.

**Independent Test**: Can be tested by obtaining a valid authentication token for `manager1` from the identity provider and submitting a request to a protected endpoint (e.g. `/api/v1/restaurants` or `/api/v1/customers/me`) via Swagger UI or command line, verifying that the request succeeds with an authorized response (HTTP 200) instead of an "invalid_token: The iss claim is not valid" rejection.

**Acceptance Scenarios**:

1. **Given** an authenticated user holding an active token issued by the identity provider, **When** the user submits a request to a protected service endpoint via interactive API documentation (Swagger UI), **Then** the service verifies the token successfully and processes the requested operation.
2. **Given** an authenticated user holding an active token, **When** the user submits an authorized command-line request to a domain service endpoint, **Then** the service accepts the token and returns the expected business payload rather than an HTTP 401 token decoding failure.
3. **Given** an unauthenticated request or a request with an expired/tampered token, **When** the request arrives at a protected endpoint, **Then** the service rejects the request with an appropriate authentication challenge.

---

### User Story 2 - Uniform Issuer Acceptance Across Deployment Topologies (Priority: P2)

As a developer or platform operator,
I want the token verification rules to align with how clients and services reach the identity provider (whether via local machine addresses or internal network addresses),
so that tokens minted for external clients remain valid when evaluated by services operating across different deployment environments.

**Why this priority**: Development and containerized stacks frequently use different network paths (e.g. external browser port mapping vs internal service discovery). Reconciling issuer expectations ensures seamless local development and container execution without fragile workarounds.

**Independent Test**: Can be tested by running the service suite in a multi-container environment, acquiring an authentication token from the external identity provider URL, and executing requests against multiple distinct microservices, verifying that all services consistently accept the token.

**Acceptance Scenarios**:

1. **Given** a multi-container deployment where the identity provider is reachable externally via an exposed port and internally via service networking, **When** an external client acquires a token and presents it to any protected service, **Then** all services recognize the issuer as trusted.
2. **Given** multiple services validating tokens from the same security realm, **When** verifying tokens, **Then** every protected service enforces identical, consistent issuer validation criteria.

---

### Edge Cases

- **Token from Unknown / Untrusted Provider**: If a token is presented with an issuer claiming an unrecognized domain or foreign realm, the service must continue to strictly reject it.
- **Expired Token with Matching Issuer**: If a token has a valid and recognized issuer claim but its expiration timestamp has elapsed, the service must report token expiration rather than issuer rejection.
- **Missing Issuer Claim**: If a token lacks an `iss` claim entirely, the service must reject the token as malformed.
- **Role Mismatch vs Issuer Rejection**: An authenticated user lacking required privileges (e.g. `customer1` trying to execute an admin-only or manager-only endpoint) must receive an HTTP 403 Forbidden response, not an HTTP 401 Invalid Token error.
- **Identity Provider Temporary Unavailability or Timeout**: If the identity provider or JWKS key endpoint is temporarily unreachable during token validation (e.g. initial discovery or key refresh timeout), services MUST reject incoming requests gracefully with appropriate error status (HTTP 503 or 401) without leaking internal network details or stack traces.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Services MUST accept and validate authentication tokens whose issuer identity matches either the external identity provider address (`http://localhost:8081/realms/rube-goldberg`) or internal container address (`http://keycloak:8080/realms/rube-goldberg`) via dual-issuer validation.
- **FR-002**: Token issuer validation rules and identity provider connection endpoints MUST be configurable cleanly using Spring profiles across local development and multi-container deployments.
- **FR-003**: The interactive API documentation (Swagger UI) MUST enable authorized users to execute secured operations using issued tokens without encountering token decoding errors.
- **FR-004**: Protected services MUST continue enforcing cryptographic signature verification against the identity provider's published key set.
- **FR-005**: All domain services in the platform (`restaurant-service`, `customer-service`, `availability-service`, `reservation-service`, `waiting-list-service`, `gateway`) MUST maintain consistent token verification behavior.
- **FR-006**: When rejecting invalid tokens, services MUST return standard authentication challenge headers with informative diagnostic details distinguishing issuer mismatches, expiration, and missing credentials.
- **FR-007**: Services MUST gracefully handle identity provider communication failures and timeouts (e.g. during JWKS key set resolution) by returning appropriate error responses without leaking internal network details or hanging request threads.

### Key Entities *(include if feature involves data)*

- **Authentication Token**: A cryptographically signed security token containing identity claims (subject, username, email), authorization roles (e.g. `CUSTOMER`, `RESTAURANT_MANAGER`, `ADMIN`), timestamps (issued-at, expiration), and the issuer URI (`iss`).
- **Identity Realm**: The security boundary defined by the identity provider that manages user credentials, roles, client applications, and cryptographic signing keys.
- **Resource Service**: A backend microservice exposing protected business APIs that verifies token signatures, issuer authenticity, and caller roles before fulfilling requests.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of non-expired authentication tokens acquired via the documented identity provider workflow are accepted by all protected service endpoints without token issuer errors.
- **SC-002**: Authorized users can authenticate and successfully execute operations in Swagger UI on the first attempt without manual token tampering or configuration workarounds.
- **SC-003**: All protected microservices in the platform exhibit identical authentication verification outcomes for tokens issued to `customer1`, `manager1`, and `admin1`.
- **SC-004**: Zero security regressions: tokens with untrusted signatures, expired timestamps, or unapproved realms continue to be rejected with 100% reliability.

## Assumptions

- The identity provider realm `rube-goldberg` and client `rube-goldberg-app` remain the standard authorization source.
- Seeded test accounts (`customer1`, `manager1`, `admin1` with password `password`) remain available for verification.
- Services may run either directly on the host machine or within containerized environments (Docker Compose).
- The solution will not weaken security checks (such as disabling signature verification or allowing arbitrary untrusted issuers).
