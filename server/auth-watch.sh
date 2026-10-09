#!/usr/bin/env bash
# auth-watch.sh – следи журнала в реално време и праща известие при грешна парола
# за sudo или su. Работи и с класическото sudo, и със sudo-rs (Ubuntu 25.10+),
# защото и двете пишат един и същ ред от pam_unix:
#   pam_unix(sudo:auth): authentication failure; logname=ivan uid=1000 euid=0 tty=/dev/pts/0 ruser=ivan rhost=  user=ivan
#
# Пуска се като услуга: push-auth-watch.service (Restart=always).
#
# Първият грешен опит праща известие веднага. Следващите опити от същия потребител
# в рамките на AUTH_WATCH_WINDOW секунди се събират и идват като едно обобщение
# („5 грешни опита за 2 мин“) – така атака с много опити не ви засипва.

set -uo pipefail
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

CONF_FILE="${PUSH_NOTIFY_CONF:-/etc/push-notify/push-notify.conf}"
# shellcheck source=/dev/null
source "$CONF_FILE"
: "${AUTH_WATCH_WINDOW:=120}"
# PAM услуги, които се следят (имената в скобите на pam_unix).
: "${AUTH_WATCH_SERVICES:=sudo sudo-i su su-l}"

declare -A first_at=() count=() tty_of=()

send() { push-notify -q -c security "$@" </dev/null >/dev/null 2>&1 & }

describe() {   # $1 PAM услуга, $2 ruser, $3 user -> заглавие
    case "$1" in
        sudo|sudo-i) echo "🚨 Грешна sudo парола: ${2:-$3}" ;;
        *)           echo "🚨 Грешна парола за su → $3${2:+ (от $2)}" ;;
    esac
}

# Праща обобщение за ключове, чийто прозорец е изтекъл.
flush() {
    local now key svc ruser user n period
    now=$(date +%s)
    if (( AUTH_WATCH_WINDOW >= 60 )); then period="$((AUTH_WATCH_WINDOW / 60)) мин"; else period="$AUTH_WATCH_WINDOW сек"; fi
    for key in "${!first_at[@]}"; do
        (( now - first_at[$key] >= AUTH_WATCH_WINDOW )) || continue
        n=${count[$key]}
        if (( n > 1 )); then
            IFS='|' read -r svc ruser user <<<"$key"
            send -l critical "$(describe "$svc" "$ruser" "$user") ×$n" \
"$n грешни опита за $period.
Потребител: ${ruser:-—}
Целеви акаунт: $user
Терминал: ${tty_of[$key]:-—}

Ако не сте били вие – някой има достъп до този акаунт!"
        fi
        unset "first_at[$key]" "count[$key]" "tty_of[$key]"
    done
}

on_line() {
    local line="$1" svc ruser user tty key when
    case "$line" in
        *"NOT in sudoers"*|*"not in the sudoers"*)
            # Потребител без sudo права се опитва да ползва sudo.
            send -l critical -k "nosudoers-$line" "⛔ Опит за sudo без права" "$line"
            return ;;
        *"pam_unix("*":auth): authentication failure"*) ;;
        *) return ;;
    esac

    svc=${line#*pam_unix(}; svc=${svc%%:auth*}
    [[ " $AUTH_WATCH_SERVICES " == *" $svc "* ]] || return

    ruser=$(sed -n 's/.* ruser=\([^ ]*\).*/\1/p' <<<"$line")
    user=$(sed -n 's/.* user=\([^ ]*\).*/\1/p' <<<"$line")
    tty=$(sed -n 's/.* tty=\([^ ]*\).*/\1/p' <<<"$line")
    key="$svc|$ruser|$user"

    if [[ -z "${first_at[$key]:-}" ]]; then
        first_at[$key]=$(date +%s)
        count[$key]=1
        tty_of[$key]=$tty
        when=$(date '+%d.%m.%Y %H:%M:%S')
        send -l warning "$(describe "$svc" "$ruser" "$user")" \
"Потребител: ${ruser:-—}
Целеви акаунт: $user
Терминал: ${tty:-—}
Време: $when"
    else
        count[$key]=$(( count[$key] + 1 ))
    fi
}

# -n0: само нови редове; SYSLOG_FACILITY=10 = authpriv (там пишат sudo, su, PAM).
while true; do
    if IFS= read -r -t 15 line; then
        on_line "$line"
    elif (( $? <= 128 )); then
        break   # journalctl спря – systemd ще рестартира услугата
    fi
    flush
done < <(journalctl -f -n0 -q -o cat SYSLOG_FACILITY=10)

exit 1
