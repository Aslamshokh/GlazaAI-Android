package com.aslamshoh.glazaai.network

import com.aslamshoh.glazaai.store.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

// Модели и сервисы для чтения со структурой, PDF, рукописного текста, описания фото/графиков/видео
// и режима «Рядом». Поля — как в glaza-ai-backend/app/routers/read.py, describe.py и live.py.
// Gson не подставляет значения по умолчанию из Kotlin, поэтому списки — nullable, читаем через orEmpty().

// ---------- Структурное чтение ----------

data class StructuredRequestBody(
    val image: String,
    val tableMode: String,
    val expect: String = "auto",
    val allowCloud: Boolean = false
)

data class ReadItem(val text: String, val level: Int?)

data class ReadBlock(
    val type: String,
    val text: String,
    val spoken: String?,
    val items: List<ReadItem>?,
    val headers: List<String>?,
    val rows: List<List<String>>?
)

/** Чек или документ, найденный в режиме «Текст»: итог, магазин, позиции, поля. */
data class DocInfo(
    val kind: String,
    val title: String,
    val summary: String,
    val total: String?,
    val fields: List<DocumentField>?,
    val items: List<DocumentItem>?,
    val itemsSpoken: String?
)

data class StructuredResult(
    val id: String,
    val title: String,
    val description: String,
    val blocks: List<ReadBlock>?,
    val text: String?,
    val spoken: String?,
    val hasTable: Boolean,
    val tableMode: String?,
    val uncertain: Boolean,
    val source: String?,
    val document: DocInfo?,
    /** «Лист обрезан справа — сдвиньте камеру правее» и т. п.; null, если всё в кадре. */
    val framingHint: String? = null,
    val timestamp: Long
)

data class PdfRequestBody(val pdf: String, val tableMode: String, val maxPages: Int? = null)

data class PdfPage(val page: Int, val source: String, val blocks: List<ReadBlock>?, val text: String?, val spoken: String?)

data class PdfResult(
    val id: String,
    val title: String,
    val description: String,
    val pageCount: Int,
    val pages: List<PdfPage>?,
    val timestamp: Long
)

data class CloudImageBody(val image: String, val allowCloud: Boolean)

/** style: "tactile" — для незрячих с рождения (осязание, размер, «лист в руках», без цвета);
 * "brief" — коротко: что и где. fromCamera — снимок камеры (тогда расстояние в шагах). */
data class PhotoDescribeBody(val image: String, val style: String, val fromCamera: Boolean)

data class HandwritingResult(
    val id: String,
    val title: String,
    val description: String,
    val recognizedText: String?,
    val spoken: String?,
    val source: String,
    val uncertain: Boolean,
    val timestamp: Long
)

// ---------- Описание ----------

data class DescribeResult(
    val id: String,
    val title: String,
    val description: String,
    val text: String?,
    val spoken: String?,
    val source: String,
    val timestamp: Long
)

data class VideoSegment(
    val index: Int,
    val start: Double,
    val end: Double,
    val label: String,
    val description: String,
    val text: String?
)

data class VideoResult(
    val id: String,
    val title: String,
    val description: String,
    val duration: Double,
    val segments: List<VideoSegment>?,
    val source: String,
    val timestamp: Long
)

data class Capabilities(
    val cloudAvailable: Boolean,
    val cloudProvider: String?,
    val localOnly: List<String>?,
    val cloudFeatures: List<String>?
)

// ---------- Режим «Рядом» ----------

data class NearFrameRequestBody(val image: String, val sessionId: String, val radiusM: Float)

data class NearItem(
    val kind: String,
    val key: String,
    val label: String,
    val direction: String,
    val distanceM: Double?,
    val layer: String,
    val say: String,
    val priority: Int
)

data class NearFrameResult(
    val id: String,
    val timestamp: Long,
    val items: List<NearItem>?,
    val say: String?,
    val radiusM: Double,
    val clear: Boolean,
    val processingMs: Int
)

// ---------- Сервисы ----------

object ReadService {
    suspend fun structured(imageDataUrl: String, tableMode: String, expectTable: Boolean): StructuredResult =
        ApiClient.postSlow(
            "/read/structured",
            StructuredRequestBody(imageDataUrl, tableMode, if (expectTable) "table" else "auto")
        )

    /** PDF целиком: сканы распознаются на сервере, это может занять несколько минут. */
    suspend fun pdf(pdfDataUrl: String, tableMode: String): PdfResult =
        ApiClient.postSlow("/read/pdf", PdfRequestBody(pdfDataUrl, tableMode), readSeconds = 300)

    suspend fun handwriting(imageDataUrl: String): HandwritingResult =
        ApiClient.postSlow("/read/handwriting", CloudImageBody(imageDataUrl, false))
}

object DescribeService {
    suspend fun photo(imageDataUrl: String, fromCamera: Boolean = false): DescribeResult =
        ApiClient.postSlow(
            "/describe/photo",
            PhotoDescribeBody(imageDataUrl, if (SettingsStore.tactileDescriptions) "tactile" else "brief", fromCamera)
        )

    suspend fun capabilities(): Capabilities = ApiClient.get("/describe/capabilities")

    /** Видео отправляется файлом (multipart): сервер режет его на фрагменты и описывает каждый. */
    suspend fun video(bytes: ByteArray, mime: String, segmentSeconds: Float = 5f): VideoResult =
        withContext(Dispatchers.IO) {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "video.mp4", bytes.toRequestBody(mime.toMediaTypeOrNull()))
                .addFormDataPart("segmentSeconds", segmentSeconds.toString())
                .build()
            val request = ApiClient.newRequestBuilder("/describe/video").post(body).build()
            val slow = ApiClient.client.newBuilder()
                .writeTimeout(120, TimeUnit.SECONDS)
                .readTimeout(600, TimeUnit.SECONDS)
                .callTimeout(660, TimeUnit.SECONDS)
                .build()
            ApiClient.execute<VideoResult>(request, slow)
        }
}

object NearService {
    suspend fun frame(imageDataUrl: String, sessionId: String, radiusM: Float): NearFrameResult =
        ApiClient.post("/vision/near-frame", NearFrameRequestBody(imageDataUrl, sessionId, radiusM))
}

// ---------- Проверка связи ----------

data class HealthResult(
    val status: String?,
    val aiProvider: String?,
    val aiKeyConfigured: Boolean?,
    val backendAuthEnabled: Boolean?,
    val smartProvider: String?
)

object HealthService {
    /** /health не требует ключа, поэтому ключ проверяем отдельно — любым защищённым запросом. */
    suspend fun check(): String {
        val h: HealthResult = ApiClient.get("/health")
        if (h.status != "ok") return "Сервер ответил, но работает с ошибкой."
        if (h.backendAuthEnabled == true) {
            try {
                ApiClient.get<Capabilities>("/describe/capabilities")
            } catch (e: ApiException.Server) {
                if (e.status == 401) return "Сервер на связи, но ключ доступа неверный. Проверьте X-API-Key."
                if (e.status != 404) throw e
            }
        }
        return "Связь с сервером есть, всё работает. Распознавание идёт на вашем сервере, без внешних сервисов."
    }
}
