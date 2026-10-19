# Deployment: Amazon Lightsail + Docker Compose + Cloudflare Tunnel

Team Ops Board runs on **one Lightsail instance** (Ubuntu 24.04 LTS, 4 GB RAM, Sydney `ap-southeast-2`) as a Docker
Compose stack, and is reachable **only** through a Cloudflare Tunnel at **https://ops.tritusa.com.au**. The instance
publishes no ports: `cloudflared` makes an outbound connection to Cloudflare, which terminates HTTPS.

[What runs](#what-runs) · [First deployment](#first-deployment) · [Checks after deploying](#checks-after-deploying) ·
[Update](#update) · [Rollback](#rollback) · [Backups and restore](#backups-and-restore) ·
[Break-glass](#break-glass-access) · [Day-to-day](#day-to-day)

## What runs

```
 Browser ──HTTPS──▶ Cloudflare (ops.tritusa.com.au) ◀──outbound tunnel── cloudflared ┐
                                                                                     │ edge 172.28.0.0/24
                                                 nginx :8080 (SPA, /api proxy) ◀─────┘
                                                     │ app 172.28.1.0/24
                                                 backend :8080 (Spring Boot, profile "prod")
                                                     │ db 172.28.2.0/24 (internal: no internet)
                                                 mysql :3306 (8.4)
```

| Service | Image | Memory limit | Volumes | Notes |
|---|---|---|---|---|
| `cloudflared` | `cloudflare/cloudflared:<CLOUDFLARED_VERSION>` | 128 MB | — | Token from `TUNNEL_TOKEN`. Distroless, so no health check; Docker restarts it, Cloudflare shows the tunnel's health |
| `nginx` | `team-ops-board-nginx:<APP_VERSION>` (`frontend/Dockerfile`) | 128 MB | — | Non-root Nginx: the built app, `/api` proxy, gzip, 10 MB uploads, security headers and CSP, real client IP from `CF-Connecting-IP` (`deploy/nginx/team-ops.conf.template`) |
| `backend` | `team-ops-board-backend:<APP_VERSION>` (`backend/Dockerfile`, target `runtime`) | 1.5 GB (heap 60%) | `uploads`, `backups` | JRE 21, non-root (uid 10001), read-only root file system, MySQL 8.4 client for the nightly `mysqldump` |
| `mysql` | `mysql:8.4` | 1.25 GB (512 MB buffer pool) | `mysql-data` | Only on the internal `db` network |
| `tools` | `team-ops-board-tools:<APP_VERSION>` (target `tools`) | 1 GB | `uploads`, `backups` | Profile `tools`: never runs on its own; for restores and restore tests |

- **Configuration:** `docker-compose.prod.yml` reads `.env.prod` (from `.env.prod.example`). Each container gets
  only the variables it needs.
- **The `prod` Spring profile** (`application-prod.yml`):
  - Flyway on, with clean disabled; demo data off.
  - Only `/actuator/health` is exposed, without details. Nginx never serves `/actuator` publicly.
  - CORS allows `https://ops.tritusa.com.au` only, and the refresh cookie is `Secure`, `HttpOnly` and `SameSite=Strict`.
  - Logs go to stdout, which Docker rotates (10 MB × 5 per container).
  - The business time zone is `Asia/Kolkata`.
  - Forwarded headers are trusted from the Nginx network (172.28.1.0/24) only.
- **The real client IP** for audit logs travels from Cloudflare's `CF-Connecting-IP` header, to Nginx (which trusts it
  only from the tunnel network), to `X-Forwarded-For`, which Nginx overwrites. The backend trusts that header only
  from Nginx.
- **Backups:**
  - The first copy is on the `backups` volume (`/var/backups/team-ops`).
  - The second copy goes to a Lightsail object storage bucket in Sydney, written with an access key scoped to that
    bucket only.
  - The rules are in [backup-and-restore.md](backup-and-restore.md).

The stack's subnets (172.28.0.0/22) don't overlap Lightsail's default VPC (172.26.0.0/16). If your instance uses
172.28.x, change the three subnets in `docker-compose.prod.yml`, `set_real_ip_from` in the Nginx template and
`server.tomcat.remoteip.internal-proxies` in `application-prod.yml` together (`ProdProfileTest` checks the last one).

## First deployment

You need: the Lightsail and Cloudflare (`tritusa.com.au` zone) accounts, SSH access, and the company password manager
for the secrets.

### 1. Create the instance

1. Lightsail → **Create instance** → region **Sydney (ap-southeast-2)** → **Linux/Unix** → **OS Only: Ubuntu 24.04
   LTS** → the **4 GB** plan. Name it `team-ops-prod`.
2. **Networking** tab, IPv4 and IPv6 firewalls:
   - Delete the **HTTP (80)** rule.
   - Keep **SSH (22)** only, and restrict it to your office IP, or tick "Allow Lightsail browser SSH" only.
   - Nothing else: the tunnel needs no inbound port.
3. **Snapshots** tab: turn on **automatic snapshots** (daily). They are a second safety net next to the app's own
   backups.

### 2. Prepare Ubuntu

SSH in (as `ubuntu`), then:

```bash
sudo apt-get update && sudo apt-get -y upgrade
sudo timedatectl set-timezone UTC          # the app works in UTC and Asia/Kolkata on its own

# 2 GB swap: headroom for image builds and memory peaks on a 4 GB box
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
echo 'vm.swappiness=10' | sudo tee /etc/sysctl.d/99-swappiness.conf && sudo sysctl --system

# Automatic security updates
sudo apt-get install -y unattended-upgrades && sudo dpkg-reconfigure -f noninteractive unattended-upgrades
```

Install Docker Engine and the Compose plugin from Docker's repository
(https://docs.docker.com/engine/install/ubuntu/):

```bash
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin git
sudo usermod -aG docker ubuntu && newgrp docker
docker compose version
```

### 3. Create the backup bucket

1. Lightsail → **Storage** → **Create bucket** → **Sydney** → a plan that fits (5 GB is plenty to start) → name such
   as `tritusa-team-ops-backups`. Keep it **private** (the default: no public access).
2. Bucket → **Objects** tab → **Versioning**: on. An overwritten or deleted backup then stays recoverable for a
   while.
3. Bucket → **Permissions** → **Access keys** → **Create access key**. Lightsail access keys work on this bucket only,
   which is the "bucket-scoped key" the setup needs. Copy the access key ID and secret straight into the password
   manager; the secret is shown once.
4. Don't attach the bucket to any instance or grant other resource access; the key is the only way in.

### 4. Create the Cloudflare Tunnel

1. Cloudflare dashboard → **Zero Trust** → **Networks** → **Tunnels** → **Create a tunnel** → **Cloudflared** → name
   `team-ops-prod`.
2. On the install screen choose **Docker** and copy only the token, the long value after `--token`. It goes into
   `TUNNEL_TOKEN`. Don't run the command shown; Compose runs cloudflared.
3. **Public hostname** → subdomain `ops`, domain `tritusa.com.au`, path empty → service **HTTP** `nginx:8080`. Save.
   Cloudflare creates the DNS record for `ops.tritusa.com.au` itself.
4. In the zone's settings for `tritusa.com.au`:
   - **SSL/TLS → Edge Certificates:** Always Use HTTPS on, minimum TLS 1.2.
   - **Speed → Optimization:** Rocket Loader **off**.
   - **Scrape Shield:** Email Address Obfuscation **off**.

   Both of the latter inject scripts that the app's Content-Security-Policy blocks.
5. Optional, recommended: **Zero Trust → Access → Applications** → a self-hosted application for
   `ops.tritusa.com.au` that only allows `@tritusa.com.au` emails. People then pass Cloudflare Access before they reach
   the sign-in page.

### 5. Get the code

```bash
sudo mkdir -p /opt/team-ops-board && sudo chown ubuntu:ubuntu /opt/team-ops-board
git clone https://github.com/MuniyasamyVetrivel/team-ops-board.git /opt/team-ops-board
cd /opt/team-ops-board
git checkout <release tag or commit>       # what you tested (see "Update")
```

A short alias, used in the rest of this guide (add it to `~/.bashrc`):

```bash
alias dc='docker compose --env-file /opt/team-ops-board/.env.prod -f /opt/team-ops-board/docker-compose.prod.yml'
```

### 6. Fill in `.env.prod`

```bash
cp .env.prod.example .env.prod && chmod 600 .env.prod
openssl rand -base64 32 | tr -d '/+='     # DB_PASSWORD, then again for MYSQL_ROOT_PASSWORD
openssl rand -base64 64 | tr -d '\n'      # JWT_SECRET
openssl rand -base64 32                   # BACKUP_ENCRYPTION_KEY
nano .env.prod
```

Replace every `CHANGE_ME`:
- `APP_VERSION`: the commit you checked out (`git rev-parse --short HEAD`).
- `CLOUDFLARED_VERSION`: the current release from https://github.com/cloudflare/cloudflared/releases.
- `TUNNEL_TOKEN`, the two MySQL passwords, `JWT_SECRET` and `BACKUP_ENCRYPTION_KEY`.
- `BOOTSTRAP_ADMIN_*`: the first Super Admin, as in [go-live-data.md](go-live-data.md).
- `BACKUP_SECONDARY_S3_BUCKET` and the bucket's access key and secret.

**Store `BACKUP_ENCRYPTION_KEY`, the MySQL passwords and `JWT_SECRET` in the password manager now.** Without the
backup key no backup can ever be restored. Check that no placeholder is left:

```bash
grep -n CHANGE_ME .env.prod || echo "all set"
```

### 7. Build and start

```bash
cd /opt/team-ops-board
dc build                 # 5–10 minutes the first time (Maven and npm downloads)
dc up -d
dc ps                    # mysql, backend and nginx become "healthy"; cloudflared "running"
dc logs -f backend       # Flyway migrates the empty database, then "Started TeamOpsApplication"
```

The first start creates the database, runs every migration, and creates the bootstrap Super Admin.

### 8. First sign-in

1. Open https://ops.tritusa.com.au and sign in with `BOOTSTRAP_ADMIN_EMAIL` and the temporary password. Choose your
   own password when asked.
2. Remove the temporary password from the configuration. Set `BOOTSTRAP_ADMIN_PASSWORD=` (empty) in `.env.prod`, then
   run `dc up -d backend`.
3. Add a **second Super Admin** (break-glass prevention, [backup-and-restore.md](backup-and-restore.md)), then load
   the team ([go-live-data.md](go-live-data.md) from step 8).

To move existing data from another installation instead of starting empty, see
[Restore a backup into production](#restore-a-backup-into-production).

## Checks after deploying

Run these after the first deployment and after every update:

```bash
dc ps                                                    # all healthy / running
sudo ss -tlnp | grep -vE '127\.0\.0\.5[34]'             # only sshd (22) listens (besides the local DNS stub)
dc exec backend mysqldump --version                      # MySQL 8.4 client present for the backups
curl -sI https://ops.tritusa.com.au | grep -iE 'strict-transport|content-security|x-frame|x-content-type'
curl -s -o /dev/null -w '%{http_code}\n' https://ops.tritusa.com.au/actuator/health   # 404: not public
curl -s -o /dev/null -w '%{http_code}\n' https://ops.tritusa.com.au/tasks             # 200: SPA fallback
```

In the app:
1. **Administration → Backups → Run backup now.** Both copies show success: `LOCAL` and `S3 <bucket>/backups/`. The
   bucket's **Objects** tab shows `backups/<file>.tobk` and `backups/<file>.tobk.sha256`.
2. **Administration → Audit Logs:** your last `LOGIN` shows your own public IP, not 172.28.x.x.
3. Upload an attachment of a few MB (it works) and one over 10 MB (refused).
4. Open the browser console on a few pages: no Content-Security-Policy errors.

## Update

Each update is a known git commit or tag, built on the instance and tagged with its commit, so the previous images
stay available for a rollback.

1. **Test the release first**, on a development machine: `backend\mvnw.cmd verify -Pit`, and in `frontend/`
   `npm run typecheck`, `npm run lint`, `npm test` and `npm run build`.
2. **Take a safety copy:**
   - In the app: **Administration → Backups → Run backup now**. Wait until both copies succeed.
   - In Lightsail: **Snapshots → Create snapshot** of the instance (name it after the release).
3. **Build the new version** next to the running one:

   ```bash
   cd /opt/team-ops-board
   grep '^APP_VERSION=' .env.prod                 # note the current version: the rollback target
   git fetch --tags && git checkout <new tag or commit>
   sed -i "s/^APP_VERSION=.*/APP_VERSION=$(git rev-parse --short HEAD)/" .env.prod
   dc build                                       # the running containers keep serving meanwhile
   ```

4. **Switch over** (about 30–60 seconds without the app; Flyway runs any new migrations at backend start):

   ```bash
   dc up -d
   dc logs -f backend                             # wait for "Started TeamOpsApplication"
   ```

5. Run [Checks after deploying](#checks-after-deploying).
6. Monthly, or when a release needs it: raise `CLOUDFLARED_VERSION`, then `dc pull cloudflared && dc up -d
   cloudflared`. Run `dc pull mysql` for MySQL 8.4 patch releases; that restarts MySQL, so do it in a quiet hour.
7. Now and then, remove images older than the last two releases: `docker image ls 'team-ops-board-*'`, then
   `docker image rm <old tags>`.

## Rollback

Find the previous `APP_VERSION`, noted in step 3 of the update (or `docker image ls team-ops-board-backend`).

**A release without new migrations** (no new `V*.sql` between the two commits: `git diff --stat <old>..<new> --
backend/src/main/resources/db/migration`). Go back to the old images:

```bash
cd /opt/team-ops-board
git checkout <old tag or commit>
sed -i "s/^APP_VERSION=.*/APP_VERSION=<old version>/" .env.prod
dc up -d                                          # the old images are still there: nothing to build
```

**A release with new migrations.** Flyway only goes forward, and the old code may not match the new schema. Restore
the data as it was before the update, then run the old version:
- **Fastest, and covers everything:** create a new instance from the Lightsail snapshot taken in update step 2. Then
  move the Cloudflare Tunnel to it: on the old instance run `dc stop cloudflared`; the new instance's `cloudflared`
  connects with the same token. If anything was entered after the update, it is lost.
- **Or on the same instance:** restore the backup taken in update step 2
  ([Restore a backup into production](#restore-a-backup-into-production)), then go back to the old images as above.

## Backups and restore

How backups work (encryption, retention 30 daily and 12 monthly, health alert after 26 hours) is in
[backup-and-restore.md](backup-and-restore.md). In this deployment:
- **First copy:** the `backups` volume, at `/var/backups/team-ops` inside the containers.
- **Second copy:** the Lightsail bucket, under `backups/`.

Both run nightly at 02:00 (Asia/Kolkata). The Backups page shows each copy's health.

All restores run in the `tools` container (JDK 21, MySQL 8.4 client and `scripts/`). It reads
`BACKUP_ENCRYPTION_KEY` and the MySQL root password from `.env.prod`. Backups on the volume are listed with:

```bash
dc --profile tools run --rm tools ls -l /var/backups/team-ops
```

### Monthly restore test

As in [backup-and-restore.md](backup-and-restore.md#monthly-restore-test), with these commands:

```bash
dc --profile tools run --rm tools scripts/restore-backup.sh \
    --file /var/backups/team-ops/<team-ops-backup-...>.tobk \
    --database team_ops_board_restore_test --host mysql --user root --replace \
    --uploads-to /var/backups/team-ops/restore-test-uploads
# compare row counts, then clean up:
dc exec mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "DROP DATABASE team_ops_board_restore_test"'
dc --profile tools run --rm tools rm -rf /var/backups/team-ops/restore-test-uploads
```

### Restore a backup into production

Use this after data loss, for a rollback across migrations, or to bring data over from another installation. A
backup made elsewhere needs that installation's `BACKUP_ENCRYPTION_KEY`: pass it with
`-e BACKUP_ENCRYPTION_KEY=...` after `run --rm`.

1. **Get the file onto the `backups` volume.** It is already there if it came from this instance. From the bucket
   (for example on a new instance):

   ```bash
   sudo snap install aws-cli --classic
   AWS_ACCESS_KEY_ID=<bucket key> AWS_SECRET_ACCESS_KEY=<secret> aws s3 cp \
       s3://<bucket>/backups/<file>.tobk . --endpoint-url https://s3.ap-southeast-2.amazonaws.com --region ap-southeast-2
   # same for <file>.tobk.sha256, then:
   dc --profile tools run --rm -v "$PWD":/import:ro tools cp /import/<file>.tobk /import/<file>.tobk.sha256 /var/backups/team-ops/
   ```

2. **Stop the app** (MySQL keeps running): `dc stop backend`.
3. **Empty the uploads volume.** The restore refuses to mix files into it. Only do this once you know the backup is
   good, for example after a restore test of the same file:

   ```bash
   dc --profile tools run --rm tools find /var/lib/team-ops/uploads -mindepth 1 -delete
   ```

4. **Restore.** It asks you to type `team_ops_board` to confirm replacing the live database:

   ```bash
   dc --profile tools run --rm tools scripts/restore-backup.sh \
       --file /var/backups/team-ops/<file>.tobk --database team_ops_board --host mysql --user root --replace \
       --uploads-to /var/lib/team-ops/uploads
   ```

5. **Start the app:** `dc up -d backend`. Flyway sees the migrations from the backup as applied, and runs any newer
   ones from the current code. Then sign in and check recent records and a few attachments. The restore is in the
   audit log as `BACKUP_RESTORED`.

## Break-glass access

When nobody can sign in as a Super Admin. The steps and rules are in
[backup-and-restore.md](backup-and-restore.md#break-glass-access). In this deployment, run the backend jar once in a
one-off container, with its own memory, the same settings and the same database, from an SSH terminal on the
instance:

```bash
dc run --rm --no-deps --entrypoint java backend -Xmx384m -jar /app/app.jar \
    --spring.main.web-application-type=none --app.scheduling.enabled=false --spring.main.banner-mode=off \
    --logging.level.root=WARN \
    --break-glass=reset-password --email=<super admin email>
# or: --break-glass=create-super-admin --employee-id=<ID> --email=<email> --first-name=<name> --last-name=<name>
```

It asks you to type the email again and then the temporary password; add `--generate-password` to have one made
for you. The use is audited as `BREAK_GLASS_USED`. There is no web endpoint for it.

## Day-to-day

| Task | Command |
|---|---|
| Status | `dc ps` |
| Logs (follow) | `dc logs -f --tail 200 backend` (or `nginx`, `mysql`, `cloudflared`) |
| Restart the app | `dc restart backend` |
| Stop / start everything | `dc stop` / `dc up -d` |
| MySQL shell | `dc exec mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" team_ops_board'` |
| Disk use | `docker system df -v` and `df -h /` |
| After an instance reboot | Nothing: Docker starts on boot, and every service has `restart: unless-stopped` |

**Watch:**
- the Backups page (or its red banner);
- the tunnel's status in Zero Trust → Networks → Tunnels;
- Lightsail's metrics for the instance (CPU, and the burst capacity on smaller plans).

Optionally, set a Lightsail alarm on CPU utilisation.
