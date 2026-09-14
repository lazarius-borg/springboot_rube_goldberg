ALTER TABLE reservation
ADD COLUMN IF NOT EXISTS cancellation_window_hours INT NOT NULL DEFAULT 2;
