package com.aslamshoh.glazaai.nav

/** Модели ответов backend, раздел nav (см. glaza-ai-backend/app/routers/nav.py). Списки — nullable:
 * Gson не знает про значения по умолчанию Kotlin, а нам нельзя падать, если поле не пришло. */

data class NavPoint(val lat: Double, val lon: Double)

data class GeocodeRequestBody(val query: String, val lat: Double?, val lon: Double?)
data class RouteRequestBody(val origin: NavPoint, val destination: NavPoint)
data class VehicleRefRequestBody(val image: String, val expectedRef: String?)

data class NavPlace(
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    val distanceM: Int?
) {
    val point: NavPoint get() = NavPoint(lat, lon)
}

data class GeocodeResult(val id: String, val timestamp: Long, val places: List<NavPlace>?)

data class WalkStep(val text: String, val distanceM: Int, val lat: Double?, val lon: Double?)

data class WalkRoute(
    val distanceM: Int,
    val durationS: Int,
    val steps: List<WalkStep>?,
    val shape: List<List<Double>>?
)

data class StopInfo(val name: String, val lat: Double, val lon: Double, val walkM: Int) {
    val point: NavPoint get() = NavPoint(lat, lon)
}

data class RouteStop(val name: String, val lat: Double, val lon: Double) {
    val point: NavPoint get() = NavPoint(lat, lon)
}

data class TransitOption(
    val ref: String,
    val vehicle: String,
    val vehicleEn: String,
    val direction: String?,
    val network: String?,
    val boardStop: StopInfo,
    val alightStop: StopInfo,
    val stopsCount: Int,
    val rideDistanceM: Int,
    val walkTotalM: Int,
    val etaMin: Int,
    val stops: List<RouteStop>?
)

data class RoutesResult(
    val id: String,
    val timestamp: Long,
    val directDistanceM: Int,
    val walking: WalkRoute?,
    val transit: List<TransitOption>?,
    val notes: List<String>?
)

data class VehicleSighting(
    val label: String,
    val direction: String,
    val distanceM: Double?,
    val refs: List<String>?,
    val match: Boolean?,
    val confidence: Double
)

data class VehicleRefResult(
    val id: String,
    val timestamp: Long,
    val vehicles: List<VehicleSighting>?,
    val processingMs: Int
)

/** Вариант, который пользователь выбирает голосом: транспорт или пешком. */
sealed class RouteChoice {
    data class Transit(val option: TransitOption) : RouteChoice()
    data class Walk(val route: WalkRoute) : RouteChoice()
}
