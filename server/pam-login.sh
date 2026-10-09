#!/usr/bin/env bash
# pam-login.sh – известие при успешен вход (SSH) или използване на sudo.
# Извиква се от PAM чрез pam_exec.so (вижте /etc/pam.d/sshd и /etc/pam.d/sudo).
# PAM подава: PAM_TYPE, PAM_USER, PAM_RHOST, PAM_SERVICE, PAM_RUSER, PAM_TTY.

PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
CONF_FILE="${PUSH_NOTIFY_CONF:-/etc/push-notify/push-notify.conf}"
# shellcheck source=/dev/null
source "$CONF_FILE" 2>/dev/null

# Само при отваряне на сесия – не и при затваряне.
[[ "${PAM_TYPE:-}" == "open_session" ]] || exit 0

for ip in ${LOGIN_IGNORE_IPS:-}; do
    [[ "${PAM_RHOST:-}" == "$ip"* ]] && exit 0
done

when=$(date '+%d.%m.%Y %H:%M:%S')

case "${PAM_SERVICE:-}" in
    sshd)
        push-notify -q -l info -c security -k "login-$PAM_USER-$PAM_RHOST" \
            "🔑 SSH вход: $PAM_USER" \
            "Потребител: $PAM_USER
От адрес: ${PAM_RHOST:-неизвестен}
Време: $when" </dev/null >/dev/null 2>&1 &
        ;;
    sudo)
        [[ "${NOTIFY_SUDO:-yes}" == "yes" ]] || exit 0
        [[ "${PAM_RUSER:-root}" == "root" ]] && exit 0   # скриптове, пуснати от root
        push-notify -q -l info -c security -k "sudo-$PAM_RUSER-$PAM_USER" \
            "🛡️ sudo: ${PAM_RUSER:-?} → $PAM_USER" \
            "Потребителят ${PAM_RUSER:-?} изпълни команда като $PAM_USER.
Терминал: ${PAM_TTY:-—}
Време: $when" </dev/null >/dev/null 2>&1 &
        ;;
    *)
        push-notify -q -l info -c security "🔑 Вход ($PAM_SERVICE): $PAM_USER" \
            "Потребител: $PAM_USER, адрес: ${PAM_RHOST:-локално}, време: $when" </dev/null >/dev/null 2>&1 &
        ;;
esac

# Не задържаме входа – известието се праща във фонов режим.
exit 0
