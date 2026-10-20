#!/usr/bin/env bash
# Installs the MySQL 8.4 LTS client tools (mysql, mysqldump) from Oracle's signed APT repository on Ubuntu 24.04.
# The backup runs mysqldump with MySQL-only options (--set-gtid-purged), and the restore script loads the dump with
# mysql, so both must be MySQL's own client, matching the mysql:8.4 server. Ubuntu's own packages are 8.0.
#
# Signing key: "MySQL Release Engineering", fingerprint pinned below. Oracle extends the key's expiry and publishes
# the refreshed copy under a new file name (RPM-GPG-KEY-mysql-2023 expired on 2025-10-22; the -2025 file is the same
# key, valid to 2027-10-22). When apt reports EXPKEYSIG again, point MYSQL_KEY_URL at the newest file from
# https://repo.mysql.com/ and, if Oracle rotated to a new key, update MYSQL_KEY_FINGERPRINT from
# https://dev.mysql.com/doc/refman/8.4/en/checking-gpg-signature.html.
set -euo pipefail

MYSQL_KEY_URL=https://repo.mysql.com/RPM-GPG-KEY-mysql-2025
MYSQL_KEY_FINGERPRINT=BCA43417C3B485DD128EC6D4B7B3B788A8D3785C
KEYRING=/usr/share/keyrings/mysql.gpg

fail() {
  echo "install-mysql-client: $*" >&2
  exit 1
}

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y --no-install-recommends ca-certificates curl gnupg

key_file=$(mktemp)
curl -fsSL --proto '=https' --tlsv1.2 "$MYSQL_KEY_URL" -o "$key_file" \
  || fail "could not download the MySQL signing key from $MYSQL_KEY_URL"

# The file must hold exactly the pinned key, and that key must not have expired.
key_info=$(gpg --batch --show-keys --with-colons "$key_file" 2>/dev/null) \
  || fail "$MYSQL_KEY_URL is not a readable OpenPGP key"
primary_keys=$(awk -F: '$1 == "pub"' <<<"$key_info" | wc -l)
fingerprint=$(awk -F: '$1 == "pub" { getline; if ($1 == "fpr") print $10 }' <<<"$key_info")
expires=$(awk -F: '$1 == "pub" { print $7 }' <<<"$key_info")
[ "$primary_keys" -eq 1 ] && [ "$fingerprint" = "$MYSQL_KEY_FINGERPRINT" ] \
  || fail "unexpected MySQL signing key from $MYSQL_KEY_URL: got fingerprint '${fingerprint:-none}'," \
          "expected $MYSQL_KEY_FINGERPRINT. Check the key and fingerprint against MySQL's documentation."
if [ -n "$expires" ] && [ "$expires" -le "$(date +%s)" ]; then
  fail "the MySQL signing key from $MYSQL_KEY_URL expired on $(date -u -d "@$expires" +%F)." \
       "Oracle publishes refreshed copies at https://repo.mysql.com/RPM-GPG-KEY-mysql-<year>; update MYSQL_KEY_URL."
fi
echo "MySQL signing key $fingerprint OK${expires:+ (expires $(date -u -d "@$expires" +%F))}"

gpg --batch --dearmor -o "$KEYRING" "$key_file"
rm -f "$key_file"
echo "deb [signed-by=$KEYRING] https://repo.mysql.com/apt/ubuntu noble mysql-8.4-lts" \
  > /etc/apt/sources.list.d/mysql.list
apt-get update
apt-get install -y --no-install-recommends mysql-community-client
apt-get purge -y gnupg
apt-get autoremove -y
rm -rf /var/lib/apt/lists/*
mysqldump --version
