-- Go-live: people created by an admin (one by one, by CSV import or by the bootstrap) or whose password an admin
-- resets must choose their own password before using the app. Existing accounts are not forced.
ALTER TABLE users
    ADD COLUMN must_change_password BOOLEAN     NOT NULL DEFAULT FALSE AFTER password_hash,
    ADD COLUMN time_zone            VARCHAR(64) NULL AFTER working_hours;
