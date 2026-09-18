ALTER TABLE restaurant ADD COLUMN IF NOT EXISTS max_reservation_duration_minutes INT NOT NULL DEFAULT 180;
ALTER TABLE restaurant_table ADD COLUMN IF NOT EXISTS zone VARCHAR(50) DEFAULT 'Main Dining';
ALTER TABLE opening_hours ALTER COLUMN open_time DROP NOT NULL;
ALTER TABLE opening_hours ALTER COLUMN close_time DROP NOT NULL;
