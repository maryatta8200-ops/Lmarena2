package com.lmarena.agent

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lmarena.agent.ui.ChatScreen
import com.lmarena.agent.ui.ChatViewModel
import com.lmarena.agent.ui.SettingsScreen
import com.lmarena.agent.ui.theme.LmArenaAgentTheme

class MainActivity : ComponentActivity() {

    // The most recent deep-link URI, set from onCreate / onNewIntent and consumed
    // by a LaunchedEffect once the ViewModel is available.
    private val pendingDeepLink = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingDeepLink.value = intent?.data
        setContent {
            LmArenaAgentTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val vm: ChatViewModel = viewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()

                    // Consume the deep link exactly once, after the ViewModel exists.
                    val link = pendingDeepLink.value
                    if (link != null) {
                        androidx.compose.runtime.LaunchedEffect(link) {
                            if (link.scheme == "lmarena-agent" && link.host == "configure") {
                                vm.applyDeepLink(link)
                            }
                            pendingDeepLink.value = null
                        }
                    }

                    if (state.showSettings) {
                        SettingsScreen(vm = vm)
                    } else {
                        ChatScreen(vm = vm)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLink.value = intent?.data
    }
}
