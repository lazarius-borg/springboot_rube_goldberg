# Feature Specification: Consolidate Frontend UI Assets into Gateway

**Feature Branch**: `019-consolidate-ui-assets`  
**Created**: 2026-09-18  
**Status**: Draft  
**Input**: User description: "Consolidate frontend UI assets into gateway and eliminate redundant top-level ui directory"

---

## Clarifications

### Session 2026-09-18
- Q: Should references to the removed `ui/src/` directory be updated only in active living documentation (such as `README.md`), or should completed historical specification artifacts (`specs/001`–`017`) also be modified? → A: Update only active living documentation (`README.md` and root guides); preserve completed historical spec files as archival records.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Single Canonical Frontend Asset Location in Gateway (Priority: P1)

As a developer and system operator, I want all web portal frontend assets (Customer Portal and Restaurant Manager Portal) to reside exclusively in `gateway/src/main/resources/static/ui/`, so that there is a single canonical source of truth for UI assets, avoiding synchronization overhead and drift risk.

**Why this priority**: Eliminating file duplication directly addresses technical debt and prevents bugs where changes made in one directory fail to reflect in the active running application.

**Independent Test**: Verify that editing or inspecting frontend files in `gateway/src/main/resources/static/ui/` directly updates what is served by Spring Cloud Gateway at `http://localhost:8080/ui/customer/index.html` and `http://localhost:8080/ui/manager/index.html`.

**Acceptance Scenarios**:

1. **Given** the consolidated project structure, **When** examining the source repository, **Then** all Customer Portal assets (`index.html`, `app.js`, `keycloak.js`) and Manager Portal assets (`index.html`, `app.js`, `keycloak.js`) reside in `gateway/src/main/resources/static/ui/`.
2. **Given** the gateway application running locally or in Docker, **When** requesting `http://localhost:8080/ui/customer/index.html` or `http://localhost:8080/ui/manager/index.html`, **Then** the browser receives the static assets with HTTP 200 and loads all scripts without resource resolution errors.

---

### User Story 2 - Retirement and Safe Removal of Top-Level `ui/` Directory (Priority: P2)

As a repository maintainer, I want the unused top-level `ui/` directory safely deleted from the codebase, so that the project directory structure is clean, unambiguous, and free from obsolete files.

**Why this priority**: Having a phantom directory at the repository root that has no build lifecycle or deployment role confuses developers and automated tools.

**Independent Test**: Check that `ui/` no longer exists in the filesystem, git status reports its clean removal, and running `./mvnw clean test` and `./mvnw package jib:dockerBuild -pl gateway -DskipTests` completes with zero build errors.

**Acceptance Scenarios**:

1. **Given** the removal of the redundant directory, **When** inspecting the root filesystem and git tracking, **Then** the `ui/` folder is completely absent from the repository.
2. **Given** the removed `ui/` folder, **When** executing the Maven build lifecycle and container image packaging, **Then** all Maven reactor builds complete successfully without missing directory warnings or errors.

---

### User Story 3 - Repository Documentation and Reference Alignment (Priority: P3)

As a developer or contributor reviewing the project, I want the `README.md` and repository developer documentation to accurately reflect the unified `gateway/src/main/resources/static/ui/` structure, so that instructions and architecture diagrams are completely aligned.

**Why this priority**: Documentation accuracy ensures new contributors and automated agents know exactly where to make frontend updates.

**Independent Test**: Perform a repository-wide search for references to `ui/src/` in active living documentation and confirm all active documentation points directly to `gateway/src/main/resources/static/ui/`.

**Acceptance Scenarios**:

1. **Given** updated project documentation, **When** reading active living documentation (such as `README.md`), **Then** references to web UI locations refer exclusively to `gateway/src/main/resources/static/ui/` (or the gateway web portal URL `http://localhost:8080/ui/...`).
2. **Given** completed historical specification artifacts (`specs/001` through `specs/018`), **When** reviewing past feature records, **Then** those files remain untouched as archival historical documents.

---

### Edge Cases

- **Asset Packaging in Gateway**: Deleting `ui/` must not affect gateway artifact creation since `ui/` was never part of Maven reactor or Jib configurations.
- **Relative Path Resolution**: Relative links within `index.html` (such as `<script src="keycloak.js"></script>` and `<script src="app.js"></script>`) must remain functional when loaded through Gateway reverse proxy.
- **Missing Asset (404) Handling**: Any request for non-existent static paths under `/ui/**` (e.g. `/ui/nonexistent.html`) must cleanly return HTTP 404 Not Found via Spring Cloud Gateway's default resource handler without 500 error or stack traces.
- **Authentication & Security Filters**: The Spring Cloud Gateway security filter chain must continue allowing unauthenticated access to `/ui/**` static resources while protecting backend API routes appropriately.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The project MUST maintain all Customer Portal and Manager Portal static web assets (`index.html`, `app.js`, `keycloak.js`) exclusively in `gateway/src/main/resources/static/ui/`.
- **FR-002**: The project MUST remove the top-level `ui/` directory and all its contents (`ui/src/customer/`, `ui/src/manager/`) from git tracking and the filesystem.
- **FR-003**: The API Gateway MUST continue serving the Customer Portal at `/ui/customer/index.html` and the Manager Portal at `/ui/manager/index.html` with HTTP 200 status.
- **FR-004**: Active living documentation (`README.md` and root developer guides) MUST be updated to eliminate references to `ui/src/` and designate `gateway/src/main/resources/static/ui/` as the single canonical location; historical completed feature specifications (`specs/001`–`018`) remain untouched as archival records.
- **FR-005**: All Maven reactor builds (`./mvnw clean test`) and container builds (`./mvnw package jib:dockerBuild`) MUST pass cleanly without warnings or failures resulting from the removal of `ui/`.
- **FR-006**: The API Gateway MUST return standard HTTP 404 Not Found for any requested static assets or subpaths under `/ui/**` that do not exist on the classpath, without leaking underlying filesystem paths.
- **FR-007**: The API Gateway MUST deliver static assets with a response latency under 10ms for local JVM execution and under 50ms at 99th percentile under nominal local container load.

---

### Key Entities

- **Static Portal Assets**: Client-side single-page application files (`HTML`, `JavaScript`, and `Keycloak` OIDC adapter) providing interactive user interfaces for dining customers and restaurant managers.
- **Gateway Web Resource Root**: The classpath resource location (`static/ui/`) inside the Spring Cloud Gateway service that automatically delivers web assets to client browsers.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of frontend static assets are maintained in a single canonical location (`gateway/src/main/resources/static/ui/`), with zero redundant duplicate files remaining in the repository.
- **SC-002**: 100% of web portal HTTP requests (`/ui/customer/index.html`, `/ui/manager/index.html`) served by API Gateway return HTTP 200 with zero missing asset errors (404).
- **SC-003**: Zero references to the deprecated `ui/src/` path remain in active living documentation (`README.md`).
- **SC-004**: 100% pass rate on full Maven reactor test suite (`./mvnw test`) and successful container image compilation following directory removal.

---

## Assumptions

- The existing files in `gateway/src/main/resources/static/ui/` are already 100% byte-for-byte identical to `ui/src/`, meaning no code migration or synchronization is needed prior to deletion.
- Neither Dockerfile, Docker Compose, Maven POMs, nor Kubernetes configurations reference the top-level `ui/` folder.
- Frontend portal functionality, endpoints, role authorization, and styling remain completely unchanged.
