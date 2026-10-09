package com.aslamshoh.glazaai.volunteer.net

// ---------- Ответы сервера (/help/…) ----------
data class VolunteerProfile(
    val id: Int = 0,
    val name: String = "",
    val languages: List<String>? = null,
    val online: Boolean = false,
    val helpedLast30Days: Int = 0,
    val rating: Double? = null,
    val isNewbie: Boolean = true,
    val underReview: Boolean = false,
    val profileComplete: Boolean = true,
    val email: String? = null,
    val birthDate: String? = null,
    val country: String? = null,
    val city: String? = null,
    val specialties: List<String>? = null,
    val helpedTotal: Int = 0,
    val helpedSecondsTotal: Long = 0
)
data class OtpResponse(val sent: Boolean = false, val devCode: String? = null)
data class VerifyResponse(val token: String = "", val volunteerId: Int = 0, val profile: VolunteerProfile? = null)
data class Offer(
    val requestId: Int = 0,
    val userName: String = "",
    val language: String = "ru",
    val urgent: Boolean = false,
    val waitedSeconds: Int = 0,
    val leftSeconds: Int = 0,
    val note: String? = null
)
data class IncomingResponse(val online: Boolean = false, val activeRequestId: Int? = null, val offers: List<Offer>? = null)
data class Location(val lat: Double = 0.0, val lon: Double = 0.0)
/** Данные для видеозвонка внутри приложения (LiveKit); null, если сервер ещё не настроен на LiveKit. */
data class LiveKitInfo(val url: String = "", val token: String = "", val room: String = "", val publishVideo: Boolean = false)
data class AcceptResponse(
    val requestId: Int = 0,
    val roomUrl: String = "",
    val room: String = "",
    val livekit: LiveKitInfo? = null,
    val userName: String = "",
    val language: String = "ru",
    val urgent: Boolean = false,
    val location: Location? = null
)
data class OkResponse(val ok: Boolean = true)

/** Анкета волонтёра (PUT /help/volunteers/me). */
data class ProfileBody(
    val name: String,
    val birthDate: String,
    val languages: List<String>,
    val acceptTerms: Boolean,
    val email: String? = null,
    val country: String? = null,
    val city: String? = null,
    val specialties: List<String> = emptyList()
)

/** Один звонок в истории. status: completed | cancelled | active. */
data class HistoryItem(
    val requestId: Int = 0,
    val userName: String = "",
    val language: String = "ru",
    val urgent: Boolean = false,
    val note: String? = null,
    val acceptedAt: Long = 0,
    val durationSeconds: Long = 0,
    val status: String = "completed",
    val rating: Int? = null
)
data class HistoryResponse(val items: List<HistoryItem>? = null)

data class StatsResponse(
    val period: String = "month",
    val completed: Int = 0,
    val cancelled: Int = 0,
    val avgRating: Double? = null,
    val helpSeconds: Long = 0,
    val byWeekday: List<Int>? = null
)
