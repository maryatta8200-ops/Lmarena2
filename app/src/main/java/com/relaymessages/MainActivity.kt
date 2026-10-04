package com.relaymessages

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.relaymessages.ui.RelayApp
import com.relaymessages.ui.RelayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launchPhone = intent.getStringExtra(EXTRA_OPEN_PHONE)
            ?: intent.data?.schemeSpecificPart?.substringBefore('?')
        setContent {
            RelayTheme(darkTheme = androidx.compose.foundation.isSystemInDarkTheme()) {
                RelayApp(initialPhone = launchPhone)
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_PHONE = "com.relaymessages.extra.OPEN_PHONE"
    }
}
