#!/usr/bin/env bash
# Restores a Team Ops Board backup (.tobk) into a MySQL database you name, after checking its checksum.
#
#   scripts/restore-backup.sh --file /path/team-ops-backup-20261016T203000Z.tobk --database team_ops_board_restore_test
#
# Steps: verify the SHA-256 checksum (the .sha256 file next to the backup, or --checksum from the backup history),
# decrypt with BACKUP_ENCRYPTION_KEY (environment, else backend/.env, else a prompt), create the database and load
# database.sql, copy the uploaded files to --uploads-to (default: a new folder next to the backup), and record
# BACKUP_RESTORED in the app database's audit log (DB_URL's database or --audit-database; --no-audit skips it).
# An existing database with tables is refused unless --replace is given; restoring over the live database also asks
# you to type its name. Needs a JDK 21 (java) and the MySQL client (mysql). See docs/backup-and-restore.md.
#
# Options: --file F --database D [--checksum HEX] [--env-file backend/.env] [--host H] [--port P] [--user U]
#          [--mysql PATH] [--java PATH] [--uploads-to DIR] [--audit-database D] [--no-audit] [--replace]
# The MySQL password comes from MYSQL_PASSWORD, else DB_PASSWORD in the env file (same user), else a prompt.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
file="" database="" checksum="" env_file="$script_dir/../backend/.env" host="" port="" user=""
mysql_bin="mysql" java_bin="" uploads_to="" audit_db="" no_audit=0 replace=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --file) file="$2"; shift 2 ;;
    --database) database="$2"; shift 2 ;;
    --checksum) checksum="$2"; shift 2 ;;
    --env-file) env_file="$2"; shift 2 ;;
    --host) host="$2"; shift 2 ;;
    --port) port="$2"; shift 2 ;;
    --user) user="$2"; shift 2 ;;
    --mysql) mysql_bin="$2"; shift 2 ;;
    --java) java_bin="$2"; shift 2 ;;
    --uploads-to) uploads_to="$2"; shift 2 ;;
    --audit-database) audit_db="$2"; shift 2 ;;
    --no-audit) no_audit=1; shift ;;
    --replace) replace=1; shift ;;
    -h|--help) sed -n '2,17p' "$0"; exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done

die() { echo "ERROR: $*" >&2; exit 1; }
[[ -n "$file" && -n "$database" ]] || die "--file and --database are required (see --help)"
[[ "$database" =~ ^[A-Za-z0-9_]{1,64}$ ]] || die "Database name may only contain letters, digits and _"
[[ -f "$file" ]] || die "Backup file not found: $file"
file="$(cd "$(dirname "$file")" && pwd)/$(basename "$file")"

env_value() {
  [[ -f "$env_file" ]] || return 0
  grep -E "^[[:space:]]*$1[[:space:]]*=" "$env_file" | tail -n 1 | sed -E "s/^[^=]*=//; s/^[[:space:]]+//; s/[[:space:]]+$//"
}
sql_text() { local v="${1//\\/\\\\}"; v="${v//\'/\'\'}"; printf "'%s'" "$v"; }
option_value() { local v="${1//\\/\\\\}"; v="${v//\"/\\\"}"; printf '"%s"' "$v"; }

# --- Connection: options, else DB_URL / DB_USERNAME / DB_PASSWORD from the env file ---
db_url="$(env_value DB_URL)"
app_db="" url_host="localhost" url_port="3306"
if [[ "$db_url" =~ ^jdbc:mysql://([^:/?]+)(:([0-9]+))?/([A-Za-z0-9_\$-]+) ]]; then
  url_host="${BASH_REMATCH[1]}"; [[ -n "${BASH_REMATCH[3]}" ]] && url_port="${BASH_REMATCH[3]}"; app_db="${BASH_REMATCH[4]}"
fi
host="${host:-$url_host}"; port="${port:-$url_port}"
env_user="$(env_value DB_USERNAME)"
user="${user:-$env_user}"
[[ -n "$user" ]] || read -r -p "MySQL user: " user
password="${MYSQL_PASSWORD:-}"
if [[ -z "$password" && "$user" == "$env_user" ]]; then password="$(env_value DB_PASSWORD)"; fi
if [[ -z "$password" ]]; then read -r -s -p "MySQL password for $user: " password; echo; fi
if [[ $no_audit -eq 1 ]]; then audit_db=""; else audit_db="${audit_db:-$app_db}"; fi

# --- Tools and key (the key reaches Java through the environment only) ---
if [[ -z "$java_bin" ]]; then java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"; fi
key="${BACKUP_ENCRYPTION_KEY:-$(env_value BACKUP_ENCRYPTION_KEY)}"
if [[ -z "$key" ]]; then read -r -s -p "BACKUP_ENCRYPTION_KEY: " key; echo; fi

work="$(mktemp -d)"
options="$(mktemp)"
chmod 600 "$options"
trap 'rm -rf "$work" "$options"' EXIT
printf '[client]\nhost=%s\nport=%s\nuser=%s\npassword=%s\ndefault-character-set=utf8mb4\n' \
  "$(option_value "$host")" "$port" "$(option_value "$user")" "$(option_value "$password")" > "$options"
run_mysql() { "$mysql_bin" --defaults-extra-file="$options" "$@"; }

echo "1/5 Verifying checksum and decrypting $(basename "$file")"
extract_dir="$work/backup"
tool_args=("$script_dir/BackupTool.java" extract "$file" "$extract_dir")
[[ -n "$checksum" ]] && tool_args+=("$checksum")
BACKUP_ENCRYPTION_KEY="$key" "$java_bin" "${tool_args[@]}" || die "The backup could not be verified or decrypted"
checksum_used="${checksum:-$(awk '{print $1; exit}' "$file.sha256")}"

echo "2/5 Preparing database $database on $host:$port"
tables="$(run_mysql -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$database'")"
exists="$(run_mysql -N -B -e "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = '$database'")"
if [[ "$tables" -gt 0 && $replace -eq 0 ]]; then
  die "Database $database already has $tables tables. Choose another name, or pass --replace to drop and replace it."
fi
if [[ -n "$app_db" && "$database" == "$app_db" ]]; then
  echo "WARNING: $database is the app's own database (DB_URL). Its current data will be replaced by the backup." >&2
  read -r -p "Type the database name ($database) to continue: " typed
  [[ "$typed" == "$database" ]] || die "Restore cancelled"
fi
create_hint="The MySQL user $user may not create or drop $database. Run again with --user root (or another account allowed to create databases)."
if [[ "$exists" -gt 0 && $replace -eq 1 ]]; then run_mysql -e "DROP DATABASE \`$database\`" || die "$create_hint"; fi
run_mysql -e "CREATE DATABASE IF NOT EXISTS \`$database\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci" || die "$create_hint"

echo "3/5 Loading database.sql (this can take a while)"
run_mysql "$database" < "$extract_dir/database.sql"
restored_tables="$(run_mysql -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$database'")"

echo "4/5 Copying uploaded files"
uploads_to="${uploads_to:-$(dirname "$file")/restore-$database-uploads}"
file_count=0
if [[ -d "$extract_dir/uploads" ]]; then
  if [[ -d "$uploads_to" && -n "$(ls -A "$uploads_to")" ]]; then
    die "Uploads folder $uploads_to is not empty; pass an empty or new folder with --uploads-to"
  fi
  mkdir -p "$uploads_to"
  cp -R "$extract_dir/uploads/." "$uploads_to/"
  file_count="$(find "$uploads_to" -type f | wc -l | tr -d ' ')"
fi

echo "5/5 Recording the restore in the audit log"
if [[ -z "$audit_db" ]]; then
  echo "WARNING: no audit database (--no-audit, or no DB_URL in the env file): the restore is not recorded." >&2
else
  details="JSON_OBJECT('file', $(sql_text "$(basename "$file")"), 'checksum', $(sql_text "$checksum_used"), \
'database', $(sql_text "$database"), 'tables', $restored_tables, 'uploadFiles', $file_count, \
'uploadsFolder', $(sql_text "$uploads_to"), 'osUser', $(sql_text "${USER:-unknown}"), \
'machine', $(sql_text "$(hostname)"), 'script', 'restore-backup.sh')"
  run_mysql "$audit_db" -e "INSERT INTO audit_logs (action, entity_type, details, user_agent) VALUES \
('BACKUP_RESTORED', 'BACKUP', $details, 'restore-backup.sh')" \
    || echo "WARNING: the restore worked but could not be recorded in $audit_db.audit_logs" >&2
fi

echo
echo "Restored $restored_tables tables into $database and $file_count files into $uploads_to."
echo "To try it, point a copy of the app at it: DB_URL=...:$port/$database and FILE_STORAGE_DIR=$uploads_to"
