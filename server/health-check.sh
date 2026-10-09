#!/usr/bin/env bash
# health-check.sh – проверява състоянието на машината и праща известие
# САМО при промяна (проблем → появява се, проблем → изчезва).
# Пуска се от push-health.timer на всеки 5 минути.

set -uo pipefail

CONF_FILE="${PUSH_NOTIFY_CONF:-/etc/push-notify/push-notify.conf}"
STATE_DIR="/var/lib/push-notify/state"
# shellcheck source=/dev/null
source "$CONF_FILE"
mkdir -p "$STATE_DIR"

: "${DISK_WARN:=85}" "${DISK_CRIT:=95}" "${MEM_WARN:=90}" "${LOAD_WARN:=1.5}" "${TEMP_WARN:=80}"
: "${SERVICES:=}" "${AUTO_RESTART:=no}" "${CHECK_FAILED_UNITS:=yes}" "${CHECK_REBOOT_REQUIRED:=yes}" "${HTTP_CHECKS:=}"

# Първо – изпрати отложени известия (ако преди е нямало мрежа).
push-notify -q --flush

# check KEY STATE TITLE BODY CATEGORY
#   STATE: ok | warning | critical
# Праща известие, ако състоянието е различно от предишното.
check() {
    local key="$1" state="$2" title="$3" body="$4" category="$5"
    local file="$STATE_DIR/$(printf '%s' "$key" | tr -c 'a-zA-Z0-9_.-' '_')"
    local old="ok"
    [[ -r "$file" ]] && old=$(<"$file")
    [[ "$state" == "$old" ]] && return 0

    if [[ "$state" == "ok" ]]; then
        push-notify -q -l info -c "$category" "✅ Възстановено: $title" "$body"
    else
        push-notify -q -l "$state" -c "$category" "$title" "$body"
    fi
    echo "$state" > "$file"
}

# --- Дискове ---
while read -r mount pcent; do
    pct=${pcent%\%}
    state=ok
    (( pct >= DISK_WARN )) && state=warning
    (( pct >= DISK_CRIT )) && state=critical
    avail=$(df -h --output=avail "$mount" | tail -1 | tr -d ' ')
    check "disk$mount" "$state" "💾 Диск $mount: $pct%" "Заето: $pct%, свободно: $avail." system
done < <(df -x tmpfs -x devtmpfs -x squashfs -x overlay -x efivarfs --output=target,pcent | tail -n +2)

# --- Памет ---
read -r mem_total mem_avail < <(awk '/MemTotal/{t=$2} /MemAvailable/{a=$2} END{print t, a}' /proc/meminfo)
mem_pct=$(( (mem_total - mem_avail) * 100 / mem_total ))
state=ok; (( mem_pct >= MEM_WARN )) && state=warning
check memory "$state" "🧠 Памет: $mem_pct% заета" \
    "Свободни: $((mem_avail / 1024)) MB от $((mem_total / 1024)) MB.
Най-много памет:
$(ps -eo comm,%mem --sort=-%mem | sed -n '2,4p')" system

# --- Натоварване на процесора ---
cores=$(nproc)
load5=$(awk '{print $2}' /proc/loadavg)
state=ok
awk -v l="$load5" -v c="$cores" -v w="$LOAD_WARN" 'BEGIN{exit !(l/c >= w)}' && state=warning
check load "$state" "🔥 Високо натоварване: $load5" \
    "Load average (5 мин): $load5 при $cores ядра.
Най-натоварени процеси:
$(ps -eo comm,%cpu --sort=-%cpu | sed -n '2,4p')" system

# --- Температура ---
temp=""
for zone in /sys/class/thermal/thermal_zone*/temp; do
    [[ -r "$zone" ]] || continue
    t=$(( $(<"$zone") / 1000 ))
    [[ -z "$temp" || $t -gt $temp ]] && temp=$t
done
if [[ -n "$temp" ]]; then
    state=ok; (( temp >= TEMP_WARN )) && state=warning
    check temperature "$state" "🌡️ Температура: $temp °C" "Най-високата измерена температура е $temp °C (праг $TEMP_WARN °C)." system
fi

# --- Услуги ---
for svc in $SERVICES; do
    systemctl list-unit-files "$svc.service" >/dev/null 2>&1 || continue
    if systemctl is-active --quiet "$svc"; then
        check "svc-$svc" ok "Услуга $svc" "Услугата $svc работи отново." service
    else
        body="Услугата $svc не работи ($(systemctl is-active "$svc")).
$(journalctl -u "$svc" -n 5 --no-pager -o cat 2>/dev/null)"
        if [[ "$AUTO_RESTART" == "yes" ]]; then
            if systemctl restart "$svc" && sleep 3 && systemctl is-active --quiet "$svc"; then
                push-notify -q -l warning -c service "🔄 $svc беше рестартирана" "$body

Автоматичният рестарт успя."
                continue
            fi
            body="$body

Автоматичният рестарт НЕ успя."
        fi
        check "svc-$svc" critical "❌ Спряла услуга: $svc" "$body" service
    fi
done

# --- Неуспешни systemd единици ---
if [[ "$CHECK_FAILED_UNITS" == "yes" ]]; then
    failed=$(systemctl list-units --state=failed --no-legend --plain | awk '{print $1}' | sort | tr '\n' ' ')
    state=ok; [[ -n "$failed" ]] && state=warning
    # ключът включва списъка, за да дойде ново известие при нова грешна единица
    prev_failed_file="$STATE_DIR/failed_units_list"
    if [[ "$failed" != "$(cat "$prev_failed_file" 2>/dev/null)" && -n "$failed" ]]; then
        rm -f "$STATE_DIR/failed-units"
    fi
    echo "$failed" > "$prev_failed_file"
    check failed-units "$state" "⚠️ Неуспешни systemd услуги" "${failed:-Няма неуспешни услуги.}" service
fi

# --- Нужен рестарт ---
if [[ "$CHECK_REBOOT_REQUIRED" == "yes" ]]; then
    state=ok; [[ -f /var/run/reboot-required ]] && state=warning
    pkgs=$(cat /var/run/reboot-required.pkgs 2>/dev/null | sort -u | head -10 | tr '\n' ' ')
    check reboot-required "$state" "🔁 Нужен е рестарт" "След обновления е нужен рестарт. Пакети: ${pkgs:-—}" system
fi

# --- HTTP проверки ---
for url in $HTTP_CHECKS; do
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 "$url")
    state=ok; [[ "$code" =~ ^[23] ]] || state=critical
    check "http-$url" "$state" "🌐 Сайтът не отговаря: $url" "HTTP код: $code" service
done

exit 0
