ALTER TABLE reservation_daily_metrics
    ADD COLUMN IF NOT EXISTS party_size1_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS party_size2_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS party_size3_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS party_size4_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS party_size5_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS party_size6_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS party_size7_plus_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS cancelled_customer_request_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS cancelled_no_show_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS cancelled_restaurant_initiated_count BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS reservation_hourly_metrics (
    id UUID PRIMARY KEY,
    restaurant_id UUID NOT NULL,
    metric_date DATE NOT NULL,
    hour_of_day INT NOT NULL,
    reservation_count BIGINT NOT NULL DEFAULT 0
);
