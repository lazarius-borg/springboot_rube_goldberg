# Contract: Grafana Dashboard Layout & Provisioning Specification

**Feature**: `010-grafana-dashboard-analytics`  
**Date**: 2026-09-15  
**Spec**: [spec.md](../spec.md)

---

## 1. Dashboard Metadata
- **Title**: `Spring Boot Rube Goldberg Platform Overview`
- **UID**: `rube-goldberg-platform`
- **Editable**: `true`
- **Time Range**: Default `now-24h` to `now`, refresh `10s`
- **Tags**: `["rube-goldberg", "microservices", "operations", "analytics"]`

---

## 2. Dashboard Template Variables
- **Variable**: `restaurant_id`
  - **Label**: `Restaurant`
  - **Type**: `query`
  - **Query**: `label_values(reservation_status_total, restaurant_id)`
  - **Include All option**: `true`
  - **Custom All Value**: `.*`
  - **Multi-select**: `false`

---

## 3. Panel Grid Layout & Query Specifications

### Panel 1: Peak Visit Times Matrix (Day-of-Week & Hourly Heatmap)
- **Type**: `heatmap` or `barchart`
- **Title**: `Popular Visit Times (Day-of-Week & Service Hour)`
- **Grid Position**: `{ "h": 8, "w": 12, "x": 0, "y": 0 }`
- **PromQL Query**:
  ```promql
  sum by (day_of_week, hour) (reservation_demand_total{restaurant_id=~"$restaurant_id"})
  ```
- **Description**: Displays reservation demand concentration across days (Mon–Sun) and service hours (00–23) to highlight rush periods.

### Panel 2: Party Size Distribution
- **Type**: `barchart`
- **Title**: `Party Size Distribution (1, 2, 3, 4, 5, 6, 7+ Guests)`
- **Grid Position**: `{ "h": 8, "w": 12, "x": 12, "y": 0 }`
- **PromQL Query**:
  ```promql
  sum by (party_bucket) (reservation_party_size_total{restaurant_id=~"$restaurant_id"})
  ```
- **Description**: Compares guest group sizes across discrete capacity categories.

### Panel 3: Cancellation Rate & Frequency by Reason
- **Type**: `timeseries` / `stat`
- **Title**: `Cancellations by Category & Overall Rate`
- **Grid Position**: `{ "h": 8, "w": 12, "x": 0, "y": 8 }`
- **PromQL Query A (Counts by Category)**:
  ```promql
  sum by (category) (reservation_cancellations_total{restaurant_id=~"$restaurant_id"})
  ```
- **PromQL Query B (Cancellation Percentage)**:
  ```promql
  (sum(reservation_cancellations_total{restaurant_id=~"$restaurant_id"}) / clamp_min(sum(reservation_status_total{restaurant_id=~"$restaurant_id", status="CREATED"}), 1)) * 100
  ```
- **Description**: Visualizes cancellations by customer request, no-show/late cancel, and restaurant-initiated with percentage rate tracking.

### Panel 4: Waiting List Conversion Efficiency
- **Type**: `gauge` / `stat`
- **Title**: `Waiting List Entry-to-Offer Conversion Rate`
- **Grid Position**: `{ "h": 8, "w": 12, "x": 12, "y": 8 }`
- **PromQL Query**:
  ```promql
  (sum(waiting_list_offers_accepted_total{restaurant_id=~"$restaurant_id"}) / clamp_min(sum(waiting_list_entries_created_total{restaurant_id=~"$restaurant_id"}), 1)) * 100
  ```
- **Unit**: `percent (0-100)`

### Panel 5: Platform Throughput & Reservation Statuses
- **Type**: `timeseries`
- **Title**: `Reservation Lifecycle Statuses`
- **Grid Position**: `{ "h": 6, "w": 24, "x": 0, "y": 16 }`
- **PromQL Query**:
  ```promql
  sum by (status) (rate(reservation_status_total{restaurant_id=~"$restaurant_id"}[5m]))
  ```
