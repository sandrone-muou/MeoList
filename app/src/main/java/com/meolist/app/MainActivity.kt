package com.meolist.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.meolist.app.ui.MeoAppRoot
import com.meolist.core.locale.LanguageStore
import com.meolist.core.theme.MeoTheme
import com.meolist.notification.DeepLink
import com.meolist.notification.DeepLinks
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Screen requested by a notification, consumed once the UI has navigated to it. */
    private var deepLink by mutableStateOf<DeepLink?>(null)

    override fun attachBaseContext(newBase: Context) {
        // Also re-applied on recreate(), which is how the language switch takes effect.
        super.attachBaseContext(LanguageStore.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // After a configuration change or process restore the back stack already holds the screen.
        if (savedInstanceState == null) deepLink = DeepLinks.from(intent)
        setContent {
            MeoTheme {
                MeoAppRoot(deepLink = deepLink, onDeepLinkHandled = { deepLink = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        DeepLinks.from(intent)?.let { deepLink = it }
    }
}
