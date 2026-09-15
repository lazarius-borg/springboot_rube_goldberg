# Feature Specification: Public Access to OpenAPI and Swagger UI Endpoints

**Feature Branch**: `004-fix-swagger-endpoints`

**Created**: 2026-09-13

**Status**: Draft

**Input**: User description: "swagger endpoints - SpringBoot aplication definition classes of the services are all annotated with 'OpenAPIDefinition' annotations, but the swagger endpoints specified in the README return 401. The README doesn't describe any step for authentication in the 'How to Use Swagger UI for Manual Inspection' section if one is required to access the swagger endpoints. If that is the reason for the 401, then update the README, otherwise locate the source for the 401 and resolve the problem."

## Clarifications

### Session 2026-09-13

- Q: Which endpoint categories should be permitted without authentication in microservices that configure Spring Security? → A: Permit strictly Swagger/OpenAPI paths (`/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`) without authentication; require authentication for `/actuator/**` and all business API routes.
- Q: Should Swagger UI include an "Authorize" button supporting Bearer JWT tokens for invoking protected endpoints via the "Try it out" interface? → A: Configure an HTTP Bearer JWT security scheme in the OpenAPI definition so Swagger UI provides an "Authorize" button for passing access tokens during "Try it out" execution.
- Q: Should the API Gateway also route Swagger and OpenAPI documentation requests to downstream services, or should developers continue accessing Swagger UI directly on each service's individual port? → A: Keep direct port access per the README (`http://localhost:<PORT>/swagger-ui.html`); do not add gateway routing rules for documentation.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Inspect API Documentation via Swagger UI Without Authentication (Priority: P1)

As a developer, API consumer, or tester, I want to access the interactive Swagger UI and raw OpenAPI specification documents for any running microservice without needing authentication credentials, so that I can inspect service capabilities, available endpoints, and data schemas directly on each service's port as documented in the README.

**Why this priority**: Discoverability and inspection of service APIs is a core developer workflow. Currently, accessing `/swagger-ui.html` or `/v3/api-docs` on secured microservices returns HTTP 401 Unauthorized, completely blocking manual inspection and API discovery.

**Independent Test**: Can be fully tested by starting any secured microservice (e.g., Restaurant Service, Reservation Service, Customer Service, Availability Service, Waiting List Service) and issuing HTTP GET requests to `/swagger-ui.html`, `/swagger-ui/index.html`, and `/v3/api-docs` on its respective port without an Authorization header, verifying an HTTP 200 response with valid HTML or JSON content.

**Acceptance Scenarios**:

1. **Given** a running microservice with OpenAPI enabled, **When** a user navigates to `/swagger-ui.html` or `/swagger-ui/index.html` on the service's port without providing authentication headers, **Then** the service responds with HTTP 200 and serves the interactive Swagger UI web interface.
2. **Given** a running microservice with OpenAPI enabled, **When** a user or automated tool requests `/v3/api-docs` or `/v3/api-docs/swagger-config` on the service's port without providing authentication headers, **Then** the service responds with HTTP 200 and returns the OpenAPI specification.
3. **Given** the Swagger UI interface loaded in a web browser, **When** the browser requests static UI assets (CSS, JavaScript, web fonts, favicon) under the Swagger web path, **Then** the service responds with HTTP 200 for all required assets without authentication challenges.

---

### User Story 2 - Maintain API Protection for Secured Business Endpoints (Priority: P2)

As a platform security engineer, I want public access to be strictly restricted to API documentation and discovery endpoints, so that protected business endpoints (such as reservation creation, customer data management, and table allocation) and system management endpoints continue to enforce authentication and authorization policies.

**Why this priority**: Permitting access to API documentation must not compromise the security posture of sensitive business transactions, customer data, and operational actuator endpoints.

**Independent Test**: Can be fully tested by issuing unauthenticated requests to protected endpoints (e.g., `POST /api/v1/reservations`) or actuator paths on the service and confirming that HTTP 401 Unauthorized is still returned, while unauthenticated requests to documentation endpoints succeed.

**Acceptance Scenarios**:

1. **Given** a secured microservice, **When** an unauthenticated request is made to a protected business endpoint, **Then** the service rejects the request with HTTP 401 Unauthorized.
2. **Given** a secured microservice, **When** an unauthenticated request is made to an actuator endpoint, **Then** the service rejects the request with HTTP 401 Unauthorized.
3. **Given** a secured microservice, **When** a request with a valid authorization token is made to a protected business endpoint, **Then** the service accepts and processes the request according to authorization rules.

---

### User Story 3 - Interactive Testing via Swagger UI "Authorize" Button and Clear README Documentation (Priority: P3)

As a developer using Swagger UI for manual testing, I want the Swagger UI interface to include an "Authorize" modal for entering Bearer JWT tokens, and the README to document the authentication workflow, so that I can seamlessly execute both public and protected endpoints directly from the browser.

**Why this priority**: The README highlights using Swagger UI's "Try it out" feature. Without a security scheme configured in Swagger UI, developers cannot supply required tokens to execute secured endpoints.

**Independent Test**: Can be verified by opening Swagger UI in a browser on a service's port, confirming the presence of the "Authorize" button, applying a Bearer token, and executing a secured endpoint to receive a successful response. Additionally, verify that the README section details this workflow.

**Acceptance Scenarios**:

1. **Given** Swagger UI for a secured service, **When** inspected in the browser, **Then** an "Authorize" button is available supporting Bearer token input.
2. **Given** a Bearer token entered in the "Authorize" dialog, **When** a user executes a secured endpoint via "Try it out", **Then** the request includes the `Authorization: Bearer <token>` header and is processed successfully.
3. **Given** the project README, **When** a developer reads the Swagger UI inspection section, **Then** it clearly explains that documentation is publicly visible without credentials, and explains how to obtain and apply a Bearer token via the "Authorize" button when invoking secured operations.

---

### Edge Cases

- **Web Browser Redirects**: When a user navigates to `/swagger-ui.html`, the documentation engine redirects to `/swagger-ui/index.html`. Both the initial redirect URL and the target UI paths must be publicly accessible.
- **Actuator Endpoints Protected**: Microservice actuator endpoints (e.g., `/actuator/**`) remain guarded behind authentication on individual services.
- **Services Without Security Starters**: Microservices that do not include security components (such as analytics service) must continue to serve Swagger UI without regression.
- **Direct Service Access**: Swagger UI access remains direct per microservice port (`localhost:8082`–`8087`); gateway routing for documentation endpoints is explicitly out of scope.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Microservices exposing OpenAPI documentation MUST permit unauthenticated access to interactive documentation web paths (`/swagger-ui.html`, `/swagger-ui/**`).
- **FR-002**: Microservices exposing OpenAPI documentation MUST permit unauthenticated access to OpenAPI schema endpoints (`/v3/api-docs`, `/v3/api-docs/**`).
- **FR-003**: Microservices with security configurations MUST require authentication for all other requests, including actuator endpoints and domain business API routes.
- **FR-004**: Security authorization rules across microservices MUST be consistently defined across all affected services without duplicating or fragmenting security policies.
- **FR-005**: Microservices declaring protected endpoints MUST define an HTTP Bearer JWT security scheme in their OpenAPI definitions, exposing the "Authorize" button in Swagger UI.
- **FR-006**: The project README MUST accurately document that Swagger UI and OpenAPI endpoints are publicly accessible for manual inspection on their respective service ports, and provide guidance on obtaining and applying tokens via the "Authorize" button for protected API executions.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of microservices exposing Swagger UI respond with HTTP 200 to unauthenticated requests for `/swagger-ui/index.html` and `/v3/api-docs`.
- **SC-002**: Zero security regressions on protected business API endpoints and actuator endpoints; 100% of unauthenticated requests to those endpoints continue to return HTTP 401 Unauthorized.
- **SC-003**: The "Authorize" dialog is available and functional in Swagger UI across 100% of secured services supporting token-based invocation.
- **SC-004**: 100% automated test pass rate across all unit and integration test suites for affected services.
- **SC-005**: The project documentation accurately reflects the Swagger UI access flow with zero missing authentication prerequisites for viewing schemas.

## Assumptions

- Microservices that include security dependencies utilize OAuth2 Resource Server with JWT validation.
- Permitting unauthenticated access strictly to Swagger UI assets and OpenAPI metadata conforms to the desired developer exploration experience described in the project README.
- Actuator endpoints remain protected under standard authentication policies on individual services.
- Developers access Swagger UI directly on each service's individual port as defined in the README.
