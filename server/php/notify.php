<?php
/**
 * notify.php – ОПЦИОНАЛНО. HTTP endpoint, чрез който ваши уеб приложения
 * (или друга машина) могат да пратят push известие през push-notify.
 *
 * Изисква www-data да е в SUDO_USERS в /etc/push-notify/push-notify.conf
 * (SUDO_USERS="www-data") и повторно пускане на install.sh – той създава
 * /etc/sudoers.d/push-notify. Извикванията от PHP не предизвикват известие „sudo“.
 *
 * Заявка:
 *   curl -X POST https://сървър/notify.php \
 *        -H "Authorization: Bearer ТАЙНИЯТ_КЛЮЧ" \
 *        -d title="Нова поръчка" -d body="Поръчка №1234" -d level=info -d category=custom
 */

// Сменете с дълъг случаен низ:  openssl rand -hex 32
const API_KEY = 'СМЕНЕТЕ-МЕ';

header('Content-Type: application/json; charset=utf-8');

function fail(int $code, string $msg): never {
    http_response_code($code);
    echo json_encode(['ok' => false, 'error' => $msg], JSON_UNESCAPED_UNICODE);
    exit;
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    fail(405, 'Само POST');
}

$auth = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
if (API_KEY === 'СМЕНЕТЕ-МЕ' || !hash_equals('Bearer ' . API_KEY, $auth)) {
    fail(401, 'Невалиден ключ');
}

$title    = trim($_POST['title'] ?? '');
$body     = trim($_POST['body'] ?? '');
$level    = $_POST['level'] ?? 'info';
$category = $_POST['category'] ?? 'custom';

if ($title === '' || $body === '') {
    fail(400, 'Липсва title или body');
}
if (!in_array($level, ['info', 'warning', 'critical'], true)) {
    fail(400, 'Невалидно ниво');
}
if (!preg_match('/^[a-z0-9_-]{1,32}$/', $category)) {
    fail(400, 'Невалидна категория');
}

$cmd = sprintf(
    'sudo -n /usr/local/bin/push-notify -q -l %s -c %s %s %s 2>&1',
    escapeshellarg($level),
    escapeshellarg($category),
    escapeshellarg(mb_substr($title, 0, 200)),
    escapeshellarg(mb_substr($body, 0, 3000))
);
exec($cmd, $out, $rc);

if ($rc !== 0) {
    fail(502, 'Неуспешно изпращане (известието е отложено)');
}
echo json_encode(['ok' => true], JSON_UNESCAPED_UNICODE);
