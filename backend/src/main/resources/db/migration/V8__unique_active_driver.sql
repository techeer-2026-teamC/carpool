-- Preserve ambiguous historical data for an explicit owner/vehicle review.
DO $$
BEGIN
    IF EXISTS (SELECT member_id FROM drivers WHERE deleted = false
               GROUP BY member_id HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Duplicate active drivers exist. Review docs/migrations/V8-active-driver.md before retrying migration.';
    END IF;
END $$;

CREATE UNIQUE INDEX uq_drivers_active_member ON drivers (member_id) WHERE deleted = false;
