-- V32: employee ID and profile photo for every app user (Expansion Phase X4; visible to everyone signed in).
--
-- 1. users.employee_id: the company's employee ID, unique (case-insensitive under the table collation). New users
--    must have one; only a Super Admin can change it later. It links an app user to their Employee record
--    (celebrations, employees.employee_code), so a person's details are kept once.
--    Existing users take the code of the Employee record already linked to them. Anyone left without one is listed
--    for the Super Admin to fill in (Admin -> Users, "Missing employee ID"); no placeholder IDs are invented.
-- 2. users.photo_file_id: the profile photo, a square JPEG resized by the server and stored like every other file.

ALTER TABLE users
    ADD COLUMN employee_id   VARCHAR(40) NULL AFTER id,
    ADD COLUMN photo_file_id BIGINT      NULL AFTER time_zone,
    ADD UNIQUE KEY uk_users_employee_id (employee_id),
    ADD KEY idx_users_photo (photo_file_id),
    ADD CONSTRAINT fk_users_photo FOREIGN KEY (photo_file_id) REFERENCES files (id) ON DELETE SET NULL;

UPDATE users u
    JOIN employees e ON e.user_id = u.id AND e.deleted_at IS NULL
SET u.employee_id = e.employee_code;
