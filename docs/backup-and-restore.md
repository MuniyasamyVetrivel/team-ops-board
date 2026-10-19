# Backups and restore

Phase X3 of the expansion brief: nightly encrypted backups of the database **and** the uploaded files, kept off the
app server and optionally in a second place, with a tested way back, and a break-glass procedure so nobody is ever
locked out.

Contents: [What a backup is](#what-a-backup-is) · [Schedule, retention and alerts](#schedule-retention-and-alerts) ·
[Where backups go](#where-backups-go) · [Second copy](#second-copy) · [The encryption key](#the-encryption-key) ·
[Restoring](#restoring) · [Monthly restore test](#monthly-restore-test) · [Export all data](#export-all-data) ·
[Break-glass access](#break-glass-access)

## What a backup is

One file per run, `team-ops-backup-<UTC time>.tobk`, plus `<file>.sha256` beside it:

1. `mysqldump` (MySQL 8.4 client, `MYSQLDUMP_PATH`) takes one consistent InnoDB snapshot of the app database. The dump
   has no `CREATE DATABASE`/`USE`, so it can be restored into any database name.
2. The dump, every file under `FILE_STORAGE_DIR` (as `uploads/...`) and a `manifest.json` are zipped.
3. The ZIP is encrypted with AES-256-GCM in 1 MiB authenticated chunks, using `BACKUP_ENCRYPTION_KEY` (base64 of
   32 random bytes, in `backend/.env` only).
4. The SHA-256 of the encrypted file is stored in the backup history (`backup_runs.checksum`) and in the `.sha256` file.
5. The file goes to each target: the first (`BACKUP_TARGET`) and, when configured, the second
   (`BACKUP_SECONDARY_TARGET`). After writing, each stored copy is read back: the checksum must match, every chunk
   must decrypt and the ZIP must contain a non-empty `database.sql`. Only then does that copy count as a success.

Every run is in the history (start, end, size, checksum, and for each copy its target, status and error) and in the
audit log: `BACKUP_COMPLETED` (every copy stored), `BACKUP_COPY_FAILED` (first copy stored, second failed) or
`BACKUP_FAILED` (first copy failed; the details say whether the second one made it).

## Schedule, retention and alerts

| Setting | Default | |
|---|---|---|
| `BACKUP_ENABLED` | `true` | nightly schedule on/off ("Run backup now" works either way) |
| `BACKUP_CRON` | `0 0 2 * * *` | 02:00 in `APP_TIME_ZONE` |
| `BACKUP_KEEP_DAILY` / `BACKUP_KEEP_MONTHLY` | 30 / 12 | the newest backup of each of the last 30 days and each of the last 12 months is kept; older files are deleted after each successful run (`BACKUP_REMOVED` in the audit log). Days without a backup do not count, so failed nights never delete the last good backups |
| `BACKUP_MAX_AGE_HOURS` | 26 | alert threshold |

The status endpoint (`GET /api/admin/backups/status`, `BACKUP_VIEW`) and `/actuator/health` report a problem when the
last successful backup is older than 26 hours, the last run failed, no backup has ever completed, or the key is
missing. Each copy is judged on its own (`copies` in the status): problems of the second copy are reported as
`SECONDARY_STALE`, `SECONDARY_LAST_FAILED` and `SECONDARY_NO_BACKUP`. `/actuator/health` then answers `{"status":"WARNING"}` with HTTP 200, so monitoring sees it without a load
balancer taking the app out of service. "Run backup now" is `POST /api/admin/backups/run` (Super Admin).

In the app, **Administration → Backups** (`BACKUP_VIEW`) shows the last successful backup, its size, the next
scheduled run, the health of each copy and the history with the primary and second copy of every run. Super Admins
also get **Run backup now**, holders of `DATA_EXPORT` get **Export all data** (with download progress), and while the
health check fails every Super Admin sees a red banner across the top of every page with a link to the page.

## Where backups go

**Development (this machine):** `BACKUP_TARGET=LOCAL`, `BACKUP_LOCAL_PATH=D:/team-ops-backups` (outside the project
folder). Use forward slashes in `.env`.

**Deployment (S3 or Lightsail object storage):** configuration only, no code change.

```properties
BACKUP_TARGET=S3
BACKUP_S3_BUCKET=team-ops-backups
BACKUP_S3_PREFIX=backups
BACKUP_S3_REGION=ap-south-1
# Lightsail object storage: https://s3.<region>.amazonaws.com ; leave blank for AWS S3
BACKUP_S3_ENDPOINT=
# Lightsail bucket access keys; leave blank on EC2 to use the instance role
BACKUP_S3_ACCESS_KEY=
BACKUP_S3_SECRET_KEY=
MYSQLDUMP_PATH=/usr/bin/mysqldump
```

Give the key only `s3:PutObject`, `s3:GetObject` and `s3:DeleteObject` on the bucket/prefix. Turn on bucket
versioning or object lock if you want deleted or overwritten backups to stay recoverable. On AWS S3 objects are also
stored with server-side encryption (SSE-S3).

## Second copy

The brief asks for an optional second copy location: another disk or NAS folder, or a bucket in another account or
region. Every backup is then written to both targets, each copy is verified and kept (30 daily, 12 monthly) on its
own, and a broken first target does not stop the second copy.

```properties
# Another folder (a different physical disk or a network share):
BACKUP_SECONDARY_TARGET=LOCAL
BACKUP_SECONDARY_LOCAL_PATH=E:/team-ops-backups-copy

# ...or another S3-compatible bucket (same settings as BACKUP_S3_*):
BACKUP_SECONDARY_TARGET=S3
BACKUP_SECONDARY_S3_BUCKET=team-ops-backups-dr
BACKUP_SECONDARY_S3_REGION=ap-southeast-2
BACKUP_SECONDARY_S3_ENDPOINT=
BACKUP_SECONDARY_S3_ACCESS_KEY=
BACKUP_SECONDARY_S3_SECRET_KEY=
```

Leave `BACKUP_SECONDARY_TARGET` blank for no second copy. A second target that is the same as the first is reported
as a failed second copy. Use different credentials for the second bucket than for the first where you can, so one
leaked key cannot delete both. Restoring works the same from either copy: both hold the same file and `.sha256`.

## The encryption key

Without `BACKUP_ENCRYPTION_KEY` no backup can be restored. Keep a copy **outside the server** (password manager,
printed in the safe). Never commit it. To generate one: `openssl rand -base64 32`. Changing the key does not re-encrypt
old backups: keep the old key until the last backup made with it has aged out (12 months).

## Restoring

You need a JDK 21 (`java`) and the MySQL client (`mysql`). The scripts verify the checksum, decrypt, create the
database you name and load the dump, copy the uploads to a folder, and write `BACKUP_RESTORED` to the audit log of the
app database. They refuse a database that already has tables unless you pass `-Replace` / `--replace`, and restoring
over the live database (the one in `DB_URL`) also asks you to type its name.

The app's own MySQL account can only use its own databases, so restoring into a **new** database needs an account
that may create databases, usually `root` (you are asked for its password). The key and the app's `DB_URL` are read
from `backend/.env`; the key can also come from the `BACKUP_ENCRYPTION_KEY` environment variable or a prompt.

**Windows**

```powershell
.\scripts\restore-backup.ps1 -BackupFile D:\team-ops-backups\team-ops-backup-20261016T203000Z.tobk `
    -Database team_ops_board_restore_test -User root
```

**Linux**

```bash
scripts/restore-backup.sh --file /var/backups/team-ops-backup-20261016T203000Z.tobk \
    --database team_ops_board_restore_test --user root
```

Options: `-Checksum` / `--checksum` (the checksum from the backup history, when the `.sha256` file is not available),
`-UploadsTo` / `--uploads-to` (default: `restore-<database>-uploads` next to the backup), `-NoAudit` / `--no-audit`.
From S3, download the `.tobk` and its `.sha256` first (`aws s3 cp s3://bucket/backups/<file> .`).

To only check a file, without MySQL: `BACKUP_ENCRYPTION_KEY=... java scripts/BackupTool.java verify <file>`.

### Restoring production for real

1. Stop the backend.
2. Restore into a new database (as above) and check it.
3. Point `DB_URL` at it (or restore with `-Database team_ops_board -Replace`, typing the name to confirm), and replace
   `FILE_STORAGE_DIR` with the restored uploads folder.
4. Start the backend. Flyway sees the migrations already applied (`flyway_schema_history` is in the backup).
5. Sign in, check recent records and a few attachments.

## Monthly restore test

On the first working day of each month:

1. Open the backup status page (or `GET /api/admin/backups/status`): healthy, last success within 26 hours.
2. Take the newest backup and restore it into `team_ops_board_restore_test` (`-Replace` if it exists from last month).
3. Check the row counts of a few tables against the live app (users, tasks, tickets), and open two or three restored
   uploads.
4. Optionally start a second backend against it (`DB_URL=...team_ops_board_restore_test`,
   `FILE_STORAGE_DIR=<restored uploads>`, `SERVER_PORT=8081`, `APP_SCHEDULING_ENABLED=false`) and sign in.
5. Drop the test database and delete the restored uploads folder.
6. Note the date, backup file and result in the operations log. The restore is already in the audit log
   (`BACKUP_RESTORED`).

## Export all data

`GET /api/admin/data-export` (`DATA_EXPORT`, Super Admin only by default) streams one ZIP: every table as
`tables/<table>.csv`, a `tables.csv` with row counts, every uploaded file under `uploads/`, and a `README.txt`. Rows in
the recycle bin are included and marked (`_in_recycle_bin` = `yes`). Password and refresh-token hashes are replaced by
`[redacted]`. Times are UTC. Each export is audited (`DATA_EXPORTED`).

The export is complete only for an exporter who also holds `FINANCE_VIEW` (the Super Admin by default). Without it,
finance data stays out (`FinanceRedaction`, worked out from the entity model, so new finance fields are covered
automatically):
- the Finance Tracker's tables and the files they point at (receipts), in `uploads/` and in `tables/files.csv`;
- money columns in other tables (`@AuditFinance` fields, such as approval amounts and vendor contract values);
- the names, titles and details of rows about a finance record (audit entries, notifications, recycle bin items), and
  the details of audit entries that hold finance amounts.

A full copy of everything, finance included, is the encrypted backup itself.

## Break-glass access

For when nobody can sign in as Super Admin: the only Super Admin forgot their password, lost access to their mailbox,
was disabled by mistake, or left the company.

### Prevention: keep a second Super Admin

- [ ] Keep **two** active Super Admin accounts at all times, held by two different people (for example the owner and
      the IT lead). The app already refuses to disable or demote the last active Super Admin.
- [ ] Each uses their own work email and a unique password (12+ characters) in a password manager, and signs in at
      least once a quarter so the account is known to work.
- [ ] Write down who holds them, and where the server access (SSH/RDP account, `backend/.env` or the service's
      environment) and the backup encryption key are kept: a sealed envelope or a shared vault entry both can open.
- [ ] When a Super Admin leaves, the other one disables their account the same day and makes someone else the second
      Super Admin.
- [ ] Twice a year (with the monthly restore test in January and July): check that both accounts are active, look for
      `BREAK_GLASS_USED` entries in the audit log, and practise the procedure below against the restore test database.

### Recovery procedure

The tool runs **on the server only**. It is the backend jar started once as a command-line program, without the web
server, against the app database, by someone signed in to the server (SSH or RDP) with access to its configuration.
There is no web endpoint for it. It needs an interactive terminal: the temporary password is typed there (or generated
with `-GeneratePassword` / `--generate-password` and shown once), never passed on the command line, and you must type
the email again to confirm. The password must be changed at the next sign-in and every session of the account is
signed out. Every use is recorded in the audit log as `BREAK_GLASS_USED` (with the operating system user and host),
next to the usual `USER_CREATED`, `USER_PASSWORD_RESET` or `USER_ENABLED` entry.

1. Try the normal way first: if another Super Admin can sign in, they reset the password or re-enable the account in
   Admin → Users. Break-glass is for when nobody can.
2. Sign in to the server. Make sure the backend jar is there (`backend/target/team-ops-board-backend-*.jar`; build it
   with `mvnw -DskipTests package` if needed), and run the tool with the same configuration as the app
   (`backend/.env` or the service's environment).
3. Run one of:

   **Forgotten password, or a disabled Super Admin** (re-enables the account; works only for Super Admins):

   ```powershell
   .\scripts\break-glass.ps1 -Command reset-password -Email rakesh@example.com
   ```
   ```bash
   sudo -u teamops scripts/break-glass.sh reset-password --email rakesh@example.com
   ```

   **The Super Admin left, or the account is unusable** (creates a new Super Admin; the employee ID and email must not
   be in use):

   ```powershell
   .\scripts\break-glass.ps1 -Command create-super-admin -EmployeeId E-0002 -Email it.admin@example.com -FirstName IT -LastName Admin -Department IT
   ```
   ```bash
   sudo -u teamops scripts/break-glass.sh create-super-admin --employee-id E-0002 --email it.admin@example.com --first-name IT --last-name Admin --department IT
   ```

   Exit codes: 0 done; 1 refused (email typed differently, passwords differ, not a Super Admin, email in use); 2 wrong
   usage or no interactive terminal.
4. Sign in with the temporary password and choose a new one.
5. Clean up in the app: disable the account of anyone who has left (Admin → Users), make sure there are two active
   Super Admins again, and check the audit log for the `BREAK_GLASS_USED` entry.
6. Record why break-glass was used, by whom and when in the operations log.

If the server itself is lost, restore it from a backup first (see [Restoring](#restoring)), then use break-glass on
the restored server.
