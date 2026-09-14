# RBAC Endpoint Security Contracts

**Feature**: `007-role-endpoint-access`  
**Protocol**: HTTP / OAuth 2.0 Resource Server (JWT Bearer)

---

## Global Authentication & Authorization Contract

All domain microservices act as OAuth 2.0 Resource Servers validating JWT tokens minted by Keycloak realm `rube-goldberg`.

### HTTP Request Header
```http
Authorization: Bearer <JWT_ACCESS_TOKEN>
```

### Security Error Responses

#### 1. Unauthenticated (401 Unauthorized)
Returned when no bearer token is supplied, or the token is expired, malformed, or issued by an untrusted issuer.
```http
HTTP/1.1 401 Unauthorized
WWW-Authenticate: Bearer error="invalid_token", error_description="..."
```

#### 2. Unauthorized Role (403 Forbidden)
Returned when the caller has a valid token but lacks the requisite role in `realm_access.roles`.
```http
HTTP/1.1 403 Forbidden
Content-Type: application/json
```

---

## Service Contracts

### 1. Restaurant Service (`port: 8083`)

#### `POST /api/v1/restaurants`
- **Description**: Register new restaurant establishment.
- **Required Role**: `RESTAURANT_MANAGER` OR `ADMIN`
- **Authorized Status**: `201 Created`
- **Forbidden Status**: `403 Forbidden` for `CUSTOMER`

#### `POST /api/v1/restaurants/{id}/tables`
- **Description**: Add dining table.
- **Required Role**: `RESTAURANT_MANAGER` OR `ADMIN`
- **Authorized Status**: `201 Created`
- **Forbidden Status**: `403 Forbidden` for `CUSTOMER`

#### `POST /api/v1/restaurants/{id}/table-combinations`
- **Description**: Add combinable table definition.
- **Required Role**: `RESTAURANT_MANAGER` OR `ADMIN`
- **Authorized Status**: `201 Created`
- **Forbidden Status**: `403 Forbidden` for `CUSTOMER`

#### `PUT /api/v1/restaurants/{id}/opening-hours`
- **Description**: Configure restaurant opening hours.
- **Required Role**: `RESTAURANT_MANAGER` OR `ADMIN`
- **Authorized Status**: `200 OK`
- **Forbidden Status**: `403 Forbidden` for `CUSTOMER`

#### `GET /api/v1/restaurants/**` (Catalog & Availability details)
- **Description**: Public catalog listings, restaurant details, tables, opening hours.
- **Required Role**: `CUSTOMER` OR `RESTAURANT_MANAGER` OR `ADMIN`
- **Authorized Status**: `200 OK`

---

### 2. Availability Service (`port: 8084`)

#### `GET /api/v1/availability`
- **Description**: Real-time table availability check across party sizes.
- **Required Role**: `CUSTOMER` OR `RESTAURANT_MANAGER` OR `ADMIN`
- **Authorized Status**: `200 OK`

---

### 3. Reservation Service (`port: 8085`)

#### `POST /api/v1/reservations`
- **Description**: Book a guaranteed table reservation.
- **Required Role**: `CUSTOMER`
- **Authorized Status**: `201 Created`
- **Forbidden Status**: `403 Forbidden` for callers lacking `CUSTOMER` (e.g., pure `RESTAURANT_MANAGER` or pure `ADMIN`)

#### `GET /api/v1/reservations/{id}`
- **Description**: Retrieve individual reservation details.
- **Required Role**: `CUSTOMER` OR `RESTAURANT_MANAGER` OR `ADMIN`
- **Authorized Status**: `200 OK`

#### `DELETE /api/v1/reservations/{id}`
- **Description**: Cancel individual reservation within allowed policy window.
- **Required Role**: `CUSTOMER` OR `RESTAURANT_MANAGER` OR `ADMIN`
- **Authorized Status**: `200 OK`

#### `GET /api/v1/reservations` (Query param: `restaurantId`)
- **Description**: List all reservations for a restaurant.
- **Required Role**: `RESTAURANT_MANAGER` OR `ADMIN`
- **Authorized Status**: `200 OK`
- **Forbidden Status**: `403 Forbidden` for `CUSTOMER`

#### `PATCH /api/v1/reservations/{id}/status`
- **Description**: Update reservation status (`ARRIVED`, `COMPLETED`, `NO_SHOW`, `CANCELLED`).
- **Required Role**: `RESTAURANT_MANAGER` OR `ADMIN`
- **Authorized Status**: `200 OK`
- **Forbidden Status**: `403 Forbidden` for `CUSTOMER`

---

### 4. Waiting List Service (`port: 8086`)

#### `POST /api/v1/waiting-list`
- **Description**: Join FIFO waiting list for cancellation openings.
- **Required Role**: `CUSTOMER`
- **Authorized Status**: `201 Created`
- **Forbidden Status**: `403 Forbidden` for pure `RESTAURANT_MANAGER` or pure `ADMIN`

#### `POST /api/v1/waiting-list/offers/{offerId}/accept`
- **Description**: Accept time-limited cancellation offer.
- **Required Role**: `CUSTOMER`
- **Authorized Status**: `200 OK`
- **Forbidden Status**: `403 Forbidden` for pure `RESTAURANT_MANAGER` or pure `ADMIN`

---

### 5. Customer Service (`port: 8082`)

#### `GET /api/v1/customers/me` & `PUT /api/v1/customers/me`
- **Description**: Retrieve or update authenticated customer profile.
- **Required Role**: `CUSTOMER`
- **Authorized Status**: `200 OK`
- **Forbidden Status**: `403 Forbidden` for pure `RESTAURANT_MANAGER` or pure `ADMIN`

---

### 6. Analytics Service (`port: 8087`)

#### `GET /api/v1/analytics/**` (including `/summary`)
- **Description**: Aggregated KPIs, reservation counts, cancellation rates.
- **Required Role**: `RESTAURANT_MANAGER` OR `ADMIN`
- **Authorized Status**: `200 OK`
- **Forbidden Status**: `403 Forbidden` for `CUSTOMER`

---

### 7. Documentation & Diagnostic Endpoints (All Services)

#### `GET /swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`
- **Description**: Interactive OpenAPI documentation.
- **Required Role**: None (`permitAll`)
- **Authorized Status**: `200 OK`
