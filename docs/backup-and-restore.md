# Backups and restore

Phase X3 of the expansion brief: nightly encrypted backups of the database **and** the uploaded files, kept off the
app server, with a tested way back.

## What a backup is

One file per run, `team-ops-backup-<UTC time>.tobk`, plus `<file>.sha256` beside it:

1. `mysqldump` (MySQL 8.4 client, `MYSQLDUMP_PATH`) takes one consistent InnoDB snapshot of the app database. The dump
   has no `CREATE DATABASE`/`USE`, so it can be restored into any database name.
2. The dump, every file under `FILE_STORAGE_DIR` (as `uploads/...`) and a `manifest.json` are zipped.
3. The ZIP is encrypted with AES-256-GCM in 1 MiB authenticated chunks, using `BACKUP_ENCRYPTION_KEY` (base64 of
   32 random bytes, in `backend/.env` only).
4. The SHA-256 of the encrypted file is stored in the backup history (`backup_runs.checksum`) and in the `.sha256` file.
5. After writing, the stored copy is read back from the target: the checksum must match, every chunk must decrypt and
   the ZIP must contain a non-empty `database.sql`. Only then does the run count as a success.

Every run is in the history (start, end, size, status, checksum, target, error) and in the audit log
(`BACKUP_COMPLETED` / `BACKUP_FAILED`).

## Schedule, retention and alerts

| Setting | Default | |
|---|---|---|
| `BACKUP_ENABLED` | `true` | nightly schedule on/off ("Run backup now" works either way) |
| `BACKUP_CRON` | `0 0 2 * * *` | 02:00 in `APP_TIME_ZONE` |
| `BACKUP_KEEP_DAILY` / `BACKUP_KEEP_MONTHLY` | 30 / 12 | the newest backup of each of the last 30 days and each of the last 12 months is kept; older files are deleted after each successful run (`BACKUP_REMOVED` in the audit log). Days without a backup do not count, so failed nights never delete the last good backups |
| `BACKUP_MAX_AGE_HOURS` | 26 | alert threshold |

The status endpoint (`GET /api/admin/backups/status`, `BACKUP_VIEW`) and `/actuator/health` report a problem when the
last successful backup is older than 26 hours, the last run failed, no backup has ever completed, or the key is
missing. `/actuator/health` then answers `{"status":"WARNING"}` with HTTP 200, so monitoring sees it without a load
balancer taking the app out of service. "Run backup now" is `POST /api/admin/backups/run` (Super Admin).

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
