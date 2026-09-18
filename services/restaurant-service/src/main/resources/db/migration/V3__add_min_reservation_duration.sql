ALTER TABLE restaurant ADD COLUMN IF NOT EXISTS min_reservation_duration_minutes INT NOT NULL DEFAULT 45;
