package com.aslamshoh.glazaai.util

import android.graphics.Bitmap

/**
 * Цвет предмета на кадре камеры, прямо на телефоне (без сервера — мгновенно и без интернета).
 * Область берётся из рамки найденного предмета (нормированные 0..1) — только её центральная часть,
 * чтобы фон по краям не мешал; без рамки — небольшой квадрат в центре кадра.
 */
object ColorAnalyzer {
    private const val SAMPLE = 40

    fun analyze(frame: Bitmap, box: List<Double>?): ColorReport {
        val w = frame.width
        val h = frame.height
        var x1: Double
        var y1: Double
        var x2: Double
        var y2: Double
        if (box != null && box.size >= 4) {
            val bw = box[2] - box[0]
            val bh = box[3] - box[1]
            // Берём середину рамки: края чаще всего — фон, руки, края вещи.
            x1 = box[0] + bw * 0.2
            x2 = box[2] - bw * 0.2
            y1 = box[1] + bh * 0.2
            y2 = box[3] - bh * 0.2
        } else {
            x1 = 0.40
            x2 = 0.60
            y1 = 0.40
            y2 = 0.60
        }
        val left = (x1 * w).toInt().coerceIn(0, w - 1)
        val top = (y1 * h).toInt().coerceIn(0, h - 1)
        val right = (x2 * w).toInt().coerceIn(left + 1, w)
        val bottom = (y2 * h).toInt().coerceIn(top + 1, h)
        val region = Bitmap.createBitmap(frame, left, top, right - left, bottom - top)
        val small = Bitmap.createScaledBitmap(region, SAMPLE, SAMPLE, true)
        val pixels = IntArray(SAMPLE * SAMPLE)
        small.getPixels(pixels, 0, SAMPLE, 0, 0, SAMPLE, SAMPLE)
        return ColorNamer.analyze(pixels)
    }

    /** Для готовой миниатюры предмета (уже вырезанной по рамке) — берём середину целиком. */
    fun analyzeCrop(crop: Bitmap): ColorReport = analyze(crop, listOf(0.0, 0.0, 1.0, 1.0))

    /** Средняя яркость кадра 0..255 — запасной вариант, если в телефоне нет датчика света. */
    fun meanLuma(frame: Bitmap): Double {
        val small = Bitmap.createScaledBitmap(frame, 24, 24, true)
        val px = IntArray(24 * 24)
        small.getPixels(px, 0, 24, 0, 0, 24, 24)
        return px.sumOf { p ->
            0.299 * (p shr 16 and 0xFF) + 0.587 * (p shr 8 and 0xFF) + 0.114 * (p and 0xFF)
        } / px.size
    }
}
