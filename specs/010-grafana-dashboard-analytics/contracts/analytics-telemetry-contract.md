# Contract: Analytics Telemetry & API Specification

**Feature**: `010-grafana-dashboard-analytics`  
**Date**: 2026-09-15  
**Spec**: [spec.md](../spec.md)

---

## 1. Prometheus Telemetry Contract (`/actuator/prometheus`)

The `analytics-service` exposes the following Prometheus metric families via Micrometer:

### 1.1 `reservation_demand_total`
- **Help**: Total reservations booked by day of week and service hour.
- **Type**: Counter
- **Labels**:
  - `restaurant_id`: UUID
  - `day_of_week`: `MONDAY` | `TUESDAY` | `WEDNESDAY` | `THURSDAY` | `FRIDAY` | `SATURDAY` | `SUNDAY`
  - `hour`: `00`..`23`
- **Example Exposition**:
  ```text
  # HELP reservation_demand_total Total reservations booked by day of week and hour
  # TYPE reservation_demand_total counter
  reservation_demand_total{day_of_week="FRIDAY",hour="19",restaurant_id="a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d"} 42.0
  ```

### 1.2 `reservation_party_size_total`
- **Help**: Total reservations booked categorized by party size bucket.
- **Type**: Counter
- **Labels**:
  - `restaurant_id`: UUID
  - `party_bucket`: `1` | `2` | `3` | `4` | `5` | `6` | `7+`
- **Example Exposition**:
  ```text
  # HELP reservation_party_size_total Total reservations booked by party size bucket
  # TYPE reservation_party_size_total counter
  reservation_party_size_total{party_bucket="2",restaurant_id="a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d"} 85.0
  reservation_party_size_total{party_bucket="4",restaurant_id="a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d"} 34.0
  ```

### 1.3 `reservation_cancellations_total`
- **Help**: Total reservation cancellations categorized by trigger reason.
- **Type**: Counter
- **Labels**:
  - `restaurant_id`: UUID
  - `category`: `CUSTOMER_REQUEST` | `NO_SHOW_LATE_CANCEL` | `RESTAURANT_INITIATED`
- **Example Exposition**:
  ```text
  # HELP reservation_cancellations_total Total cancellations by category
  # TYPE reservation_cancellations_total counter
  reservation_cancellations_total{category="CUSTOMER_REQUEST",restaurant_id="a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d"} 6.0
  reservation_cancellations_total{category="NO_SHOW_LATE_CANCEL",restaurant_id="a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d"} 2.0
  ```

---

## 2. REST API Summary Contract (`GET /api/v1/analytics/summary`)

### Endpoint
`GET /api/v1/analytics/summary?restaurantId={optionalUuid}`

### Query Parameters
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `restaurantId` | UUID | No | Specific restaurant UUID. If omitted, returns platform-wide aggregates. |

### Response Body (`200 OK`)
```json
{
  "restaurantId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
  "totalReservations": 120,
  "completedReservations": 98,
  "cancelledReservations": 14,
  "noShows": 8,
  "totalGuests": 348,
  "averagePartySize": 2.9,
  "cancellationRatePercentage": 11.67,
  "cancellationsByCategory": {
    "CUSTOMER_REQUEST": 10,
    "NO_SHOW_LATE_CANCEL": 3,
    "RESTAURANT_INITIATED": 1
  },
  "partySizeDistribution": {
    "1": 12,
    "2": 62,
    "3": 18,
    "4": 20,
    "5": 4,
    "6": 3,
    "7+": 1
  },
  "waitingListConversionRate": 82.5
}
```
