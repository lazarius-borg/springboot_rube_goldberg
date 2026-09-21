# Research: Consolidate Frontend UI Assets into Gateway

**Feature**: `019-consolidate-ui-assets`  
**Date**: 2026-09-18  
**Status**: Completed  

---

## 1. Technical Context & Investigation Summary

### Background
Currently, the codebase contains two identical copies of frontend static assets:
1. `ui/src/` (located at repository root)
2. `gateway/src/main/resources/static/ui/` (located in the `gateway` Maven submodule)

### Parity Verification
A recursive binary diff (`diff -r ui/src gateway/src/main/resources/static/ui`) was performed across all files:
- `customer/index.html` (identical)
- `customer/app.js` (identical)
- `customer/keycloak.js` (identical)
- `manager/index.html` (identical)
- `manager/app.js` (identical)
- `manager/keycloak.js` (identical)

**Result**: 0 differences. The files are 100% byte-for-byte identical.

### Runtime Asset Resolution Analysis
- In Spring Boot WebFlux / Spring Cloud Gateway, resources located in `classpath:/static/` are automatically mapped and served by the embedded server.
- The `gateway` submodule packages `gateway/src/main/resources/static/` into its executable JAR and into `/app/resources/static/` in Jib container images.
- When accessed via `http://localhost:8080/ui/customer/index.html` or `http://localhost:8080/ui/manager/index.html`, Spring Cloud Gateway resolves these directly from its classpath.
- The root `ui/` directory has no build script (`package.json`, `pom.xml`), is not part of the Maven reactor, and is never referenced in Docker, Docker Compose, or Kubernetes.

---

## 2. Technical Decisions & Rationale

### Decision 1: Establish `gateway/src/main/resources/static/ui/` as the Sole Canonical Location
- **Chosen**: Maintain all customer and manager frontend assets strictly under `gateway/src/main/resources/static/ui/`.
- **Rationale**:
  - Spring Cloud Gateway is the actual service delivering the web portals to browsers.
  - Colocating static assets within the gateway submodule follows standard Spring Boot web conventions (`src/main/resources/static/`).
  - Completely eliminates the manual synchronization burden that was previously executed in specs `014`, `015`, `016`, and `017`.
- **Alternatives Considered**:
  - *Automating file synchronization via `maven-resources-plugin` from `ui/src` into `gateway` target*: Rejected because it adds unnecessary Maven configuration and build complexity for 6 static web files that require no build transpilation.
  - *Extracting `ui/` into a separate Nginx or Node container*: Rejected as out of scope; the architectural pattern established in the platform uses the API Gateway as the unified host for static assets and reverse proxying.

### Decision 2: Complete Deletion of Root `ui/` Directory
- **Chosen**: Remove `ui/` from the repository and git tracking (`git rm -rf ui`).
- **Rationale**:
  - Eliminates dead code and ambiguity for human developers and AI assistants.
  - Leaves the repository root clean (only `pom.xml`, `gateway/`, `services/`, `common/`, `infrastructure/`, `k8s/`, and `specs/`).
- **Alternatives Considered**:
  - *Leaving a placeholder README in `ui/`*: Rejected; a redirect README still leaves an obsolete directory in the root tree. Clean removal is standard practice.

### Decision 3: Documentation Alignment Scope
- **Chosen**: Update active living documentation (`README.md`), removing references to `ui/` or `ui/src/` and pointing to `gateway/src/main/resources/static/ui/`. Completed historical specifications (`specs/001` through `specs/018`) remain untouched.
- **Rationale**:
  - In accordance with Clarification Session 2026-09-18, historical specification files serve as archival records of past development sprints and should not undergo retrofitted git churn.
  - Only active developer-facing guides (`README.md`) need to reflect the current repository state.
- **Alternatives Considered**:
  - *Mass regex search-and-replace across all `specs/`*: Rejected due to risk of noisy git history and breaking archival traceability.

### Decision 4: Build and Runtime Verification Plan
- **Chosen**:
  1. Validate directory removal via `git status` and filesystem checks.
  2. Verify reactor compilation and testing via `./mvnw clean test`.
  3. Verify container packaging via `./mvnw package jib:dockerBuild -pl gateway -DskipTests`.
  4. Verify HTTP 200 response on `http://localhost:8080/ui/customer/index.html` and `http://localhost:8080/ui/manager/index.html` with correct MIME types (`text/html`, `application/javascript`).
- **Rationale**: Ensures zero regression across local builds, automated tests, containerization, and browser delivery.
