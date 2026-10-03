package com.aslamshoh.glazaai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aslamshoh.glazaai.ui.GlazaAITheme
import com.aslamshoh.glazaai.ui.GlazaNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GlazaAITheme {
                GlazaNavHost()
            }
        }
    }
}
