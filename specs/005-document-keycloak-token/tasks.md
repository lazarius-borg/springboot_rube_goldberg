# Tasks: Document Keycloak Access Token Acquisition in README

**Branch**: `005-document-keycloak-token` | **Date**: 2026-09-13 | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Baseline verification of Keycloak realm configuration and README anchors.

- [X] T001 Verify Keycloak realm configuration in infrastructure/keycloak/realm-export.json matches documented client and credentials

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Verify target sections and anchor structure in README.md.

**⚠️ CRITICAL**: Must complete before user story updates begin.

- [X] T002 Verify section headings and anchor hierarchy in README.md around Keycloak Security Configuration and Swagger UI exploration

**Checkpoint**: Baseline verified - documentation updates can proceed.

---

## Phase 3: User Story 1 - Obtain Keycloak Access Token via README Instructions (Priority: P1) 🎯 MVP

**Goal**: Provide clear, copy-pasteable instructions in `README.md` for acquiring Keycloak JWT access tokens via Direct Access Grants for all seeded user accounts (`customer1`, `manager1`, `admin1`) with CLI verification.

**Independent Test**: Execute the documented `curl` + `jq` command against Keycloak and verify a valid JWT token is returned, then run the verification command against `customer-service` (`GET /api/v1/customers/me`) receiving HTTP 200 OK.

### Implementation for User Story 1

- [X] T003 [US1] Add ### Obtaining a Keycloak Access Token subsection with token endpoint details, curl + jq export one-liner, role credentials (customer1, manager1, admin1), fallback raw curl snippet, and verification request in README.md under ## 🔐 Keycloak Security Configuration

**Checkpoint**: User Story 1 complete - developers can obtain and verify Keycloak JWT access tokens directly from README instructions.

---

## Phase 4: User Story 2 - Accurate Link from Swagger UI Instructions to Token Documentation (Priority: P2)

**Goal**: Fix the broken anchor link in Step 3 of "How to Use Swagger UI for Manual Inspection" so it navigates directly to the newly added token acquisition subsection.

**Independent Test**: Inspect the hyperlink target in `README.md` and verify it points to `#obtaining-a-keycloak-access-token` without dead links.

### Implementation for User Story 2

- [X] T004 [US2] Update Step 3 anchor link under ### How to Use Swagger UI for Manual Inspection in README.md to point to #obtaining-a-keycloak-access-token

**Checkpoint**: User Story 2 complete - documentation navigation is accurate and seamless.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Final link validation and execution of quickstart verification scenarios.

- [X] T005 Validate markdown anchor link integrity and formatting in README.md
- [X] T006 Execute quickstart.md validation scenarios

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately.
- **Foundational (Phase 2)**: Depends on Phase 1 - verifies README structure.
- **User Story 1 (Phase 3)**: Depends on Phase 2 - adds token acquisition documentation (MVP).
- **User Story 2 (Phase 4)**: Depends on Phase 3 - links Swagger UI section to the new heading.
- **Polish (Phase 5)**: Depends on Phase 4 - validates anchors and runs quickstart scenarios.

### User Story Dependencies

- **User Story 1 (P1)**: Independent of User Story 2.
- **User Story 2 (P2)**: Depends on User Story 1 creating the target heading `### Obtaining a Keycloak Access Token`.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Setup (T001) and Foundational (T002).
2. Complete User Story 1 (T003).
3. Validate token acquisition commands work against Keycloak.

### Incremental Delivery

1. Deliver User Story 1 (token instructions).
2. Deliver User Story 2 (link fix).
3. Validate end-to-end via Polish phase.
