# Phase 1: Data Model & Service Mapping

**Feature**: `018-jib-docker-containers`  
**Date**: 2026-09-18  

## 1. Container Image Entities

Each runnable microservice produces an OCI-compliant layered container image built with Jib:

| Module Artifact ID | Main Application Class | Produced Primary Tag | Secondary Tag |
| :--- | :--- | :--- | :--- |
| `gateway` | `nl.invokedynamic.demo.gateway.GatewayApplication` | `springboot-rube-goldberg/gateway:1.0.0-SNAPSHOT` | `springboot-rube-goldberg/gateway:latest` |
| `customer-service` | `nl.invokedynamic.demo.customer.CustomerServiceApplication` | `springboot-rube-goldberg/customer-service:1.0.0-SNAPSHOT` | `springboot-rube-goldberg/customer-service:latest` |
| `restaurant-service` | `nl.invokedynamic.demo.restaurant.RestaurantServiceApplication` | `springboot-rube-goldberg/restaurant-service:1.0.0-SNAPSHOT` | `springboot-rube-goldberg/restaurant-service:latest` |
| `availability-service` | `nl.invokedynamic.demo.availability.AvailabilityServiceApplication` | `springboot-rube-goldberg/availability-service:1.0.0-SNAPSHOT` | `springboot-rube-goldberg/availability-service:latest` |
| `reservation-service` | `nl.invokedynamic.demo.reservation.ReservationServiceApplication` | `springboot-rube-goldberg/reservation-service:1.0.0-SNAPSHOT` | `springboot-rube-goldberg/reservation-service:latest` |
| `waiting-list-service` | `nl.invokedynamic.demo.waitinglist.WaitingListServiceApplication` | `springboot-rube-goldberg/waiting-list-service:1.0.0-SNAPSHOT` | `springboot-rube-goldberg/waiting-list-service:latest` |
| `notification-service` | `nl.invokedynamic.demo.notification.NotificationServiceApplication` | `springboot-rube-goldberg/notification-service:1.0.0-SNAPSHOT` | `springboot-rube-goldberg/notification-service:latest` |
| `analytics-service` | `nl.invokedynamic.demo.analytics.AnalyticsServiceApplication` | `springboot-rube-goldberg/analytics-service:1.0.0-SNAPSHOT` | `springboot-rube-goldberg/analytics-service:latest` |

### Non-Runnable Modules (Skipped)
- `springboot-rube-goldberg` (root parent POM) — `jib.skip = true`
- `event-contracts` (shared domain library) — `jib.skip = true`

---

## 2. Docker Compose Service Mapping

All services in `infrastructure/docker-compose.apps.yml` migrate from `build:` blocks to `image:` definitions:

```yaml
services:
  gateway:
    image: springboot-rube-goldberg/gateway:${IMAGE_TAG:-latest}
    container_name: rube-gateway
    ports: ["8080:8080"]
    ...
  customer-service:
    image: springboot-rube-goldberg/customer-service:${IMAGE_TAG:-latest}
    container_name: rube-customer-service
    ports: ["8082:8082"]
    ...
  restaurant-service:
    image: springboot-rube-goldberg/restaurant-service:${IMAGE_TAG:-latest}
    container_name: rube-restaurant-service
    ports: ["8083:8083"]
    ...
  availability-service:
    image: springboot-rube-goldberg/availability-service:${IMAGE_TAG:-latest}
    container_name: rube-availability-service
    ports: ["8084:8084"]
    ...
  reservation-service:
    image: springboot-rube-goldberg/reservation-service:${IMAGE_TAG:-latest}
    container_name: rube-reservation-service
    ports: ["8085:8085"]
    ...
  waiting-list-service:
    image: springboot-rube-goldberg/waiting-list-service:${IMAGE_TAG:-latest}
    container_name: rube-waiting-list-service
    ports: ["8086:8086"]
    ...
  notification-service:
    image: springboot-rube-goldberg/notification-service:${IMAGE_TAG:-latest}
    container_name: rube-notification-service
    ports: ["8088:8088"]
    ...
  analytics-service:
    image: springboot-rube-goldberg/analytics-service:${IMAGE_TAG:-latest}
    container_name: rube-analytics-service
    ports: ["8087:8087"]
    ...
```

### Invariants & Guarantees
1. **Zero Drift**: Runtime ports, environment profiles (`SPRING_PROFILES_ACTIVE=docker`), memory ceilings (`mem_limit: 512m`), JVM flags (`JAVA_TOOL_OPTIONS=-Xms128m -Xmx384m`), and health check dependencies (`depends_on`) remain identical.
2. **Layer Caching**: Jib separates dependencies, resources, and compiled classes into separate OCI layers, ensuring re-compiling single classes only pushes tiny deltas without re-packaging or re-layering third-party JARs.
