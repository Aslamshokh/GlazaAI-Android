package com.aslamshoh.glazaai.network

import java.net.URLEncoder

/** Vision API — описание сцены (используется режимом «Предметы»). Путь и формат точно
 * соответствуют glaza-ai-backend/app/routers/vision.py. */
object VisionService {
    suspend fun describeScene(imageDataUrl: String, detail: Boolean = false): SceneResult =
        if (detail) ApiClient.postSlow("/vision/describe-scene", SceneRequestBody(imageDataUrl, true))
        else ApiClient.post("/vision/describe-scene", ImageRequestBody(imageDataUrl))

    /** «Сколько людей рядом»: только число и стороны, без распознавания лиц. */
    suspend fun peopleNearby(imageDataUrl: String): PeopleNearbyResult =
        ApiClient.post("/vision/people-nearby", ImageRequestBody(imageDataUrl))

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

/** «Чек / Документ»: фото -> поля. Распознавание на сервере может занять до пары минут. */
object DocumentService {
    suspend fun read(imageDataUrl: String, kind: String): DocumentResult =
        ApiClient.postSlow("/document/read", DocumentRequestBody(imageDataUrl, kind))
}

/** «Что в руке»: предмет, цвет, состояние овощей и фруктов. Серверная модель тратит несколько секунд. */
object InspectService {
    suspend fun inspect(imageDataUrl: String): InspectResult =
        ApiClient.postSlow("/vision/inspect", ImageRequestBody(imageDataUrl))
}

/** Отзыв тестера — приходит владельцу приложения (сервер сохраняет и пересылает в Telegram). */
object FeedbackService {
    suspend fun send(body: FeedbackRequestBody): FeedbackResult = ApiClient.post("/feedback", body)
}

/** «Позвать волонтёра»: сервер подбирает свободного волонтёра и выдаёт секретную ссылку на видеокомнату. */
object HelpService {
    suspend fun request(body: HelpRequestBody): HelpStatus = ApiClient.post("/help/requests", body)

    suspend fun status(requestId: Int, deviceId: String): HelpStatus =
        ApiClient.get("/help/requests/$requestId?deviceId=${URLEncoder.encode(deviceId, "UTF-8")}")

    suspend fun cancel(requestId: Int, deviceId: String): HelpStatus =
        ApiClient.post("/help/requests/$requestId/cancel", HelpDeviceBody(deviceId))

    suspend fun rate(requestId: Int, deviceId: String, rating: Int, comment: String? = null): HelpOk =
        ApiClient.post("/help/requests/$requestId/rate", HelpRateBody(deviceId, rating, comment))

    suspend fun report(requestId: Int, deviceId: String, reason: String): HelpOk =
        ApiClient.post("/help/requests/$requestId/report", HelpReportBody(deviceId, reason))
}
