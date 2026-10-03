package com.aslamshoh.glazaai.util

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Backend (app/services/image_utils.py) ожидает ровно data:image/jpeg;base64,... — тот же
 * формат, что строит canvas.toDataURL('image/jpeg', ...) в web-версии (см. также
 * GlazaAI-iOS/GlazaAI/Utils/ImageEncoding.swift — идентичная логика на iOS). Фото с камеры
 * телефона может быть 12+ Мп — сжимаем по длинной стороне перед кодированием, чтобы не
 * упираться в лимит backend (MAX_IMAGE_MB, по умолчанию 8 МБ) и не гонять лишние мегабайты.
 */
object ImageEncoding {
    fun dataUrl(bitmap: Bitmap, maxDimension: Int = 1600, quality: Int = 85): String {
        val resized = resize(bitmap, maxDimension)
        val stream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64"
    }

    private fun resize(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longestSide = max(bitmap.width, bitmap.height)
        if (longestSide <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / longestSide.toFloat()
        val newWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val newHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
