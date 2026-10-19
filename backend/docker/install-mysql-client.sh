#!/usr/bin/env bash
# Installs the MySQL 8.4 LTS client tools (mysql, mysqldump) from Oracle's signed APT repository on Ubuntu 24.04.
# The backup runs mysqldump with MySQL-only options (--set-gtid-purged), and the restore script loads the dump with
# mysql, so both must be MySQL's own client, matching the mysql:8.4 server. Ubuntu's own packages are 8.0.
set -euo pipefail

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y --no-install-recommends ca-certificates curl gnupg
curl -fsSL https://repo.mysql.com/RPM-GPG-KEY-mysql-2023 | gpg --dearmor -o /usr/share/keyrings/mysql.gpg
echo "deb [signed-by=/usr/share/keyrings/mysql.gpg] http://repo.mysql.com/apt/ubuntu noble mysql-8.4-lts" \
  > /etc/apt/sources.list.d/mysql.list
apt-get update
apt-get install -y --no-install-recommends mysql-community-client
apt-get purge -y gnupg
apt-get autoremove -y
rm -rf /var/lib/apt/lists/*
mysqldump --version
