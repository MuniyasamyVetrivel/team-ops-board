# Go-live data: start from a clean database with the real team

Use these steps to reset a database to a clean state and load the real team. They are the same on a developer
machine and on the production server. Only the place where the settings live differs (see
[Production differences](#production-differences)).

When you finish:
- The database contains the schema and the reference data from Flyway.
- One Super Admin exists, created from `BOOTSTRAP_ADMIN_*`.
- Everyone else comes from the team CSV, imported in **Admin → Users → Import CSV**.
- Every person, the Super Admin included, chooses their own password at first sign-in.

> The team file holds personal data. Keep it in `private/` at the project root, which is git-ignored, and never
> commit, email or paste it anywhere. Check with `git check-ignore -v private/team.csv`.

## What is removed and what is kept

| Removed | Kept |
|---|---|
| Every user, including demo users and their sessions | The application code and configuration |
| All tasks, tickets, projects, approvals, announcements, articles, documents, calendar entries, notifications | `backend/.env` (you edit a few lines in it) |
| All marketing data: SEO pages and rankings, targets, activities, campaigns, leads, backlinks, content, reports | `private/team.csv` |
| Celebrations data: employees, important days, events, LinkedIn posts | The MySQL server, and the `teamops` database user and its grants |
| Audit log entries | |
| Uploaded files in `FILE_STORAGE_DIR` (they belong to the removed records) | |

Flyway recreates the **reference data** on the next start. Nothing has to be added by hand:
- Roles and permissions, and the permissions of each role.
- The 10 departments (codes `IT`, `CYBERSEC`, `HR`, `TA`, `WEBDEV`, `APPDEV`, `DM`, `PRESALES`, `GRAPHICS`,
  `PAYROLL`).
- SLA policies.
- Marketing target types.
- Ticket categories and knowledge base categories.
- Approval types and their workflow steps.
- Admin settings and code sequences.

Changes made in the app to reference data, such as edited settings, role permissions, SLA policies or extra
departments, are reset to the Flyway defaults. Write them down first if you want to apply them again.

## Before you start

- The team CSV is ready in `private/team.csv`. It uses the columns of the import template:
  `first_name, last_name, email, phone, job_title, department_code, extra_department_codes, role, reports_to_email,
  location, time_zone, extra_permissions`.
  - `department_code` and `extra_department_codes` must use the codes listed above.
  - Separate several extra departments or permissions with commas, and quote the cell (`"A,B"`).
  - Digital Marketing access means listing each permission. For example, full marketing access is
    `MARKETING_VIEW, MARKETING_EDIT, SEO_VIEW, SEO_EDIT, CAMPAIGN_VIEW, CAMPAIGN_EDIT, TARGET_VIEW, TARGET_EDIT,
    LEAD_VIEW, LEAD_EDIT, BACKLINK_VIEW, BACKLINK_EDIT, CONTENT_VIEW, CONTENT_EDIT`. Add `CELEBRATIONS_EDIT` for
    the Celebrations pages. An edit permission always needs its view permission.
  - A manager in `reports_to_email` must already exist or appear on an earlier row.
- You know the MySQL admin (`root`) password.
- The MySQL client tools are installed. On Windows they are in `C:\Program Files\MySQL\MySQL Server 8.x\bin`.

## Steps

### 1. Back up the current database (recommended)

```bash
mysqldump -u root -p --single-transaction --routines team_ops_board > team_ops_board-before-go-live.sql
```

Also copy the upload directory (`FILE_STORAGE_DIR`, `backend/uploads` by default) if you might need those files.
Store the backup somewhere private: it contains personal data.

### 2. Stop the backend

Stop `mvnw spring-boot:run`, or the service on the server. Flyway must not run while the database is dropped.

### 3. Drop and recreate the database

Run as the MySQL admin user:

```sql
DROP DATABASE team_ops_board;
CREATE DATABASE team_ops_board CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

Dropping a database keeps the `teamops` user and its database-level grant. If the user does not exist yet, for
example on a new server, create it as shown in the README (step 1 of Installation).

### 4. Empty the upload directory

Delete the contents of `FILE_STORAGE_DIR` (`backend/uploads` by default), but keep the directory. Those files
belonged to the records you just removed.

### 5. Configure the first start

In `backend/.env`:

```properties
# The demo seeder exists only in the automated tests; keep this off everywhere.
DEV_SEED_ENABLED=false

# Creates the first Super Admin, only while the users table is empty.
BOOTSTRAP_ADMIN_EMAIL=<the Super Admin's work email, the same as in the team CSV>
BOOTSTRAP_ADMIN_PASSWORD=<a temporary password of at least 12 characters>
BOOTSTRAP_ADMIN_FIRST_NAME=<first name>
BOOTSTRAP_ADMIN_LAST_NAME=<last name>
BOOTSTRAP_ADMIN_DEPARTMENT_CODE=<their department code, e.g. HR>
```

The bootstrap password is temporary. The Super Admin must replace it at first sign-in, and after that it no longer
works.

### 6. Start the backend

```bash
cd backend
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

In the log, look for:
- Flyway migrating the schema up to the latest version.
- `Bootstrap Super Admin created: …`.

To check the result (optional):

```sql
USE team_ops_board;
SELECT COUNT(*) FROM users;                 -- 1
SELECT code FROM departments ORDER BY code; -- the 10 codes above
SELECT COUNT(*) FROM roles;                 -- 3
SELECT COUNT(*) FROM tasks;                 -- 0
```

### 7. Sign in as the Super Admin

1. Start the frontend (`cd frontend && npm run dev`, or use the production URL).
2. Sign in with the bootstrap email and password.
3. The app asks you to choose your own password: at least 10 characters, with letters and numbers. Nothing else in
   the app works until you do.
4. Remove `BOOTSTRAP_ADMIN_PASSWORD` from `backend/.env`. It is no longer used, and the bootstrap never runs again
   once a user exists.

### 8. Import the team

1. Go to **Admin → Users** and choose **Import CSV**.
2. Choose `private/team.csv` and select **Check file**. Nothing is saved yet.
3. Review the result:
   - Every person should be a valid row, except the Super Admin's row. It is listed under **Invalid rows** with
     "A user with this email already exists", because step 6 created that account.
   - Fix any other error in the file, then check it again. Invalid rows are never imported.
4. Tick **Skip the 1 invalid row**, then select **Import**.
5. Select **Download file** right away. The file (`new-users-<date>.csv`) lists each new person's temporary
   password. It is shown only once and is not stored anywhere, not even in the database, which keeps only password
   hashes.
6. Give each person their own temporary password privately, then delete the file.

The import is audited. **Admin → Audit logs** shows "Users imported from CSV" and "CSV import committed", plus a
"User created" entry for each person. Passwords never appear in the audit log.

### 9. Complete the Super Admin's profile

The bootstrap only sets the name, email, department and role. In **Admin → Users**, open the Super Admin and fill in
the phone, job title, location and time zone from the CSV row that was skipped.

### 10. First sign-in for everyone

Each person signs in with their temporary password and must choose their own before using the app. Under
**Admin → Users → (person) → Security**, a person who has not done that yet shows "They have not chosen their own
password yet". To give someone a new temporary password, use **Reset password** there. They will have to change
it again at their next sign-in.

## Production differences

- Put the settings in the server's environment, or in a `.env` file next to the jar, instead of `backend/.env`.
  Use `REFRESH_COOKIE_SECURE=true` and a strong `JWT_SECRET`, as described in the README's Deployment section.
- Copy the team CSV to the server only for the import, through a private channel, then delete it. You can also run
  the import from your own browser, signed in to the production site, with the file on your machine.
- Take the backup in step 1 even if the database looks empty.
- Keep `DEV_SEED_ENABLED=false`. The demo data now lives only in the automated tests, so the production build
  cannot create it.
