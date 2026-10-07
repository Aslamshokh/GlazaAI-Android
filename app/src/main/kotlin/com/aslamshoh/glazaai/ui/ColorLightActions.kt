package com.aslamshoh.glazaai.ui

import android.content.Context
import android.graphics.Bitmap
import com.aslamshoh.glazaai.network.LiveTrack
import com.aslamshoh.glazaai.util.ColorAnalyzer
import com.aslamshoh.glazaai.util.ColorNamer
import com.aslamshoh.glazaai.util.LightMeter
import com.aslamshoh.glazaai.util.LightText
import com.aslamshoh.glazaai.util.MemoryText

/**
 * «Какого цвета эта рубашка?» — считается прямо на телефоне, без сервера.
 * Порядок выбора предмета: названный голосом и найденный камерой → предмет, на который нажали →
 * центр кадра. Если назвали предмет, а камера его не видит, честно говорим об этом.
 */
internal fun colorAnswer(item: String, tracks: List<LiveTrack>, selected: LiveTrack?, frame: Bitmap?): String {
    if (frame == null) return "Камера ещё не дала кадр. Подождите секунду и спросите ещё раз."
    val named = if (item.isBlank()) null else tracks
        .filter { MemoryText.sameItem(it.label, item) }
        .maxByOrNull { (it.box[2] - it.box[0]) * (it.box[3] - it.box[1]) }
    val track = named ?: if (item.isBlank()) selected else null
    if (track != null && track.box.size >= 4) {
        val report = ColorAnalyzer.analyze(frame, track.box)
        return ColorNamer.speak(report, track.label.replaceFirstChar { it.uppercase() })
    }
    val report = ColorAnalyzer.analyze(frame, null)
    val centre = ColorNamer.speak(report, "В центре кадра")
    return if (item.isBlank()) {
        "$centre Чтобы узнать цвет вещи, держите её по центру камеры."
    } else {
        "Не вижу, где $item, поэтому смотрю на центр кадра. $centre"
    }
}

/** «Включён ли свет?» — датчик света телефона; без датчика — грубая оценка по яркости кадра. */
internal suspend fun lightAnswer(context: Context, frame: Bitmap?): String {
    val lux = LightMeter.read(context)
    val luma = if (lux == null && frame != null) ColorAnalyzer.meanLuma(frame) else null
    return LightText.answer(lux, luma)
}
