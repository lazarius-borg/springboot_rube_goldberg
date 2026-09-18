# Implementation Tasks: Consolidate Frontend UI Assets into Gateway

**Feature**: `019-consolidate-ui-assets`  
**Date**: 2026-09-18  
**Spec**: [spec.md](file:///Users/lazolazarev/projects/springboot_rube_goldberg/specs/019-consolidate-ui-assets/spec.md) | **Plan**: [plan.md](file:///Users/lazolazarev/projects/springboot_rube_goldberg/specs/019-consolidate-ui-assets/plan.md)

---

## Phase 1: Setup (Parity & Baseline Validation)

**Purpose**: Validate asset parity and baseline integrity before making destructive changes.

- [X] T001 Verify byte-for-byte file parity between `ui/src/` and `gateway/src/main/resources/static/ui/` using recursive diff (`diff -r ui/src gateway/src/main/resources/static/ui`)
- [X] T002 [P] Verify presence and file integrity of all 6 canonical assets in `gateway/src/main/resources/static/ui/` (`customer/index.html`, `customer/app.js`, `customer/keycloak.js`, `manager/index.html`, `manager/app.js`, `manager/keycloak.js`)

---

## Phase 2: Foundational (Pre-Removal Build & Routing Gate)

**Purpose**: Confirm the baseline gateway application compiles and passes tests before retiring redundant assets.

**⚠️ CRITICAL**: Must complete before removing the `ui/` directory.

- [X] T003 Execute baseline gateway build and tests via `./mvnw test -pl gateway` to verify clean build state

**Checkpoint**: Parity confirmed, baseline builds passing — safe to proceed to consolidation.

---

## Phase 3: User Story 1 - Single Canonical Frontend Asset Location in Gateway (Priority: P1) 🎯 MVP

**Goal**: Establish `gateway/src/main/resources/static/ui/` as the sole canonical source of truth for both web portals, validating self-contained relative asset references.

**Independent Test**: Inspect `gateway/src/main/resources/static/ui/` and confirm that all relative references (`keycloak.js`, `app.js`) resolve locally within the gateway classpath without external references.

- [X] T004 [US1] Inspect and verify relative script and resource paths in `gateway/src/main/resources/static/ui/customer/index.html` to confirm local resolution of `keycloak.js` and `app.js`
- [X] T005 [P] [US1] Inspect and verify relative script and resource paths in `gateway/src/main/resources/static/ui/manager/index.html` to confirm local resolution of `keycloak.js` and `app.js`
- [X] T006 [US1] Verify Gateway static resource configuration in `gateway/src/main/resources/application.yml` ensuring `/ui/**` routes map directly to classpath static resources

**Checkpoint**: Gateway static assets verified as self-contained and ready to serve independently.

---

## Phase 4: User Story 2 - Retirement and Safe Removal of Top-Level `ui/` Directory (Priority: P2)

**Goal**: Delete the unreferenced top-level `ui/` directory from the repository and git tracking, ensuring zero build or packaging regressions.

**Independent Test**: Verify `ui/` does not exist on filesystem, `git status` reports its deletion, and `./mvnw clean test -pl gateway` succeeds without errors.

- [X] T007 [US2] Remove the redundant top-level `ui/` directory and all child files from git tracking and filesystem (`git rm -rf ui`)
- [X] T008 [US2] Verify `ui/` directory is completely absent from the filesystem and untracked in `git status`
- [X] T009 [US2] Execute `./mvnw clean test -pl gateway` to confirm the gateway submodule compiles and passes tests with `ui/` removed

**Checkpoint**: `ui/` directory permanently removed; gateway build verified clean.

---

## Phase 5: User Story 3 - Repository Documentation and Reference Alignment (Priority: P3)

**Goal**: Update active living documentation (`README.md`) to reflect the unified `gateway/src/main/resources/static/ui/` asset location while preserving historical feature specs as archival records.

**Independent Test**: Search for `ui/src/` in `README.md` and confirm zero occurrences remain.

- [X] T010 [US3] Update `README.md` to remove any references to `ui/src/` or the top-level `ui/` directory and designate `gateway/src/main/resources/static/ui/` as the canonical asset location
- [X] T011 [US3] Verify zero occurrences of `ui/src/` in active documentation via `grep -n "ui/src" README.md`

**Checkpoint**: Active documentation accurately reflects the single canonical frontend location.

---

## Phase 6: Polish & Cross-Cutting Validation

**Purpose**: Comprehensive reactor testing, container packaging verification, and quickstart execution.

- [X] T012 Run full Maven reactor build and test suite `./mvnw clean test` across all 10 modules to confirm 100% pass rate
- [X] T013 [P] Package Gateway container image via `./mvnw package jib:dockerBuild -pl gateway -DskipTests` and verify static assets exist in `/app/resources/static/ui/` inside the image
- [X] T014 Execute quickstart verification guide in `specs/019-consolidate-ui-assets/quickstart.md` to confirm end-to-end correctness

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Can start immediately (read-only verification).
- **Foundational (Phase 2)**: Depends on Phase 1 completion.
- **User Story 1 (Phase 3)**: Depends on Phase 2; validates gateway assets.
- **User Story 2 (Phase 4)**: Depends on Phase 3; executes deletion of `ui/`.
- **User Story 3 (Phase 5)**: Depends on Phase 4; aligns documentation post-deletion.
- **Polish (Phase 6)**: Depends on Phases 4 & 5; validates entire reactor build, container packaging, and quickstart.

### User Story Dependencies

- **User Story 1 (P1)**: Independent verification of gateway assets (blocks US2).
- **User Story 2 (P2)**: Depends on US1 (requires confirming gateway assets before deleting `ui/`).
- **User Story 3 (P3)**: Depends on US2 (updates docs after `ui/` is removed).

### Parallel Opportunities

- In Phase 1: `T002` can run in parallel with `T001`.
- In Phase 3: `T005` can run in parallel with `T004`.
- In Phase 6: `T013` can run in parallel with `T012`.

---

## Parallel Example: User Story 1

```bash
# Launch parallel asset verification tasks:
Task T004: "Inspect relative paths in gateway/src/main/resources/static/ui/customer/index.html"
Task T005: "Inspect relative paths in gateway/src/main/resources/static/ui/manager/index.html"
```

---

## Implementation Strategy

### MVP First (User Story 1 + User Story 2)
1. Complete Phase 1 (Parity verification) & Phase 2 (Baseline build).
2. Complete Phase 3 (Validate gateway assets).
3. Complete Phase 4 (Remove `ui/` and verify clean gateway build).
4. Validate MVP: repository has single canonical source, zero duplicates, clean gateway build.

### Incremental Delivery
1. Baseline verified (Phases 1 & 2)
2. Assets verified in gateway (Phase 3: US1)
3. Redundant folder removed (Phase 4: US2 - MVP!)
4. Documentation updated (Phase 5: US3)
5. Full reactor build, Jib container packaging, and quickstart validation (Phase 6: Polish)
