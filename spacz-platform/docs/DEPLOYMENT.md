# Deploying SPACZ to the Hostinger VPS (with CI/CD)

The VPS (KVM 4, Ubuntu 24.04) is **shared**: it already runs other websites through nginx on
ports 80/443. SPACZ is added next to them without touching them:

* its own Docker containers (compose project `spacz`), its own Postgres, its own `/opt/spacz` folder;
* nothing published to the internet: the gateway listens on `127.0.0.1:18080` only;
* the existing nginx gets one extra site file that proxies the API domain to that port;
* no firewall changes, no package upgrades.

## How it works

```
 git push main ──► GitHub Actions
                   1. test      ./mvnw verify (all 5 services)
                   2. images    build 5 Docker images ──► ghcr.io/scaltechnologies/spacz-*:<commit-sha>
                   3. deploy    SSH into the VPS as "deploy" ──► deploy.sh: docker compose pull + up --wait
                                   └─ a container not healthy? ──► previous build restarted automatically
                   4. smoke     calls https://api.spacz.in (health, programs, halls, login, auth check)
                                   └─ a check fails? ──► previous build restarted automatically

 Internet ──443──► nginx (existing, HTTPS via certbot) ──► 127.0.0.1:18080 gateway ──► auth/user/studyhall/admin
                                                                                        └─► postgres (private)
```

* Pull requests only run the tests (`.github/workflows/ci.yml`).
* Pushes to `main` that touch `spacz-platform/**` test, build, deploy and smoke-test
  (`.github/workflows/deploy.yml`). A red run means the live API is still on the last good build.
* A deploy can also be started by hand: **Actions → Deploy → Run workflow**.
  * `image_tag` empty: rebuild and deploy the current `main`.
  * `image_tag` = `rollback`: go back to the build before the current one (no rebuild).
  * `image_tag` = a commit SHA: deploy that exact earlier build (no rebuild).
* The smoke test uses `https://api.spacz.in`. To change it, set the repository variable
  `PUBLIC_API_URL` (**Settings → Secrets and variables → Actions → Variables**).
* Secrets (DB passwords, JWT key …) live **only on the server** in `/opt/spacz/.env`.

Files in `deploy/`:

| File | Purpose |
|---|---|
| `docker-compose.prod.yml` | production stack (pulls images, no build, memory limits, log rotation) |
| `nginx/spacz-api.conf` | site file for the existing nginx |
| `deploy.sh` | pull + restart + wait for healthy; also used for rollback |
| `vps-setup.sh` | one-time: `deploy` user, `/opt/spacz`, swap, nightly backup |
| `backup.sh` | nightly `pg_dumpall`, keeps 14 days |
| `.env.production.example` | template for `/opt/spacz/.env` |

---

## One-time setup

Server commands below are run as **root** in hPanel → VPS → Manage → **Browser terminal**
(or `ssh root@200.234.36.196`).

### 1. Choose the API address

Add a DNS **A record** `api.yourdomain.com → 200.234.36.196` at your domain provider (any subdomain works).
Use it everywhere this guide says `api.yourdomain.com`.

### 2. Create the deploy SSH key (on your laptop, PowerShell)

```powershell
ssh-keygen -t ed25519 -C "github-actions-deploy" -f spacz_deploy -N '""'
Get-Content spacz_deploy.pub
```

This creates `spacz_deploy` (private, goes to GitHub) and `spacz_deploy.pub` (public, goes to the server).

### 3. Prepare the server

Copy `deploy/vps-setup.sh` to the server (or paste its content into `nano /root/vps-setup.sh`), then:

```bash
bash /root/vps-setup.sh "<the line printed by Get-Content spacz_deploy.pub>"
```

### 4. Create the production `.env`

```bash
nano /opt/spacz/.env          # paste deploy/.env.production.example and fill in every value
chown deploy:deploy /opt/spacz/.env && chmod 600 /opt/spacz/.env
```

Generate each secret with `openssl rand -base64 48 | tr -d '/+=' | cut -c1-48`.
Set `CORS_ALLOWED_ORIGINS` to the real frontend URLs. Check that port 18080 is free first
(`ss -tlnp | grep 18080` prints nothing); otherwise choose another `GATEWAY_HOST_PORT`.

> The DB passwords are applied only on the **first** Postgres start. Changing them later in `.env`
> will not change them in the database.

### 5. Add the GitHub secrets

GitHub repo → **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value |
|---|---|
| `VPS_HOST` | `200.234.36.196` |
| `VPS_USER` | `deploy` |
| `VPS_SSH_KEY` | full content of the **private** file `spacz_deploy` (including the BEGIN/END lines) |
| `VPS_PORT` | optional, only if SSH is not on 22 |

Also create **Settings → Environments → New environment → `production`**. Optionally add a
*required reviewer* there to approve each production deploy.

### 6. First deploy

Push to `main` (or **Actions → Deploy → Run workflow**) and watch the **Actions** tab. The first start
takes a few minutes. Then on the server:

```bash
curl http://127.0.0.1:18080/actuator/health        # {"status":"UP"...}
```

### 7. Connect nginx (only once)

```bash
cp /opt/spacz/deploy/nginx/spacz-api.conf /etc/nginx/sites-available/spacz-api
nano /etc/nginx/sites-available/spacz-api          # set server_name api.yourdomain.com;
ln -s /etc/nginx/sites-available/spacz-api /etc/nginx/sites-enabled/spacz-api
nginx -t && systemctl reload nginx                 # nginx -t must say "ok" before reloading
certbot --nginx -d api.yourdomain.com              # HTTPS (apt install certbot python3-certbot-nginx if missing)
```

`nginx -t` checks the config first, so a typo cannot take the other websites down. Only reload when it passes.

Check: `curl https://api.yourdomain.com/actuator/health`

---

## Day-to-day

Run these on the server as `deploy` or root.

| Task | Command |
|---|---|
| Status | `docker ps --filter name=spacz` |
| Logs of one service | `docker logs -f --tail 200 spacz-auth-service-1` |
| Restart SPACZ | `bash /opt/spacz/deploy/deploy.sh` |
| Roll back | Easiest: **Actions → Deploy → Run workflow** with `image_tag` = `rollback`. On the server: `bash /opt/spacz/deploy/deploy.sh --rollback` or `… deploy.sh <older commit sha>` (GHCR login needed, see below) |
| Change a setting | edit `/opt/spacz/.env`, then `bash /opt/spacz/deploy/deploy.sh` |
| Manual backup | `bash /opt/spacz/deploy/backup.sh` |
| Open psql | `docker exec -it spacz-postgres-1 psql -U postgres` |

Phone OTP codes go to the logs while `OTP_SMS_PROVIDER=log`:
`docker logs spacz-auth-service-1 | grep -i otp`.

**Manual pulls (rollback from the server):** the images are private, so the server needs a login first.
Create a GitHub *classic* personal access token with only `read:packages` and run once as `deploy`:
`echo <token> | docker login ghcr.io -u <github-username> --password-stdin`.

**Backups** are written to `/opt/spacz/backups`. Copy them off the server regularly.

## Troubleshooting

| Symptom | Fix |
|---|---|
| Deploy step: `Permission denied (publickey)` | `VPS_SSH_KEY` is wrong, or the `.pub` key is not in `/home/deploy/.ssh/authorized_keys` |
| `denied` / `unauthorized` when pulling | check `IMAGE_REGISTRY` in `.env` is lower case and the workflow has `packages: read` |
| `port is already allocated` | another app uses 18080: change `GATEWAY_HOST_PORT` in `.env` and in the nginx file |
| Deploy red, "did not become healthy … Rolling back" | the new build was rejected and the old one is running again; the run log shows the failing container's logs. Otherwise: `docker logs spacz-<name>-1`: usually a wrong DB password or a missing `.env` value |
| Container restarts with exit code 137 | out of memory: lower `*_MEM_LIMIT` in `.env` |
| 502 Bad Gateway from nginx | SPACZ is not running or the port in the nginx file differs from `GATEWAY_HOST_PORT` |
| CORS errors in the frontend | add the exact frontend origin (`https://…`, no trailing slash) to `CORS_ALLOWED_ORIGINS` and redeploy |

**Rollbacks and the database.** A rollback changes only the code, never the data. Flyway
migrations only move forward. Before merging a migration that removes or renames a column, make
sure the previous build still works with the new schema (add first, remove in a later release).
