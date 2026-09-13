# Data Model & Component Catalog: OpenAPI and Swagger UI Security

**Feature**: `004-fix-swagger-endpoints` | **Date**: 2026-09-13

## Overview

This document catalogs the configuration components, security filter chains, OpenAPI schemes, and path access policies across the microservices ecosystem.

---

## 1. Component Architecture Catalog

### Secured Domain Services
The following 5 microservices include `spring-boot-starter-oauth2-resource-server` and define both `SecurityConfig` and `OpenApiConfig`:
1. `customer-service` (`port 8082`)
2. `restaurant-service` (`port 8083`)
3. `availability-service` (`port 8084`)
4. `reservation-service` (`port 8085`)
5. `waiting-list-service` (`port 8086`)

### Unsecured Services
1. `analytics-service` (`port 8087`): No Spring Security starter; exposes `OpenApiConfig` without auth restrictions.
2. `notification-service`: Internal event processor (no web endpoints or swagger).
3. `gateway` (`port 8080`): Reactive gateway with existing `SecurityConfig`.

---

## 2. Security Configuration Model (`SecurityConfig`)

| Property | Value / Specification |
|:---|:---|
| **Class** | `nl.invokedynamic.demo.<service>.config.SecurityConfig` |
| **Annotations** | `@Configuration(proxyBeanMethods = false)`, `@EnableWebSecurity` |
| **Bean Type** | `org.springframework.security.web.SecurityFilterChain` |
| **Bean Name** | `securityFilterChain` |
| **CSRF** | Disabled (`AbstractHttpConfigurer::disable`) |
| **Session Policy**| Stateless |
| **Permitted Paths** | `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**` |
| **Secured Paths** | `anyRequest().authenticated()` (all domain API routes and `/actuator/**`) |
| **Auth Mechanism**| `oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))` |

---

## 3. OpenAPI Configuration Model (`OpenApiConfig`)

| Property | Value / Specification |
|:---|:---|
| **Class** | `nl.invokedynamic.demo.<service>.config.OpenApiConfig` |
| **Annotations** | `@Configuration(proxyBeanMethods = false)`, `@OpenAPIDefinition(...)`, `@SecurityScheme(...)` |
| **Security Scheme Name** | `bearerAuth` |
| **Scheme Type** | `SecuritySchemeType.HTTP` (`http`) |
| **Scheme** | `bearer` |
| **Bearer Format** | `JWT` |
| **Description** | `Keycloak JWT Bearer Token` |
| **Global Security Item** | `@SecurityRequirement(name = "bearerAuth")` |

---

## 4. Path Authorization Matrix

| Endpoint Pattern | Access Rule | Intended Consumer |
|:---|:---:|:---|
| `GET /swagger-ui.html` | `permitAll()` | Browser (redirect to index.html) |
| `GET /swagger-ui/**` | `permitAll()` | Browser (UI static resources & webjars) |
| `GET /v3/api-docs/**` | `permitAll()` | Browser & automated tools (OpenAPI schema) |
| `GET /actuator/**` | `authenticated()` | Monitoring tools with Bearer token |
| `ALL /api/v1/**` | `authenticated()` | Authenticated clients & gateway routing |
