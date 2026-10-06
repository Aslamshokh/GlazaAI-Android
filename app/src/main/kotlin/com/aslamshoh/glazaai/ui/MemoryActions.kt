package com.aslamshoh.glazaai.ui

import android.content.Context
import android.graphics.Bitmap
import com.aslamshoh.glazaai.network.LiveTrack
import com.aslamshoh.glazaai.network.VisionService
import com.aslamshoh.glazaai.store.HistoryStore
import com.aslamshoh.glazaai.store.MemoryItem
import com.aslamshoh.glazaai.store.MemoryStore
import com.aslamshoh.glazaai.util.HeadingProvider
import com.aslamshoh.glazaai.util.ImageEncoding
import com.aslamshoh.glazaai.util.MemoryText
import com.aslamshoh.glazaai.util.NearObject
import com.aslamshoh.glazaai.util.PlaceFix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Что показать и произнести после «запомни…» / «где…». */
internal class MemoryOutcome(val title: String, val text: String, val thumb: Bitmap?)

/**
 * «Запомни ключи здесь»: одновременно ловим GPS и описываем, что рядом (по найденным камерой
 * предметам, а если их нет — запросом к серверу), затем сохраняем вещь вместе со снимком.
 * Вызывать из корутины на главном потоке (LocationManager требует главный Looper).
 */
internal suspend fun rememberHere(
    context: Context,
    name: String,
    place: String,
    frame: Bitmap?,
    tracks: List<LiveTrack>
): MemoryOutcome = coroutineScope {
    val fixJob = async { PlaceFix.current(context) }
    val aroundJob = async { describeAround(name, frame, tracks) }
    val fix = fixJob.await()
    val around = aroundJob.await()

    val item = MemoryStore.remember(
        name = name,
        place = place,
        around = around,
        point = fix?.point,
        accuracyM = fix?.accuracyM,
        photo = frame
    )
    val text = MemoryText.rememberedAnswer(name, place, around, hasGps = fix != null)
    HistoryStore.addEntry("Память", "Запомнено: $name", text, frame)
    MemoryOutcome(item.name.replaceFirstChar { it.uppercase() }, text, frame)
}

private suspend fun describeAround(name: String, frame: Bitmap?, tracks: List<LiveTrack>): String {
    val near = tracks.map { NearObject(it.label, it.direction, it.distanceM) }
    val fromLive = MemoryText.aroundPhrase(near, name)
    if (fromLive.isNotEmpty() || frame == null) return fromLive
    return try {
        val dataUrl = withContext(Dispatchers.Default) { ImageEncoding.dataUrl(frame, 960, 80) }
        val scene = MemoryText.normalizeScene(VisionService.describeScene(dataUrl).description)
        if (scene.startsWith("Не удалось")) "" else scene
    } catch (e: Exception) {
        ""
    }
}

/** «Где мои ключи?»: время, описание, расстояние и сторона от текущего положения. */
internal suspend fun recallItem(context: Context, item: MemoryItem): MemoryOutcome {
    var here: PlaceFix? = null
    var heading: Float? = null
    if (item.lat != null && item.lon != null) {
        here = PlaceFix.current(context, 4000L)
        if (here != null) heading = readHeading(context)
    }
    val text = MemoryText.recallAnswer(item, System.currentTimeMillis(), here?.point, here?.accuracyM, heading)
    return MemoryOutcome(item.name.replaceFirstChar { it.uppercase() }, text, MemoryStore.loadThumb(item))
}

/** Куда сейчас смотрит телефон (компас). null — датчика нет. Ждём секунду, пока сгладится. */
private suspend fun readHeading(context: Context): Float? {
    val provider = HeadingProvider(context)
    if (!provider.available) return null
    provider.start()
    return try {
        delay(1000)
        provider.degrees
    } finally {
        provider.stop()
    }
}
