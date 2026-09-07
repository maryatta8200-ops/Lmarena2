package com.lmarena.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lmarena.agent.ui.ChatScreen
import com.lmarena.agent.ui.ChatViewModel
import com.lmarena.agent.ui.SettingsScreen
import com.lmarena.agent.ui.theme.LmArenaAgentTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LmArenaAgentTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val vm: ChatViewModel = viewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    if (state.showSettings) {
                        SettingsScreen(vm = vm)
                    } else {
                        ChatScreen(vm = vm)
                    }
                }
            }
        }
    }
}
