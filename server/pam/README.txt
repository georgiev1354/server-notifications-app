Редове, които install.sh добавя в края на PAM файловете:

/etc/pam.d/sshd:
session optional pam_exec.so quiet /usr/local/lib/push-notify/pam-login.sh

/etc/pam.d/sudo:
session optional pam_exec.so quiet /usr/local/lib/push-notify/pam-login.sh

"optional" гарантира, че входът НИКОГА не се блокира, дори скриптът да даде грешка.
