package com.aslamshoh.glazaai.util

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Картинка, которой поделились с приложением («Поделиться» → Eyes AI). MainActivity кладёт, навигация забирает. */
object SharedInbox {
    var pending by mutableStateOf<Uri?>(null)
        private set

    fun put(uri: Uri?) { if (uri != null) pending = uri }
    fun take(): Uri? = pending.also { pending = null }
}
