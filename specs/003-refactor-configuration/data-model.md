# Data Model & Configuration Component Catalog: Refactor Configuration

**Feature**: `003-refactor-configuration` | **Date**: 2026-09-11

## Overview

This feature does not alter domain database entities or database schemas. Instead, it refactors the application configuration component architecture across all 8 microservices and gateway into dedicated, encapsulated `@Configuration(proxyBeanMethods = false)` components.

---

## Configuration Component Specifications

### 1. `JacksonConfig` (Domain Microservices)

**Target Package**: `nl.invokedynamic.demo.<service>.config`

**Target Services**:
- `customer-service`
- `restaurant-service`
- `reservation-service`
- `availability-service`
- `waiting-list-service`
- `notification-service`
- `analytics-service`

**Class Definition**:
```java
package nl.invokedynamic.demo.<service>.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
```

**Responsibilities**:
- Instantiates and registers the primary Jackson 2 `ObjectMapper` bean.
- Registers `JavaTimeModule` for ISO-8601 `Instant`, `LocalDate`, and `LocalTime` serialization.
- Ensures consistent date formatting without numeric timestamps.
- Disables `FAIL_ON_UNKNOWN_PROPERTIES` for backwards-compatible event and DTO deserialization.

---

### 2. `SecurityConfig` (Gateway)

**Target Package**: `nl.invokedynamic.demo.gateway.config`

**Target Service**: `gateway`

**Class Definition**:
```java
package nl.invokedynamic.demo.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/**", "/ui/**", "/api/v1/availability/**", "/api/v1/restaurants/**").permitAll()
                        .anyExchange().permitAll()
                )
                .build();
    }
}
```

**Responsibilities**:
- Enables reactive WebFlux security via `@EnableWebFluxSecurity`.
- Configures CSRF protection and public path matchers.
- Registers the `SecurityWebFilterChain` bean.

---

### 3. `CacheConfig` (Availability Service)

**Target Package**: `nl.invokedynamic.demo.availability.config`

**Target Service**: `availability-service`

**Class Definition**:
```java
package nl.invokedynamic.demo.availability.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues();
        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(config)
                .build();
    }
}
```

**Responsibilities**:
- Enables declarative Spring caching via `@EnableCaching`.
- Configures Redis cache defaults (10-minute entry TTL, null-value caching disabled).
- Registers the primary `CacheManager` bean.

---

## Application Class State (Post-Refactoring)

Every `*Application.java` will maintain only:
1. `public static void main(String[] args)` bootstrap.
2. `@SpringBootApplication` annotation.
3. Service metadata (e.g. `@OpenAPIDefinition`) and scheduling triggers (`@EnableScheduling`).
4. **Zero `@Bean` methods**.
