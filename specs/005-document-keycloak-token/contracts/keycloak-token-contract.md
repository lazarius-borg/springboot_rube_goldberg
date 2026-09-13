# Contract Specification: Keycloak Token Endpoint & Usage

**Feature**: `005-document-keycloak-token` | **Date**: 2026-09-13

## 1. Token Acquisition HTTP Request Contract

### Endpoint
`POST http://localhost:8081/realms/rube-goldberg/protocol/openid-connect/token`

### Headers
- `Content-Type: application/x-www-form-urlencoded`

### Request Body
```
client_id=rube-goldberg-app&grant_type=password&username=<USERNAME>&password=<PASSWORD>
```

### CLI One-Liner (with jq)
```bash
export TOKEN=$(curl -s -X POST "http://localhost:8081/realms/rube-goldberg/protocol/openid-connect/token" \
  -d "client_id=rube-goldberg-app" \
  -d "grant_type=password" \
  -d "username=customer1" \
  -d "password=password" | jq -r .access_token)
```

### Response Status
- `HTTP/1.1 200 OK`
- `Content-Type: application/json`

---

## 2. API Authorization Request Contract

### Endpoint Example
`GET http://localhost:8082/api/v1/customers/me`

### Headers
- `Authorization: Bearer <access_token>`

### CLI Invocation
```bash
curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8082/api/v1/customers/me
```

### Response Status
- With valid token: `HTTP/1.1 200 OK`
- Without token or invalid token: `HTTP/1.1 401 Unauthorized` (`WWW-Authenticate: Bearer`)

---

## 3. Swagger UI Authorization Contract

1. Open `http://localhost:<PORT>/swagger-ui.html`
2. Click **Authorize** button (lock icon) at the top right.
3. In the `bearerAuth` dialog, enter `<access_token>` (without the `Bearer ` prefix).
4. Click **Authorize**, then **Close**.
5. Requests executed via **Try it out** will include `Authorization: Bearer <access_token>`.
