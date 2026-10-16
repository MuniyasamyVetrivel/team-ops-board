#!/usr/bin/env bash
# Break-glass recovery on the server: create a new Super Admin, or reset a Super Admin's password.
#
#   scripts/break-glass.sh reset-password --email rakesh@example.com
#   scripts/break-glass.sh create-super-admin --email it.admin@example.com --first-name IT --last-name Admin
#
# For when nobody can sign in as Super Admin (see "Break-glass access" in docs/backup-and-restore.md). Runs the
# backend jar once as a command-line tool, without the web server, against the database in the app's .env (or the
# environment). It asks you to type the email again and then the temporary password (never on the command line); the
# password must be changed at the next sign-in and every session of that account is signed out; reset-password works
# only for Super Admins and re-enables a disabled account; every use is recorded in the audit log as BREAK_GLASS_USED,
# with your Linux user and the host name. Run it in an interactive terminal on the server itself, as the user the app
# runs as (for example: sudo -u teamops scripts/break-glass.sh ...). Needs Java 21 and the built backend jar.
#
# Options: --email E [--first-name F] [--last-name L] [--department CODE] [--generate-password]
#          [--jar PATH] [--app-dir DIR (folder with .env; default backend/)] [--java PATH]
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
command="${1:-}"
[[ "$command" == "create-super-admin" || "$command" == "reset-password" ]] || {
  echo "Usage: $0 create-super-admin|reset-password --email E [options] (see the top of this file)" >&2; exit 2; }
shift
email="" first="" last="" department="" generate=0 jar="" app_dir="$script_dir/../backend" java_bin=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --email) email="$2"; shift 2 ;;
    --first-name) first="$2"; shift 2 ;;
    --last-name) last="$2"; shift 2 ;;
    --department) department="$2"; shift 2 ;;
    --generate-password) generate=1; shift ;;
    --jar) jar="$2"; shift 2 ;;
    --app-dir) app_dir="$2"; shift 2 ;;
    --java) java_bin="$2"; shift 2 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done
[[ -n "$email" ]] || { echo "--email is required" >&2; exit 2; }
[[ -t 0 ]] || { echo "Run this in an interactive terminal on the server." >&2; exit 2; }

app_dir="$(cd "$app_dir" && pwd)"
if [[ -z "$jar" ]]; then
  jar="$(ls -t "$app_dir"/target/team-ops-board-backend-*.jar 2>/dev/null | grep -v '\.original$' | head -n 1 || true)"
  [[ -n "$jar" ]] || { echo "No backend jar found in $app_dir/target. Build it first: ./mvnw -DskipTests package" >&2; exit 2; }
fi
if [[ -z "$java_bin" ]]; then java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"; fi

args=(-jar "$jar"
  # A one-off command-line run: no web server, no background jobs, quiet logs.
  --spring.main.web-application-type=none --app.scheduling.enabled=false --spring.main.banner-mode=off
  --logging.level.root=WARN
  "--break-glass=$command" "--email=$email")
[[ -n "$first" ]] && args+=("--first-name=$first")
[[ -n "$last" ]] && args+=("--last-name=$last")
[[ -n "$department" ]] && args+=("--department=$department")
[[ $generate -eq 1 ]] && args+=(--generate-password)

# The app reads .env from its working folder.
cd "$app_dir"
exec "$java_bin" "${args[@]}"
