ALTER TABLE restaurant_view ADD COLUMN IF NOT EXISTS min_reservation_duration_minutes INT NOT NULL DEFAULT 45;
