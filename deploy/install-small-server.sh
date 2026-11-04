#!/usr/bin/env bash
# Fresh Ubuntu server installation. Run as root from the unpacked cloud release.
set -euo pipefail
[[ $EUID -eq 0 ]] || { echo 'Run with sudo'; exit 1; }
: "${APP_PUBLIC_IP:?Set APP_PUBLIC_IP to your own server IPv4 address}"
: "${INITIAL_ADMIN_USERNAME:?Set INITIAL_ADMIN_USERNAME explicitly}"
[[ "$APP_PUBLIC_IP" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ && "$APP_PUBLIC_IP" != 203.0.113.10 ]] || { echo 'Invalid public IPv4 address'; exit 1; }
for octet in ${APP_PUBLIC_IP//./ }; do
  (( 10#$octet <= 255 )) || { echo 'Invalid IPv4 octet'; exit 1; }
done
[[ "$INITIAL_ADMIN_USERNAME" =~ ^[A-Za-z0-9_]{3,50}$ ]] || { echo 'Invalid initial administrator username'; exit 1; }
cd -- "$(dirname -- "$0")/.."
[[ -f backend.jar && -f frontend/index.html && -f frontend/downloads/latest.json ]] || exit 1
[[ -f /etc/letsencrypt/live/elderly-study-ip/fullchain.pem ]] || { echo 'HTTPS certificate missing'; exit 1; }
[[ ! -e /etc/elderly-study/backend.env ]] || { echo 'Existing deployment found; stop and use an update procedure'; exit 1; }
if mysql -Nse "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME='elderly_study'" | grep -q elderly_study; then
  echo 'Database already exists; refusing to overwrite'; exit 1
fi
id elderly >/dev/null 2>&1 || useradd --system --user-group --home-dir /opt/elderly-study --shell /usr/sbin/nologin elderly
install -d -m 0755 /opt/elderly-study/deploy /var/www/elderly-study
install -d -m 0700 /etc/elderly-study
install -d -o elderly -g elderly -m 0750 /var/lib/elderly-study/uploads /var/lib/elderly-study/spark-tmp /var/log/elderly-study
if [[ $(swapon --noheadings --show | wc -l) -eq 0 && ! -e /var/lib/elderly-study/swapfile ]]; then
  fallocate -l 2G /var/lib/elderly-study/swapfile
  chmod 0600 /var/lib/elderly-study/swapfile
  mkswap /var/lib/elderly-study/swapfile >/dev/null
  swapon /var/lib/elderly-study/swapfile
  printf '/var/lib/elderly-study/swapfile none swap sw 0 0\n' >> /etc/fstab
fi
install -m 0644 backend.jar /opt/elderly-study/backend.jar
cp -R frontend/. /var/www/elderly-study/
chmod -R a+rX /var/www/elderly-study
install -m 0644 deploy/jvm.options /opt/elderly-study/deploy/jvm.options
sed -i 's/^-Xms512m/-Xms128m/;s/^-Xmx3g/-Xmx640m/' /opt/elderly-study/deploy/jvm.options
printf '\n-XX:MaxDirectMemorySize=128m\n-XX:MaxMetaspaceSize=256m\n' >> /opt/elderly-study/deploy/jvm.options
cat >/etc/mysql/mysql.conf.d/elderly-small.cnf <<'MYSQL'
[mysqld]
bind-address=127.0.0.1
mysqlx-bind-address=127.0.0.1
innodb_buffer_pool_size=128M
max_connections=30
performance_schema=OFF
MYSQL
systemctl restart mysql
umask 077
APP_PASSWORD=$(openssl rand -hex 24)
MIGRATION_PASSWORD=$(openssl rand -hex 24)
BACKUP_PASSWORD=$(openssl rand -hex 24)
JWT_SECRET=$(openssl rand -hex 48)
ADMIN_PASSWORD=$(openssl rand -hex 16)
sed -e "s/__NEW_APP_PASSWORD__/$APP_PASSWORD/" -e "s/__NEW_MIGRATION_PASSWORD__/$MIGRATION_PASSWORD/" -e "s/__NEW_BACKUP_PASSWORD__/$BACKUP_PASSWORD/" deploy/mysql-users.sql.example | mysql
cat >/etc/elderly-study/backend.env <<ENV
DB_URL=jdbc:mysql://127.0.0.1:3306/elderly_study?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
DB_USERNAME=elderly_app
DB_PASSWORD=$APP_PASSWORD
DB_POOL_SIZE=4
DB_MIGRATIONS_ENABLED=true
DB_MIGRATION_USERNAME=elderly_migrator
DB_MIGRATION_PASSWORD=$MIGRATION_PASSWORD
JWT_SECRET=$JWT_SECRET
BOOTSTRAP_ADMIN_ENABLED=true
BOOTSTRAP_ADMIN_USERNAME=${INITIAL_ADMIN_USERNAME}
BOOTSTRAP_ADMIN_PASSWORD=$ADMIN_PASSWORD
DEEPSEEK_ENABLED=false
UPLOAD_DIR=/var/lib/elderly-study/uploads
SPARK_EXPORT_DIR=/var/lib/elderly-study/spark-tmp
LOG_FILE=/var/log/elderly-study/backend.log
SPARK_MODE=local
SPARK_MASTER=local[1]
TTS_MODE=http
TTS_API_URL=http://127.0.0.1:5050/generate
TTS_STARTUP_WARMUP=false
TTS_PREWARM_ON_STARTUP=false
CORS_ALLOWED_ORIGINS=
ENV
printf 'username=%s\npassword=%s\n' "$INITIAL_ADMIN_USERNAME" "$ADMIN_PASSWORD" >/etc/elderly-study/initial-admin.txt
printf '[client]\nhost=127.0.0.1\nuser=elderly_backup\npassword=%s\n' "$BACKUP_PASSWORD" >/etc/elderly-study/backup.cnf
install -m 0644 deploy/elderly-study.service /etc/systemd/system/elderly-study.service
install -m 0644 deploy/nginx-ip.conf.example /etc/nginx/sites-available/elderly-study
sed -i "s/203\.0\.113\.10/$APP_PUBLIC_IP/g" /etc/nginx/sites-available/elderly-study
ln -s /etc/nginx/sites-available/elderly-study /etc/nginx/sites-enabled/elderly-study
# Existing default serves the ACME webroot; our IP-specific server wins for app requests.
nginx -t
systemctl reload nginx
systemctl daemon-reload
install -m 0644 deploy/elderly-cert-renew.service /etc/systemd/system/elderly-cert-renew.service
install -m 0644 deploy/elderly-cert-renew.timer /etc/systemd/system/elderly-cert-renew.timer
systemctl daemon-reload
systemctl enable --now elderly-cert-renew.timer
systemctl enable --now elderly-study
for attempt in $(seq 1 90); do
  if curl -fsS http://127.0.0.1:8081/api/course/list >/dev/null; then
    sed -i 's/^BOOTSTRAP_ADMIN_ENABLED=true/BOOTSTRAP_ADMIN_ENABLED=false/;/^BOOTSTRAP_ADMIN_USERNAME=/d;/^BOOTSTRAP_ADMIN_PASSWORD=/d' /etc/elderly-study/backend.env
    echo 'BACKEND_READY; initial admin credentials stored in /etc/elderly-study/initial-admin.txt (root only)'
    exit 0
  fi
  sleep 2
done
echo 'Backend not ready; inspect journalctl -u elderly-study'
exit 1
