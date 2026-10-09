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
    val underReview: Boolean = false
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
data class AcceptResponse(
    val requestId: Int = 0,
    val roomUrl: String = "",
    val userName: String = "",
    val language: String = "ru",
    val urgent: Boolean = false,
    val location: Location? = null
)
data class OkResponse(val ok: Boolean = true)
