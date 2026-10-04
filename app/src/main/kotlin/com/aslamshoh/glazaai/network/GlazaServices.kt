package com.aslamshoh.glazaai.network

import java.net.URLEncoder

/** Vision API — описание сцены (используется режимом «Предметы»). Путь и формат точно
 * соответствуют glaza-ai-backend/app/routers/vision.py. */
object VisionService {
    suspend fun describeScene(imageDataUrl: String): SceneResult =
        ApiClient.post("/vision/describe-scene", ImageRequestBody(imageDataUrl))

    /** Один кадр видеопотока для живого режима: backend ведёт трекинг объектов между кадрами
     * (по sessionId) и возвращает направление, расстояние и приоритет каждого. */
    suspend fun liveFrame(imageDataUrl: String, sessionId: String): LiveFrameResult =
        ApiClient.post("/vision/live-frame", LiveFrameRequestBody(imageDataUrl, sessionId))
}

/** OCR API — распознавание печатного/рукописного текста. Используется и для «Текст», и для
 * «Документы»: отдельного распознавания полей паспорта backend не делает, это тот же общий
 * OCR — честно показываем это в интерфейсе, а не выдаём за специализированное распознавание. */
object OcrService {
    suspend fun recognize(imageDataUrl: String, language: String): OcrResult =
        ApiClient.post("/ocr/recognize", OcrRequestBody(imageDataUrl, language))
}

/** Currency Recognition API — номинал купюры по фото. */
object CurrencyService {
    suspend fun recognize(imageDataUrl: String): CurrencyResult =
        ApiClient.post("/currency/recognize", ImageRequestBody(imageDataUrl))
}

/** Barcode API — поиск товара по штрих-коду через Open Food Facts. Сам штрих-код считывается
 * на устройстве нативно через ZXing (см. ui/BarcodeScreen.kt), backend только ищет товар по
 * уже распознанному коду. */
object BarcodeService {
    suspend fun lookup(code: String): BarcodeResult {
        val encoded = URLEncoder.encode(code, "UTF-8")
        return ApiClient.get("/barcode/$encoded")
    }
}
