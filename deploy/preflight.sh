#!/usr/bin/env bash
# 只读检查，不安装软件、不创建用户、不修改配置。
set -uo pipefail
failed=0
check() {
  if command -v "$1" > /dev/null 2>&1; then printf '[OK] %s\n' "$1";
  else printf '[MISSING] %s\n' "$1"; failed=1; fi
}
if [[ "$(uname -s)" != Linux ]]; then
  echo 'This deployment template requires Linux with systemd.' >&2
  exit 1
fi
if [[ -r /etc/os-release ]]; then grep -E '^(PRETTY_NAME|VERSION_ID)=' /etc/os-release; fi
for command in java mysql mysqldump nginx systemctl openssl tar gzip curl; do check "$command"; done
if command -v java > /dev/null 2>&1; then
  version="$(java -version 2>&1 | head -n 1)"
  printf '%s\n' "$version"
  if [[ ! "$version" =~ \"17\. ]]; then
    echo '[CHECK] The current JVM options are prepared for JDK 17.'
    failed=1
  fi
fi
if [[ ! -d /run/systemd/system ]]; then
  echo '[CHECK] systemd is not running; service templates cannot be started here.'
  failed=1
fi
printf '\nDisk capacity:\n'
df -h /
printf '\nMemory:\n'
free -h
echo 'Also verify DNS/TLS, MySQL connectivity, private TTS readiness and backup storage before release.'
exit "$failed"
