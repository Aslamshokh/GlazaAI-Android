package com.aslamshoh.glazaai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aslamshoh.glazaai.ui.GlazaAITheme
import com.aslamshoh.glazaai.ui.GlazaNavHost
import com.aslamshoh.glazaai.util.SharedInbox

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        takeShared(intent)
        setContent {
            GlazaAITheme {
                GlazaNavHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeShared(intent)
    }

    /** «Поделиться» картинкой из другого приложения (мессенджер, галерея, браузер). */
    @Suppress("DEPRECATION")
    private fun takeShared(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) return
        SharedInbox.put(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
    }
}
