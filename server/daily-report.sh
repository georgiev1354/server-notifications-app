#!/usr/bin/env bash
# daily-report.sh – дневен отчет за състоянието на сървъра.
# Пуска се от push-daily.timer всеки ден в 08:00.

set -uo pipefail

CONF_FILE="${PUSH_NOTIFY_CONF:-/etc/push-notify/push-notify.conf}"
# shellcheck source=/dev/null
source "$CONF_FILE"

uptime_txt=$(awk '{s=int($1); printf "%d дни, %d ч, %d мин", s/86400, s%86400/3600, s%3600/60}' /proc/uptime)
load=$(cut -d' ' -f1-3 /proc/loadavg)
mem=$(free -m | awk '/^Mem:/{printf "%d / %d MB (%d%%)", $3, $2, $3*100/$2}')
disks=$(df -h -x tmpfs -x devtmpfs -x squashfs -x overlay -x efivarfs --output=target,used,size,pcent | tail -n +2 | awk '{printf "  %s: %s / %s (%s)\n", $1, $2, $3, $4}')

# Обновления
updates="неизвестно"
if [[ -x /usr/lib/update-notifier/apt-check ]]; then
    IFS=';' read -r all security < <(/usr/lib/update-notifier/apt-check 2>&1)
    updates="$all (от тях за сигурност: $security)"
else
    updates=$(apt list --upgradable 2>/dev/null | grep -c upgradable)
fi

# Неуспешни SSH опити за последните 24 ч.
failed_ssh=$(journalctl -u ssh -u sshd --since "24 hours ago" --no-pager -o cat 2>/dev/null \
    | grep -cE "Failed password|Invalid user|authentication failure")
ok_ssh=$(journalctl -u ssh -u sshd --since "24 hours ago" --no-pager -o cat 2>/dev/null \
    | grep -cE "Accepted (password|publickey)")

# fail2ban
banned="—"
if command -v fail2ban-client >/dev/null && systemctl is-active --quiet fail2ban; then
    banned=$(fail2ban-client status sshd 2>/dev/null | awk -F: '/Currently banned/{gsub(/ /,"",$2); print $2}')
    total=$(fail2ban-client status sshd 2>/dev/null | awk -F: '/Total banned/{gsub(/ /,"",$2); print $2}')
    banned="${banned:-0} сега, ${total:-0} общо"
fi

reboot="не"
[[ -f /var/run/reboot-required ]] && reboot="ДА"

failed_units=$(systemctl list-units --state=failed --no-legend --plain | awk '{print $1}' | tr '\n' ' ')

report="⏱ Работи от: $uptime_txt
⚙️ Натоварване: $load ($(nproc) ядра)
🧠 Памет: $mem
💾 Дискове:
$disks
📦 Обновления: $updates
🔁 Нужен рестарт: $reboot
🔐 SSH за 24 ч: $ok_ssh успешни, $failed_ssh неуспешни опита
🚫 fail2ban (sshd): $banned
⚠️ Неуспешни услуги: ${failed_units:-няма}"

level=info
[[ "$reboot" == "ДА" || -n "$failed_units" ]] && level=warning

push-notify -l "$level" -c report "📊 Дневен отчет – $SERVER_NAME" "$report"
