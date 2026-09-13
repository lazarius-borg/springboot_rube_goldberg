# Contract Specification: OpenAPI and Security Filter Chain

**Feature**: `004-fix-swagger-endpoints` | **Date**: 2026-09-13

## 1. SecurityFilterChain Contract

### Contract Definition
```java
package nl.invokedynamic.demo.<service>.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }
}
```

### Expected HTTP Responses

| Request | Expected Status | Response Body Content |
|:---|:---:|:---|
| `GET /swagger-ui.html` | `302 Found` or `200 OK` | Location header pointing to `/swagger-ui/index.html` or HTML content |
| `GET /swagger-ui/index.html` | `200 OK` | HTML containing Swagger UI assets |
| `GET /v3/api-docs` | `200 OK` | JSON containing OpenAPI 3.0 schema |
| `GET /actuator/health` | `401 Unauthorized` | Problem Details / WWW-Authenticate: Bearer |
| `POST /api/v1/<business-route>` | `401 Unauthorized` | Problem Details / WWW-Authenticate: Bearer |
| `POST /api/v1/<business-route>` with valid JWT | `200` / `201` / `400` / `404` | Standard domain response |

---

## 2. OpenAPI SecurityScheme Contract

### Contract Definition
```java
package nl.invokedynamic.demo.<service>.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
        info = @Info(
                title = "<Service Name> API",
                version = "1.0.0",
                description = "<Service Description>"
        ),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Keycloak JWT Bearer Token"
)
public class OpenApiConfig {
}
```

### OpenAPI JSON Schema Components
The generated `/v3/api-docs` must include:
```json
{
  "components": {
    "securitySchemes": {
      "bearerAuth": {
        "type": "http",
        "scheme": "bearer",
        "bearerFormat": "JWT",
        "description": "Keycloak JWT Bearer Token"
      }
    }
  },
  "security": [
    {
      "bearerAuth": []
    }
  ]
}
```
