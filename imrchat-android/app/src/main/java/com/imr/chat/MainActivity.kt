package com.imr.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.imr.chat.ui.chat.ChatScreen
import com.imr.chat.ui.chat.ChatViewModel
import com.imr.chat.ui.settings.SettingsScreen
import com.imr.chat.ui.settings.SettingsViewModel
import com.imr.chat.ui.theme.IMRChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as IMRChatApp

        setContent {
            // Scope ViewModels to Activity so they survive screen navigation.
            // ChatViewModel holds the WebSocket connection — it must not be
            // recreated every time the user switches between screens.
            val activity = LocalContext.current as ComponentActivity

            val settingsViewModel: SettingsViewModel = viewModel(
                viewModelStoreOwner = activity,
                factory = SettingsViewModel.Factory(app.settingsStore, app.database)
            )
            val settings by settingsViewModel.settings.collectAsState()

            val chatViewModel: ChatViewModel = viewModel(
                viewModelStoreOwner = activity,
                factory = ChatViewModel.Factory(
                    context = app.applicationContext,
                    database = app.database,
                    settingsStore = app.settingsStore
                )
            )

            IMRChatTheme(darkMode = settings.darkMode) {
                val navController = rememberNavController()

                // Check if server is configured
                val hasServer = settings.servers.isNotEmpty() &&
                    settings.servers.any { it.host.isNotBlank() }

                val startDestination = if (hasServer) "chat" else "settings"

                NavHost(
                    navController = navController,
                    startDestination = startDestination
                ) {
                    composable("settings") {
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            onNavigateToChat = {
                                navController.navigate("chat") {
                                    popUpTo("settings") { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                    composable("chat") {
                        ChatScreen(
                            viewModel = chatViewModel,
                            onNavigateToSettings = {
                                navController.navigate("settings")
                            }
                        )
                    }
                }
            }
        }
    }
}
