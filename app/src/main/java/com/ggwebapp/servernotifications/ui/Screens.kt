package com.ggwebapp.servernotifications.ui

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ggwebapp.servernotifications.data.Category
import com.ggwebapp.servernotifications.data.Level
import com.ggwebapp.servernotifications.data.NotificationRepository
import com.ggwebapp.servernotifications.data.ServerNotification
import com.ggwebapp.servernotifications.data.Settings
import com.ggwebapp.servernotifications.push.Notifier
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val bgLocale = Locale.forLanguageTag("bg-BG")

private fun formatTime(millis: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", bgLocale).format(Date(millis))

private fun Level.color(): Color = when (this) {
    Level.CRITICAL -> Color(0xFFD32F2F)
    Level.WARNING -> Color(0xFFF57C00)
    Level.INFO -> Color(0xFF1976D2)
}

private fun Level.label(): String = when (this) {
    Level.CRITICAL -> "Критично"
    Level.WARNING -> "Предупреждение"
    Level.INFO -> "Информация"
}

// ---------------------------------------------------------------- Списък

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    repository: NotificationRepository,
    onOpen: (Long) -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val all by repository.notifications.collectAsState()
    var category by rememberSaveable { mutableStateOf<Category?>(null) }
    var unreadOnly by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val unreadCount = all.count { !it.read }
    val shown = all.filter { (category == null || it.category == category) && (!unreadOnly || !it.read) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Сървърни известия")
                        if (unreadCount > 0) {
                            Text("$unreadCount непрочетени", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { repository.markAllRead(); Notifier.cancelAll(context) }) { Text("✓ Всички") }
                    TextButton(onClick = { confirmDelete = true }) { Text("🗑") }
                    TextButton(onClick = onSettings) { Text("⚙") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = unreadOnly, onClick = { unreadOnly = !unreadOnly }, label = { Text("Непрочетени") })
                FilterChip(selected = category == null, onClick = { category = null }, label = { Text("Всички") })
                Category.entries.forEach { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { category = if (category == c) null else c },
                        label = { Text("${c.emoji} ${c.label}") },
                    )
                }
            }

            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (all.isEmpty()) "Още няма известия.\nПробвайте на сървъра:\nsudo push-notify --test"
                        else "Няма известия за този филтър.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown, key = { it.id }) { n -> NotificationRow(n) { onOpen(n.id) } }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Изтриване") },
            text = { Text("Да се изтрият ли всички ${all.size} известия?") },
            confirmButton = {
                TextButton(onClick = {
                    repository.deleteAll()
                    Notifier.cancelAll(context)
                    confirmDelete = false
                }) { Text("Изтрий") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отказ") } },
        )
    }
}

@Composable
private fun NotificationRow(n: ServerNotification, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (n.read) MaterialTheme.colorScheme.surfaceContainerLow
            else MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Row(Modifier.padding(12.dp)) {
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .size(10.dp)
                    .background(n.level.color(), CircleShape),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        n.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (n.read) FontWeight.Normal else FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        formatTime(n.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    n.body,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${n.category.emoji} ${n.category.label} · ${n.server}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Детайли

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(repository: NotificationRepository, id: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val all by repository.notifications.collectAsState()
    val n = all.firstOrNull { it.id == id }

    LaunchedEffect(id) {
        repository.markRead(id)
        Notifier.cancel(context, id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Известие") },
                navigationIcon = { TextButton(onClick = onBack) { Text("← Назад") } },
                actions = {
                    if (n != null) {
                        TextButton(onClick = {
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "${n.title}\n${n.server} · ${formatTime(n.timestamp)}\n\n${n.body}")
                                    },
                                    "Сподели",
                                ),
                            )
                        }) { Text("Сподели") }
                        TextButton(onClick = { repository.delete(id); onBack() }) { Text("Изтрий") }
                    }
                },
            )
        },
    ) { padding ->
        if (n == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Известието не е намерено.")
            }
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                n.level.label(),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .background(n.level.color(), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
            Spacer(Modifier.height(12.dp))
            SelectionContainer {
                Text(n.title, style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${n.category.emoji} ${n.category.label} · ${n.server} · ${formatTime(n.timestamp)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            SelectionContainer {
                Text(
                    n.body,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Настройки

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: Settings, onBack: () -> Unit) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(settings.topic) }
    var input by rememberSaveable { mutableStateOf(settings.topic.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = { TextButton(onClick = onBack) { Text("← Назад") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Връзка със сървъра", style = MaterialTheme.typography.titleMedium)
            Text(
                "Въведете topic-а, който install.sh отпечата на сървъра " +
                    "(или вижте FCM_TOPIC в /etc/push-notify/push-notify.conf).",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Topic") },
                placeholder = { Text("srv-…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !busy && input.isNotBlank() && input.trim() != current,
                    onClick = {
                        busy = true
                        message = null
                        settings.subscribe(input) { error ->
                            busy = false
                            current = settings.topic
                            message = error ?: "✅ Абонирани сте. Пуснете на сървъра: sudo push-notify --test"
                        }
                    },
                ) { Text(if (busy) "Изчакайте…" else "Абонирай се") }
                if (current != null) {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            settings.unsubscribe { error ->
                                busy = false
                                current = settings.topic
                                message = error ?: "Отписахте се – няма да получавате известия."
                            }
                        },
                    ) { Text("Отпиши се") }
                }
            }
            Text(
                if (current != null) "Текущ topic: $current" else "Не сте абонирани за известия.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

            HorizontalDivider()

            Text("Звук и вибрация", style = MaterialTheme.typography.titleMedium)
            Text(
                "Известията са в три канала – Критични, Предупреждения и Информация. " +
                    "Можете да настроите звука на всеки поотделно (напр. Информация без звук).",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = {
                context.startActivity(
                    Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }) { Text("Системни настройки за известия") }
        }
    }
}
