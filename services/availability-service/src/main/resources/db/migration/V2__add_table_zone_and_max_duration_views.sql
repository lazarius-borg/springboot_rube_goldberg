ALTER TABLE restaurant_view ADD COLUMN IF NOT EXISTS max_reservation_duration_minutes INT NOT NULL DEFAULT 180;
ALTER TABLE table_inventory_view ADD COLUMN IF NOT EXISTS zone VARCHAR(50) DEFAULT 'Main Dining';
