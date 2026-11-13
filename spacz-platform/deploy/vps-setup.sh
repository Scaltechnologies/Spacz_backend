#!/usr/bin/env bash
# One-time preparation of the shared Hostinger VPS (Ubuntu 24.04, Docker + nginx already installed,
# other sites running). It only ADDS things for SPACZ: it does not upgrade packages, touch the
# firewall, nginx or any existing container. Run as root:
#   bash vps-setup.sh "ssh-ed25519 AAAA... github-actions-deploy"
set -euo pipefail

DEPLOY_PUBKEY="${1:?Pass the public key of the GitHub Actions deploy key as the first argument}"
APP_DIR=/opt/spacz

command -v docker >/dev/null || { echo "Docker is not installed." >&2; exit 1; }
docker compose version >/dev/null || { echo "The docker compose plugin is missing." >&2; exit 1; }

echo "==> deploy user (used by GitHub Actions)"
if ! id deploy >/dev/null 2>&1; then
  useradd --create-home --shell /bin/bash deploy
fi
usermod -aG docker deploy
install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
touch /home/deploy/.ssh/authorized_keys
grep -qxF "$DEPLOY_PUBKEY" /home/deploy/.ssh/authorized_keys || echo "$DEPLOY_PUBKEY" >> /home/deploy/.ssh/authorized_keys
chown deploy:deploy /home/deploy/.ssh/authorized_keys
chmod 600 /home/deploy/.ssh/authorized_keys

echo "==> App directory $APP_DIR"
install -d -o deploy -g deploy "$APP_DIR" "$APP_DIR/backups"

echo "==> Swap (protects all apps on the server from the out-of-memory killer)"
if ! swapon --show | grep -q .; then
  fallocate -l 4G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

echo "==> Nightly backup (databases + upload volumes) at 02:30"
cat > /etc/cron.d/spacz-backup <<'EOF'
30 2 * * * deploy /bin/bash /opt/spacz/deploy/backup.sh >> /opt/spacz/backups/backup.log 2>&1
EOF

echo
echo "Done. Next: create $APP_DIR/.env (see deploy/.env.production.example), owned by deploy, mode 600."
