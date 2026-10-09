package com.aslamshoh.glazaai.network

// Модели запросов/ответов согласованы один в один с glaza-ai-backend/app/schemas.py —
// см. также GlazaAI-iOS/GlazaAI/Models/APIModels.swift (та же структура на iOS).

data class RecognizedObject(
    val label: String,
    val direction: String?,
    val distanceApprox: String?
)

// ---------- Тела запросов ----------

data class ImageRequestBody(val image: String)

data class OcrRequestBody(val image: String, val language: String)

data class DocumentRequestBody(val image: String, val kind: String)

data class FeedbackRequestBody(
    val message: String,
    val rating: Int?,
    val category: String?,
    val tester: String?,
    val screen: String?,
    val appVersion: String?,
    val device: String?
)

data class LiveFrameRequestBody(val image: String, val sessionId: String)

data class FindObjectRequestBody(val image: String, val query: String)

// ---------- Ответы backend ----------

data class SceneResult(
    val id: String,
    val title: String,
    val description: String,
    val confidence: Double?,
    val source: String,
    val timestamp: Long,
    val objects: List<RecognizedObject> = emptyList()
)

data class FindObjectResult(
    val id: String,
    val title: String,
    val description: String,
    val source: String,
    val timestamp: Long,
    val found: Boolean,
    val `object`: RecognizedObject?
)

data class PeopleNearbyResult(
    val id: String,
    val title: String,
    val description: String,
    val source: String,
    val timestamp: Long,
    val peopleCount: Int,
    val people: List<RecognizedObject> = emptyList()
)

data class OcrResult(
    val id: String,
    val title: String,
    val description: String,
    val recognizedText: String,
    val language: String,
    val source: String,
    val timestamp: Long
)

data class CurrencyResult(
    val id: String,
    val title: String,
    val description: String,
    val currency: String,
    val denomination: String,
    val lowConfidenceWarning: Boolean,
    val source: String,
    val timestamp: Long
)

data class SurroundingsScanResult(
    val id: String,
    val title: String,
    val description: String,
    val source: String,
    val timestamp: Long,
    val hasObstacle: Boolean,
    val obstacleDirection: String?,
    val obstacleDescription: String?,
    val trafficLightPresent: Boolean,
    val trafficLightState: String?
)

data class BarcodeResult(
    val id: String,
    val title: String,
    val description: String,
    val code: String,
    val productName: String?,
    val brand: String?,
    val quantity: String?,
    val ingredients: String?,
    val allergens: String?,
    val nutriScore: String?,
    /** Страна, пищевая ценность на 100 г и фото товара — приходят от обновлённого backend (необязательно). */
    val country: String? = null,
    val nutrition: String? = null,
    val imageUrl: String? = null,
    val found: Boolean,
    val source: String,
    val timestamp: Long
)

/** Один найденный и отслеживаемый объект живого режима (POST /vision/live-frame). */
data class LiveTrack(
    val trackId: Int,
    val label: String,
    val labelEn: String,
    val direction: String,
    val distanceM: Double?,
    val zone: String,
    val approaching: Boolean,
    val priority: String,
    val isNew: Boolean,
    val confidence: Double,
    val box: List<Double>,
    val trafficLightState: String?
)

data class LiveFrameResult(
    val id: String,
    val timestamp: Long,
    val tracks: List<LiveTrack>?,
    val qualityHint: String?,
    val processingMs: Int
)

data class ErrorResponse(val detail: String)

/** Поле документа или чека: «Итого — 341 рубль 90 копеек». */
data class DocumentField(val label: String, val value: String)

/** Позиция чека; spoken — готовая фраза «Молоко, 2 шт, 120 рублей». */
data class DocumentItem(val name: String, val quantity: String?, val amount: Double?, val spoken: String)

data class DocumentResult(
    val id: String,
    val title: String,
    val description: String,
    val kind: String,
    val store: String?,
    val date: String?,
    val total: Double?,
    val totalSure: Boolean?,
    val items: List<DocumentItem>?,
    val fields: List<DocumentField>?,
    val recognizedText: String?,
    val warnings: List<String>?,
    val timestamp: Long
)

/** Цвет предмета: название и доля площади (0..1). */
data class InspectColor(val name: String, val share: Double)

/** «Что в руке»: предмет, цвет и (для овощей и фруктов) состояние по цвету. */
data class InspectResult(
    val id: String,
    val found: Boolean,
    val title: String,
    val description: String,
    val label: String?,
    val confidence: Double?,
    val inHand: Boolean?,
    val box: List<Double>?,
    val alternatives: List<String>?,
    val colors: List<InspectColor>?,
    val colorText: String?,
    val condition: String?,
    val conditionState: String?,
    val attention: Boolean?,
    val isProduce: Boolean?,
    val timestamp: Long
)

data class FeedbackResult(val id: String, val saved: Boolean, val forwarded: Boolean, val description: String)

// ---------- «Помощь волонтёра» ----------
data class HelpRequestBody(
    val deviceId: String,
    val name: String,
    val language: String,
    val urgent: Boolean,
    val lat: Double?,
    val lon: Double?
)
data class HelpDeviceBody(val deviceId: String)
data class HelpRateBody(val deviceId: String, val rating: Int, val comment: String?)
data class HelpReportBody(val deviceId: String, val reason: String)
data class HelpOk(val ok: Boolean = true)

/** Состояние вызова: waiting → accepted → finished; либо expired / cancelled. */
data class HelpStatus(
    val requestId: Int,
    val status: String,
    val urgent: Boolean = false,
    val waitedSeconds: Int = 0,
    val leftSeconds: Int = 0,
    val volunteerName: String? = null,
    val roomUrl: String? = null,
    val room: String? = null,
    /** Данные для видеозвонка внутри приложения; null — LiveKit на сервере не настроен, тогда открывается Jitsi по roomUrl. */
    val livekit: LiveKitInfo? = null,
    val volunteersOnline: Int? = null
)

/** Адрес и персональный токен для подключения к комнате LiveKit. publishVideo — включать ли камеру (у пользователя да, у волонтёра нет). */
data class LiveKitInfo(
    val url: String = "",
    val token: String = "",
    val room: String = "",
    val publishVideo: Boolean = false
)

