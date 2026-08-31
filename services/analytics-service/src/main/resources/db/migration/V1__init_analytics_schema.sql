CREATE TABLE IF NOT EXISTS reservation_daily_metrics (
    id UUID PRIMARY KEY,
    restaurant_id UUID NOT NULL,
    metric_date DATE NOT NULL,
    reservations_created_count BIGINT NOT NULL DEFAULT 0,
    reservations_completed_count BIGINT NOT NULL DEFAULT 0,
    reservations_cancelled_count BIGINT NOT NULL DEFAULT 0,
    reservations_no_show_count BIGINT NOT NULL DEFAULT 0,
    total_guests_count BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS waiting_list_daily_metrics (
    id UUID PRIMARY KEY,
    restaurant_id UUID NOT NULL,
    metric_date DATE NOT NULL,
    entries_created_count BIGINT NOT NULL DEFAULT 0,
    offers_created_count BIGINT NOT NULL DEFAULT 0,
    offers_accepted_count BIGINT NOT NULL DEFAULT 0,
    offers_expired_count BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    consumer_group VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);
