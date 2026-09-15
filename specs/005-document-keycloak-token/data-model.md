# Data Model: Keycloak Token Authentication & Pre-Seeded Identities

**Feature**: `005-document-keycloak-token` | **Date**: 2026-09-13

## 1. Keycloak Realm Identity Entities

| Username | Default Password | Realm Role | Description | Target Use Case |
| :--- | :--- | :--- | :--- | :--- |
| `customer1` | `password` | `CUSTOMER` | Regular dining customer (Alice Customer) | Customer profile, making reservations, viewing personal bookings |
| `manager1` | `password` | `RESTAURANT_MANAGER` | Restaurant staff/manager (Bob Manager) | Restaurant configuration, table allocation, managing waiting lists |
| `admin1` | `password` | `ADMIN` | System administrator (Carol Admin) | Platform-wide administration and management |

---

## 2. Keycloak Token Request Schema

| Field | Type | Required | Value / Example | Description |
| :--- | :--- | :---: | :--- | :--- |
| `client_id` | string | Yes | `rube-goldberg-app` | Keycloak client configured with direct access grants |
| `grant_type` | string | Yes | `password` | OAuth2 Resource Owner Password Credentials grant |
| `username` | string | Yes | `customer1` / `manager1` / `admin1` | Seeded user identifier |
| `password` | string | Yes | `password` | User password |

---

## 3. Keycloak Token Response Schema

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expires_in": 300,
  "refresh_expires_in": 1800,
  "refresh_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "not-before-policy": 0,
  "session_state": "12345678-1234-1234-1234-1234567890ab",
  "scope": "openid email profile"
}
```

### Essential JWT Claims in `access_token`
- `iss`: `http://localhost:8081/realms/rube-goldberg` (or `http://keycloak:8080/realms/rube-goldberg` in Docker network)
- `sub`: Keycloak user UUID
- `preferred_username`: `customer1` / `manager1` / `admin1`
- `realm_access.roles`: `["CUSTOMER"]`, `["RESTAURANT_MANAGER"]`, or `["ADMIN"]`
