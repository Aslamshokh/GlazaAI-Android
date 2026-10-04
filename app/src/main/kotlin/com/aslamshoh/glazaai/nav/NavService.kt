package com.aslamshoh.glazaai.nav

import com.aslamshoh.glazaai.network.ApiClient

/** Запросы навигации к backend (раздел nav, см. glaza-ai-backend/app/routers/nav.py). */
object NavService {
    suspend fun geocode(query: String, lat: Double?, lon: Double?): GeocodeResult =
        ApiClient.post("/nav/geocode", GeocodeRequestBody(query, lat, lon))

    suspend fun routes(origin: NavPoint, destination: NavPoint): RoutesResult =
        ApiClient.post("/nav/routes", RouteRequestBody(origin, destination))

    suspend fun walk(origin: NavPoint, destination: NavPoint): WalkRoute =
        ApiClient.post("/nav/walk", RouteRequestBody(origin, destination))

    suspend fun vehicleRef(imageDataUrl: String, expectedRef: String?): VehicleRefResult =
        ApiClient.post("/nav/vehicle-ref", VehicleRefRequestBody(imageDataUrl, expectedRef))
}
