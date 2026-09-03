package com.example

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.ui.chat.ChatScreen
import com.example.ui.chat.ChatViewModel
import com.example.ui.diagnostics.DiagnosticsBottomSheet
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.HermesChatTheme
import kotlinx.coroutines.launch

enum class AppDestination {
    CHAT,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private val chatViewModel: ChatViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by chatViewModel.settings.collectAsState()
            HermesChatTheme(uiDensityScale = settings.uiDensityScale) {
                HermesChatApp(
                    chatViewModel = chatViewModel,
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HermesChatApp(
    chatViewModel: ChatViewModel,
    settingsViewModel: SettingsViewModel
) {
    var currentDestination by remember { mutableStateOf(AppDestination.CHAT) }
    var showDiagnostics by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    val serverHealth by chatViewModel.serverHealth.collectAsState()
    val settings by chatViewModel.settings.collectAsState()

    val context = LocalContext.current
    var lastBackPressTime by remember { mutableStateOf(0L) }

    // When on SettingsScreen: pressing back returns to ChatScreen
    BackHandler(enabled = currentDestination == AppDestination.SETTINGS) {
        currentDestination = AppDestination.CHAT
        chatViewModel.checkServerHealth()
    }

    // When on ChatScreen: pressing back prompts user to press again to exit
    BackHandler(enabled = currentDestination == AppDestination.CHAT) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBackPressTime < 2000L) {
            (context as? Activity)?.finish()
        } else {
            lastBackPressTime = currentTime
            Toast.makeText(
                context,
                "Se quiser sair carregue outra vez na tecla retrocesso",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    AnimatedContent(
        targetState = currentDestination,
        transitionSpec = {
            if (targetState == AppDestination.SETTINGS) {
                slideInHorizontally { width -> width } togetherWith slideOutHorizontally { width -> -width }
            } else {
                slideInHorizontally { width -> -width } togetherWith slideOutHorizontally { width -> width }
            }
        },
        label = "ScreenTransition",
        modifier = Modifier.fillMaxSize()
    ) { destination ->
        when (destination) {
            AppDestination.CHAT -> {
                ChatScreen(
                    viewModel = chatViewModel,
                    onNavigateToSettings = {
                        currentDestination = AppDestination.SETTINGS
                    },
                    onOpenDiagnostics = {
                        showDiagnostics = true
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            AppDestination.SETTINGS -> {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateBack = {
                        currentDestination = AppDestination.CHAT
                        // Sync health check when returning from settings
                        chatViewModel.checkServerHealth()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // Diagnostics Bottom Sheet
    if (showDiagnostics) {
        DiagnosticsBottomSheet(
            serverHealth = serverHealth,
            serverUrl = settings.serverUrl,
            sheetState = sheetState,
            onDismiss = {
                coroutineScope.launch { sheetState.hide() }
                showDiagnostics = false
            },
            onRecheck = {
                chatViewModel.checkServerHealth()
            }
        )
    }
}
