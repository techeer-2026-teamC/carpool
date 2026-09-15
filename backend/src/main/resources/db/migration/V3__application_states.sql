-- Hibernate's old string-enum constraint must include the new terminal application state.
ALTER TABLE applications DROP CONSTRAINT IF EXISTS applications_status_check;
ALTER TABLE applications ADD CONSTRAINT applications_status_check CHECK (status IN ('PENDING','ACCEPTED','REJECTED','CANCELLED')) NOT VALID;
