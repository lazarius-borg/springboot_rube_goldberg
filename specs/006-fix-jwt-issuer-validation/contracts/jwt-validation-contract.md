# Contract Specification: JWT Token Authorization & Validation

**Feature**: `006-fix-jwt-issuer-validation` | **Date**: 2026-09-14

## 1. Request Contract

Any protected business endpoint requires an HTTP Bearer authorization header.

### Headers
```http
Authorization: Bearer <access_token>
```

---

## 2. Response Contracts

### 2.1 Success (Authorized)
- **Status**: `200 OK` (or `201 Created`, etc.)
- **Body**: Business payload JSON.

### 2.2 Rejection: Missing Token
- **Status**: `401 Unauthorized`
- **Headers**:
  ```http
  WWW-Authenticate: Bearer
  ```

### 2.3 Rejection: Invalid Issuer (Untrusted Realm / Origin)
- **Status**: `401 Unauthorized`
- **Headers**:
  ```http
  WWW-Authenticate: Bearer error="invalid_token", error_description="The iss claim is not valid. Expected one of: [http://localhost:8081/realms/rube-goldberg, http://keycloak:8080/realms/rube-goldberg]", error_uri="https://tools.ietf.org/html/rfc6750#section-3.1"
  ```

### 2.4 Rejection: Expired Token
- **Status**: `401 Unauthorized`
- **Headers**:
  ```http
  WWW-Authenticate: Bearer error="invalid_token", error_description="Jwt is expired", error_uri="https://tools.ietf.org/html/rfc6750#section-3.1"
  ```

### 2.5 Rejection: Insufficient Roles
- **Status**: `403 Forbidden`
- **Headers**:
  ```http
  WWW-Authenticate: Bearer error="insufficient_scope"
  ```

---

## 3. Swagger UI Interaction Contract

1. User opens `http://localhost:<PORT>/swagger-ui.html`.
2. User clicks **Authorize** and pastes the token acquired via host cURL (minted by `http://localhost:8081`).
3. User executes a protected endpoint (e.g. `GET /api/v1/restaurants`).
4. Service receives token with `iss: http://localhost:8081/realms/rube-goldberg`.
5. Service validates that `iss` is in `accepted-issuers` (`localhost:8081` and `keycloak:8080`).
6. Service returns `200 OK` with JSON data.
