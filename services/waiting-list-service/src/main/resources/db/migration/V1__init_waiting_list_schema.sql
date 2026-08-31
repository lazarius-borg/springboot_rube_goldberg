CREATE TABLE IF NOT EXISTS waiting_list_entry (
    id UUID PRIMARY KEY,
    restaurant_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    customer_email VARCHAR(255) NOT NULL,
    target_date DATE NOT NULL,
    earliest_time TIME NOT NULL,
    latest_time TIME NOT NULL,
    party_size INT NOT NULL CHECK (party_size > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS waiting_list_offer (
    id UUID PRIMARY KEY,
    waiting_list_entry_id UUID NOT NULL REFERENCES waiting_list_entry(id) ON DELETE CASCADE,
    restaurant_id UUID NOT NULL,
    offered_start_time TIMESTAMPTZ NOT NULL,
    offered_table_ids UUID[] NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    trace_context JSONB,
    created_at TIMESTAMPTZ NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    consumer_group VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);
