# Implementation Plan: Document Keycloak Access Token Acquisition in README

**Branch**: `005-document-keycloak-token` | **Date**: 2026-09-13 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/005-document-keycloak-token/spec.md`

## Summary

Add comprehensive, copy-pasteable instructions to `README.md` for acquiring Keycloak JWT access tokens via Direct Access Grants (`grant_type=password`) for all seeded user accounts (`customer1`, `manager1`, `admin1`). Fix the broken anchor link in Step 3 under "How to Use Swagger UI for Manual Inspection" so it directly navigates to the newly added token acquisition subsection (`#obtaining-a-keycloak-access-token`). Include a CLI verification example demonstrating token authentication against a protected microservice endpoint.

## Technical Context

**Language/Version**: Markdown (GFM), Shell (cURL, jq)  
**Primary Dependencies**: Keycloak OIDC server (port 8081), `rube-goldberg` realm, `rube-goldberg-app` client  
**Storage**: N/A (Documentation update)  
**Testing**: cURL / bash validation scenarios against local Keycloak instance; Markdown link validation  
**Target Platform**: GitHub / Markdown viewer / Terminal  
**Project Type**: Documentation & Developer Ergonomics  
**Performance Goals**: N/A  
**Constraints**: Pure documentation update; maintain consistency with `realm-export.json` and existing README style  
**Scale/Scope**: `README.md` (`## 🔐 Keycloak Security Configuration` and `### How to Use Swagger UI for Manual Inspection`)  

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Documentation Accuracy**: All documented URLs, endpoints, usernames, passwords, and client IDs strictly match the actual system configuration in `infrastructure/keycloak/realm-export.json`.
- **Zero Disruption**: No code changes to Spring Boot microservices, security filters, or infrastructure compose files.
- **Link Integrity**: All internal anchor references must match generated heading slugs without dead links.

## Project Structure

### Documentation (this feature)

```text
specs/005-document-keycloak-token/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output: Token endpoint, grant type, and link design
├── data-model.md        # Phase 1 output: Keycloak identities, request/response schema, and claims
├── quickstart.md        # Phase 1 output: Validation scenarios and curl testing
├── contracts/           # Phase 1 output: Keycloak token endpoint and API authorization contract
│   └── keycloak-token-contract.md
└── tasks.md             # Phase 2 output (/speckit-tasks command)
```

### Affected Files (repository root)

```text
README.md                # Updated Keycloak security section and Swagger UI inspection link
```

## Implementation Strategy

1. **Section 1: Add Token Acquisition Guide to README.md**:
   - Add subsection `### Obtaining a Keycloak Access Token` under `## 🔐 Keycloak Security Configuration`.
   - Document the token URL: `http://localhost:8081/realms/rube-goldberg/protocol/openid-connect/token`.
   - Provide the primary `curl` + `jq` one-liner with `export TOKEN=...` for `customer1`.
   - Provide command variations for `manager1` and `admin1`.
   - Provide a fallback raw `curl` snippet showing the JSON response format.
   - Provide a verification `curl` command using `Authorization: Bearer $TOKEN` against `customer-service` (`http://localhost:8082/api/v1/customers/me`).
2. **Section 2: Fix Anchor Link in Swagger UI Section**:
   - Update Step 3 in `### How to Use Swagger UI for Manual Inspection` from:
     `(see [Keycloak Access Token](#option-a-local-development-docker-compose-infrastructure--local-java-services))`
     to:
     `(see [Keycloak Access Token](#obtaining-a-keycloak-access-token))`.
3. **Section 3: Verification**:
   - Verify anchor link syntax and heading id alignment.
   - Run quickstart validation scenarios.
