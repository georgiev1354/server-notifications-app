#!/usr/bin/env bash
# install.sh – инсталира push известията на Ubuntu сървъра.
#
#   sudo ./install.sh /път/до/service-account.json      # инсталиране / обновяване
#   sudo ./install.sh --uninstall                        # премахване
#
# Скриптът е идемпотентен – може да се пуска многократно (напр. след промяна на файловете).

set -euo pipefail
cd "$(dirname "$0")"

[[ $EUID -eq 0 ]] || { echo "Пуснете с sudo."; exit 1; }

PAM_LINE="session optional pam_exec.so quiet /usr/local/lib/push-notify/pam-login.sh"
UNITS="push-health.timer push-daily.timer push-boot.service"

if [[ "${1:-}" == "--uninstall" ]]; then
    systemctl disable --now $UNITS 2>/dev/null || true
    rm -f /etc/systemd/system/push-{health,daily}.{service,timer} /etc/systemd/system/push-boot.service
    systemctl daemon-reload
    for f in /etc/pam.d/sshd /etc/pam.d/sudo; do
        sed -i "\|pam-login.sh|d" "$f"
    done
    rm -f /etc/fail2ban/action.d/push-notify.conf /etc/fail2ban/jail.d/push-notify.local
    systemctl restart fail2ban 2>/dev/null || true
    rm -rf /usr/local/lib/push-notify /usr/local/bin/push-notify /var/cache/push-notify /var/lib/push-notify /var/spool/push-notify
    echo "Премахнато. Конфигурацията в /etc/push-notify е запазена (изтрийте я ръчно при нужда)."
    exit 0
fi

echo "==> Инсталиране на нужните пакети"
apt-get update -qq
apt-get install -y -qq curl jq openssl fail2ban whois >/dev/null

echo "==> Копиране на скриптовете"
install -m 755 push-notify /usr/local/bin/push-notify
install -d -m 755 /usr/local/lib/push-notify
install -m 755 health-check.sh daily-report.sh pam-login.sh /usr/local/lib/push-notify/
install -d -m 700 /etc/push-notify /var/lib/push-notify /var/cache/push-notify /var/spool/push-notify

if [[ ! -f /etc/push-notify/push-notify.conf ]]; then
    install -m 600 push-notify.conf /etc/push-notify/push-notify.conf
    topic="srv-$(openssl rand -hex 12)"
    sed -i "s/^FCM_TOPIC=.*/FCM_TOPIC=\"$topic\"/" /etc/push-notify/push-notify.conf
    echo "    Създаден е /etc/push-notify/push-notify.conf с нов topic."
else
    echo "    /etc/push-notify/push-notify.conf вече съществува – не е променен."
fi

if [[ -n "${1:-}" ]]; then
    jq -e '.type == "service_account" and .private_key and .client_email' "$1" >/dev/null \
        || { echo "$1 не е валиден service account JSON."; exit 1; }
    install -m 600 "$1" /etc/push-notify/service-account.json
    echo "    Service account ключът е копиран."
fi
[[ -f /etc/push-notify/service-account.json ]] \
    || echo "    ВНИМАНИЕ: липсва /etc/push-notify/service-account.json – известията няма да работят."

echo "==> fail2ban"
install -m 644 fail2ban/action.d/push-notify.conf /etc/fail2ban/action.d/push-notify.conf
install -m 644 fail2ban/jail.d/push-notify.local /etc/fail2ban/jail.d/push-notify.local
systemctl enable fail2ban >/dev/null 2>&1
systemctl restart fail2ban

echo "==> PAM (известие при SSH вход и sudo)"
for f in /etc/pam.d/sshd /etc/pam.d/sudo; do
    grep -qF "pam-login.sh" "$f" || echo "$PAM_LINE" >> "$f"
done

echo "==> systemd таймери"
install -m 644 systemd/*.service systemd/*.timer /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now $UNITS >/dev/null 2>&1

echo
echo "Готово! Topic за приложението:"
echo
echo "    $(. /etc/push-notify/push-notify.conf; echo "$FCM_TOPIC")"
echo
echo "Въведете го в приложението (Настройки → Topic) и пробвайте:  sudo push-notify --test"
