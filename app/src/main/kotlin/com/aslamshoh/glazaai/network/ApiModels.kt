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
    val found: Boolean,
    val source: String,
    val timestamp: Long
)

data class ErrorResponse(val detail: String)
