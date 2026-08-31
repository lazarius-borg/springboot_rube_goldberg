CREATE TABLE IF NOT EXISTS restaurant_view (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    timezone VARCHAR(50) NOT NULL,
    default_reservation_duration_minutes INT NOT NULL,
    min_booking_advance_minutes INT NOT NULL,
    max_booking_horizon_days INT NOT NULL,
    cancellation_window_hours INT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS table_inventory_view (
    id UUID PRIMARY KEY,
    restaurant_id UUID NOT NULL,
    table_number VARCHAR(20) NOT NULL,
    capacity INT NOT NULL
);

CREATE TABLE IF NOT EXISTS table_combination_view (
    id UUID PRIMARY KEY,
    restaurant_id UUID NOT NULL,
    name VARCHAR(50) NOT NULL,
    table_ids UUID[] NOT NULL,
    combined_capacity INT NOT NULL
);

CREATE TABLE IF NOT EXISTS slot_occupancy_view (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL,
    restaurant_id UUID NOT NULL,
    table_id UUID NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    consumer_group VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);
