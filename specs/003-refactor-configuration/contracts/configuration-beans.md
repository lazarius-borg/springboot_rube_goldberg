# Configuration Bean Contracts

**Feature**: `003-refactor-configuration` | **Date**: 2026-09-11

## Overview

This contract document defines the exposed beans, interfaces, qualifiers, and expected lifecycle contracts provided by the dedicated configuration components.

---

## 1. Serialization Contract (`JacksonConfig`)

| Attribute | Specification |
|:---|:---|
| **Bean Name** | `objectMapper` |
| **Bean Type** | `com.fasterxml.jackson.databind.ObjectMapper` |
| **Configuration Class** | `nl.invokedynamic.demo.<service>.config.JacksonConfig` |
| **Annotation** | `@Configuration(proxyBeanMethods = false)` |
| **Modules Registered** | `com.fasterxml.jackson.datatype.jsr310.JavaTimeModule` |
| **Disabled Features** | `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS`, `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES` |
| **Consuming Components** | Event publishers, event listeners, domain services, Kafka message serializers |
| **Scope** | Singleton |

---

## 2. Gateway Security Contract (`SecurityConfig`)

| Attribute | Specification |
|:---|:---|
| **Bean Name** | `springSecurityFilterChain` |
| **Bean Type** | `org.springframework.security.web.server.SecurityWebFilterChain` |
| **Configuration Class** | `nl.invokedynamic.demo.gateway.config.SecurityConfig` |
| **Annotation** | `@Configuration(proxyBeanMethods = false)`, `@EnableWebFluxSecurity` |
| **Injected Dependencies**| `org.springframework.security.config.web.server.ServerHttpSecurity` |
| **Public Paths** | `/actuator/**`, `/ui/**`, `/api/v1/availability/**`, `/api/v1/restaurants/**` |
| **CSRF** | Disabled (`CsrfSpec::disable`) |
| **Scope** | Singleton |

---

## 3. Availability Caching Contract (`CacheConfig`)

| Attribute | Specification |
|:---|:---|
| **Bean Name** | `cacheManager` |
| **Bean Type** | `org.springframework.data.redis.cache.RedisCacheManager` (implements `org.springframework.cache.CacheManager`) |
| **Configuration Class** | `nl.invokedynamic.demo.availability.config.CacheConfig` |
| **Annotation** | `@Configuration(proxyBeanMethods = false)`, `@EnableCaching` |
| **Injected Dependencies**| `org.springframework.data.redis.connection.RedisConnectionFactory` |
| **Default TTL** | 10 minutes (`Duration.ofMinutes(10)`) |
| **Null Caching** | Disabled (`disableCachingNullValues()`) |
| **Scope** | Singleton |
