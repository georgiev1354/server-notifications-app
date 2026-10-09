package com.ggwebapp.servernotifications

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ggwebapp.servernotifications.data.NotificationRepository
import com.ggwebapp.servernotifications.data.Settings
import com.ggwebapp.servernotifications.push.Notifier
import com.ggwebapp.servernotifications.ui.DetailScreen
import com.ggwebapp.servernotifications.ui.ListScreen
import com.ggwebapp.servernotifications.ui.SettingsScreen
import com.ggwebapp.servernotifications.ui.theme.ServerNotificationsTheme

private sealed interface Screen {
    data object List : Screen
    data class Detail(val id: Long) : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {

    private var screen by mutableStateOf<Screen>(Screen.List)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = NotificationRepository.get(this)
        val settings = Settings(this)

        if (settings.topic == null) screen = Screen.Settings
        handleIntent(intent)
        askNotificationPermission()

        setContent {
            ServerNotificationsTheme {
                when (val s = screen) {
                    Screen.List -> ListScreen(
                        repository = repository,
                        onOpen = { screen = Screen.Detail(it) },
                        onSettings = { screen = Screen.Settings },
                    )

                    is Screen.Detail -> {
                        BackHandler { screen = Screen.List }
                        DetailScreen(
                            repository = repository,
                            id = s.id,
                            onBack = { screen = Screen.List },
                        )
                    }

                    Screen.Settings -> {
                        BackHandler { screen = Screen.List }
                        SettingsScreen(
                            settings = settings,
                            onBack = { screen = Screen.List },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Докосване на системно известие отваря директно детайлите му. */
    private fun handleIntent(intent: Intent?) {
        val id = intent?.getLongExtra(Notifier.EXTRA_NOTIFICATION_ID, -1L) ?: -1L
        if (id > 0) {
            screen = Screen.Detail(id)
            intent?.removeExtra(Notifier.EXTRA_NOTIFICATION_ID)
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }
}
