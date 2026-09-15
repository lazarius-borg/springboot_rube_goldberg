# Research: Role-Based Access Control (RBAC) & Endpoint Authorization

**Feature**: `007-role-endpoint-access`  
**Date**: 2026-09-14  

---

## 1. Keycloak Role Extraction & Spring Security 6 Mapping

### Context & Problem
In standard Keycloak tokens generated for realm `rube-goldberg`, assigned realm roles (`CUSTOMER`, `RESTAURANT_MANAGER`, `ADMIN`) are located in the JWT payload under the `realm_access` claim:
```json
{
  "sub": "b2f69ce8-...",
  "iss": "http://localhost:8081/realms/rube-goldberg",
  "preferred_username": "customer1",
  "realm_access": {
    "roles": [
      "CUSTOMER",
      "default-roles-rube-goldberg",
      "offline_access",
      "uma_authorization"
    ]
  }
}
```
By default, Spring Security's `JwtAuthenticationConverter` only processes the `scope` or `scp` claim and maps them to `SCOPE_<name>`. It does not parse `realm_access.roles`, resulting in empty authorities and failing `.hasRole(...)` checks.

### Decision
Implement a `KeycloakRealmRoleConverter` (implementing `Converter<Jwt, Collection<GrantedAuthority>>`) that extracts `realm_access.roles`, filters or converts all string role names by prefixing them with `ROLE_`, and yields `SimpleGrantedAuthority("ROLE_" + roleName)`.

To also preserve any default scope-based authorities (if present), the converter combines default authorities from `JwtGrantedAuthoritiesConverter` with the mapped Keycloak realm roles.

```java
public class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
    private final JwtGrantedAuthoritiesConverter defaultAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>(defaultAuthoritiesConverter.convert(jwt));
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.containsKey("roles")) {
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) realmAccess.get("roles");
            if (roles != null) {
                roles.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .forEach(authorities::add);
            }
        }
        return authorities;
    }
}
```

In `SecurityConfig.java`:
```java
@Bean
public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
    return converter;
}
```
And wire it into the resource server configuration:
```java
.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
```

### Alternatives Considered
1. **Method-Level Security (`@PreAuthorize`)**:
   - *Evaluated*: Adding `@PreAuthorize("hasRole('CUSTOMER')")` to controller methods.
   - *Rejected*: Requires annotating individual methods across multiple controllers; less centralized than declarative `requestMatchers` in `SecurityFilterChain`, and harder to test cleanly with mock filter chains in isolation.
2. **Keycloak Spring Boot Adapter**:
   - *Evaluated*: The legacy `keycloak-spring-boot-starter`.
   - *Rejected*: Deprecated by Red Hat; Spring Security 6 native OAuth2 Resource Server with Nimbus JWT decoder is the official standard.

---

## 2. Service-Specific Endpoint Authorization Matrix

### Decision
Define explicit HTTP method and path matchers in `SecurityFilterChain` for each microservice according to the clarified spec:

| Service | Port | Endpoint Pattern | HTTP Method | Allowed Roles | Spring Security Matcher |
|---|---|---|---|---|---|
| **restaurant-service** | 8083 | `/api/v1/restaurants` | POST | `RESTAURANT_MANAGER`, `ADMIN` | `.requestMatchers(HttpMethod.POST, "/api/v1/restaurants").hasAnyRole("RESTAURANT_MANAGER", "ADMIN")` |
| | | `/api/v1/restaurants/*/tables` | POST | `RESTAURANT_MANAGER`, `ADMIN` | `.requestMatchers(HttpMethod.POST, "/api/v1/restaurants/*/tables").hasAnyRole("RESTAURANT_MANAGER", "ADMIN")` |
| | | `/api/v1/restaurants/*/table-combinations` | POST | `RESTAURANT_MANAGER`, `ADMIN` | `.requestMatchers(HttpMethod.POST, "/api/v1/restaurants/*/table-combinations").hasAnyRole("RESTAURANT_MANAGER", "ADMIN")` |
| | | `/api/v1/restaurants/*/opening-hours` | PUT | `RESTAURANT_MANAGER`, `ADMIN` | `.requestMatchers(HttpMethod.PUT, "/api/v1/restaurants/*/opening-hours").hasAnyRole("RESTAURANT_MANAGER", "ADMIN")` |
| | | `/api/v1/restaurants/**` | GET | `CUSTOMER`, `RESTAURANT_MANAGER`, `ADMIN` | `.requestMatchers(HttpMethod.GET, "/api/v1/restaurants/**").hasAnyRole("CUSTOMER", "RESTAURANT_MANAGER", "ADMIN")` |
| **reservation-service** | 8085 | `/api/v1/reservations` | POST | `CUSTOMER` | `.requestMatchers(HttpMethod.POST, "/api/v1/reservations").hasRole("CUSTOMER")` |
| | | `/api/v1/reservations/{id}` | GET | `CUSTOMER`, `RESTAURANT_MANAGER`, `ADMIN` | `.requestMatchers(HttpMethod.GET, "/api/v1/reservations/*").hasAnyRole("CUSTOMER", "RESTAURANT_MANAGER", "ADMIN")` |
| | | `/api/v1/reservations/{id}` | DELETE | `CUSTOMER`, `RESTAURANT_MANAGER`, `ADMIN` | `.requestMatchers(HttpMethod.DELETE, "/api/v1/reservations/*").hasAnyRole("CUSTOMER", "RESTAURANT_MANAGER", "ADMIN")` |
| | | `/api/v1/reservations` | GET (list for restaurant) | `RESTAURANT_MANAGER`, `ADMIN` | `.requestMatchers(HttpMethod.GET, "/api/v1/reservations").hasAnyRole("RESTAURANT_MANAGER", "ADMIN")` |
| | | `/api/v1/reservations/*/status` | PATCH | `RESTAURANT_MANAGER`, `ADMIN` | `.requestMatchers(HttpMethod.PATCH, "/api/v1/reservations/*/status").hasAnyRole("RESTAURANT_MANAGER", "ADMIN")` |
| **waiting-list-service**| 8086 | `/api/v1/waiting-list/**` | POST | `CUSTOMER` | `.requestMatchers(HttpMethod.POST, "/api/v1/waiting-list/**").hasRole("CUSTOMER")` |
| **customer-service** | 8082 | `/api/v1/customers/**` | GET, PUT | `CUSTOMER` | `.requestMatchers("/api/v1/customers/**").hasRole("CUSTOMER")` |
| **availability-service**| 8084 | `/api/v1/availability/**` | GET | `CUSTOMER`, `RESTAURANT_MANAGER`, `ADMIN` | `.requestMatchers(HttpMethod.GET, "/api/v1/availability/**").hasAnyRole("CUSTOMER", "RESTAURANT_MANAGER", "ADMIN")` |
| **analytics-service** | 8087 | `/api/v1/analytics/**` | GET | `RESTAURANT_MANAGER`, `ADMIN` | `.requestMatchers("/api/v1/analytics/**").hasAnyRole("RESTAURANT_MANAGER", "ADMIN")` |

All services maintain:
- `.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()`
- `.anyRequest().authenticated()`

---

## 3. Securing Analytics Service

### Context
`services/analytics-service` currently does not have Spring Security or OAuth2 Resource Server enabled. It serves `/api/v1/analytics/summary` publicly or without token inspection.

### Decision
1. Add `spring-boot-starter-oauth2-resource-server` to `services/analytics-service/pom.xml`.
2. Introduce `SecurityConfig.java` and `JwtMultiIssuerValidator.java` in `nl.invokedynamic.demo.analytics.config`, consistent with all other services.
3. Configure dual-issuer validation (`docker` profile vs `!docker` fallback) preserving existing platform conventions.
4. Add OpenApi `@SecurityScheme` configuration to `nl.invokedynamic.demo.analytics.config.OpenApiConfig` if not already present.
5. Create `AnalyticsSecurityTest` validating unauthenticated (401), unauthorized customer (403), and authorized manager/admin access, as well as Swagger UI permitAll.

---

## 4. Documentation & Walkthrough Updates (`README.md`)

### Context
The current walkthrough targets `http://localhost:8080/...` (the API Gateway) without explicit tokens, and without showing which role performs which action.

### Decision
1. Add a dedicated **Role-Based Access Control (RBAC)** section to `README.md` defining:
   - **`CUSTOMER`**: Public discovery, table availability lookup, reservation creation, waitlist entry, offer acceptance, profile management, and individual reservation lookup/cancellation. Forbidden from administrative restaurant and inventory operations and platform analytics.
   - **`RESTAURANT_MANAGER`**: Restaurant registration, table inventory configuration, table combination definitions, operating hours management, restaurant reservation queries, reservation status transitions, and operational analytics. Forbidden from booking reservations or waitlists unless granted the `CUSTOMER` role.
   - **`ADMIN`**: Full administrative supervision matching manager capabilities across all restaurant inventory and operational analytics.
2. Update the **Interactive End-to-End Walkthrough** to:
   - Target direct microservice ports (`8083` Restaurant, `8084` Availability, `8085` Reservation, `8086` Waiting List, `8087` Analytics).
   - Show obtaining `$MANAGER_TOKEN` (`manager1`) and `$CUSTOMER_TOKEN` (`customer1`).
   - Use `$MANAGER_TOKEN` for Step 1 (Register Restaurant & Tables on `8083`), Step 7 (Analytics on `8087`).
   - Use `$CUSTOMER_TOKEN` for Step 2 & 4 (Availability on `8084`), Step 3 (Book Table on `8085`), Step 5 (Waiting List on `8086`), Step 6 (Cancel Reservation on `8085`).
