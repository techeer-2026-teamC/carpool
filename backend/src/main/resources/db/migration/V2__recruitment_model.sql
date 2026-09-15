ALTER TABLE posts ADD COLUMN IF NOT EXISTS type varchar(16) NOT NULL DEFAULT 'CARPOOL';
ALTER TABLE posts ADD COLUMN IF NOT EXISTS manually_closed boolean NOT NULL DEFAULT false;
ALTER TABLE posts ADD COLUMN IF NOT EXISTS meeting_completed_at timestamp;
ALTER TABLE posts ADD COLUMN IF NOT EXISTS departure_notified_at timestamp;
-- Existing CLOSED rows may have been closed by the owner: preserve closure rather than reopen them.
UPDATE posts SET manually_closed = true WHERE status = 'CLOSED';
UPDATE posts SET auto_accept = false WHERE auto_accept = true;
-- NOT VALID preserves historical inconsistent rows for a deliberate data audit, while guarding new writes.
ALTER TABLE posts ADD CONSTRAINT posts_seat_capacity CHECK (
    max_passengers >= CASE WHEN type = 'TAXI' THEN 2 ELSE 1 END AND current_passengers >= 0
    AND current_passengers + CASE WHEN type = 'TAXI' THEN 1 ELSE 0 END <= max_passengers) NOT VALID;
ALTER TABLE posts ADD CONSTRAINT posts_type CHECK (type IN ('CARPOOL','TAXI')) NOT VALID;
