package com.aslamshoh.glazaai.volunteer.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import com.aslamshoh.glazaai.volunteer.store.VolunteerStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.io.File

/** Снимок с фронтальной камеры (системное приложение «Камера»). Большинство камер понимают эти подсказки. */
class FrontCameraPreview : ActivityResultContracts.TakePicturePreview() {
    override fun createIntent(context: Context, input: Void?): Intent =
        super.createIntent(context, input)
            .putExtra("android.intent.extras.CAMERA_FACING", 1)
            .putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
            .putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
}

/** Сохраняет выбранное фото профиля во внутренней папке приложения (уменьшенное). Возвращает false при ошибке. */
fun saveAvatarFromUri(context: Context, uri: Uri): Boolean {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return false
        val old = VolunteerStore.avatarPath
        val file = File(context.filesDir, "avatar_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        VolunteerStore.setAvatar(file.absolutePath)
        if (old.isNotEmpty()) File(old).delete()
        true
    } catch (e: Exception) {
        false
    }
}

/** Открывает ссылку или карту во внешнем приложении. */
fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: android.content.ActivityNotFoundException) {
        com.aslamshoh.glazaai.volunteer.VolunteerRepo.showMessage("Не нашлась программа для этого действия.")
    }
}

/** Состояние загрузки данных экрана. */
class Loaded<T> {
    var data by androidx.compose.runtime.mutableStateOf<T?>(null)
    var error by androidx.compose.runtime.mutableStateOf<String?>(null)
    var loading by androidx.compose.runtime.mutableStateOf(true)
}

@androidx.compose.runtime.Composable
fun <T> rememberLoad(key: Any?, block: suspend () -> T): Loaded<T> {
    val st = androidx.compose.runtime.remember { Loaded<T>() }
    androidx.compose.runtime.LaunchedEffect(key) {
        st.loading = true
        try {
            st.data = block(); st.error = null
        } catch (e: Exception) {
            st.error = e.message ?: "Не удалось загрузить."
        } finally { st.loading = false }
    }
    return st
}
