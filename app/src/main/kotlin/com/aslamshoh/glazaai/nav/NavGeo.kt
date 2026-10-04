package com.aslamshoh.glazaai.nav

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Расстояния на местности (метры). Чистая математика, без Android. */
object NavGeo {
    private const val EARTH_R = 6_371_000.0

    fun distanceM(a: NavPoint, b: NavPoint): Double {
        val p1 = Math.toRadians(a.lat)
        val p2 = Math.toRadians(b.lat)
        val dphi = p2 - p1
        val dl = Math.toRadians(b.lon - a.lon)
        val h = sin(dphi / 2) * sin(dphi / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * EARTH_R * asin(min(1.0, sqrt(h)))
    }

    /** Расстояние от точки до ломаной (маршрута): проекция на плоскость вокруг точки хватает для
     * сотен метров. */
    fun distanceToPolylineM(p: NavPoint, line: List<NavPoint>): Double {
        if (line.isEmpty()) return Double.MAX_VALUE
        if (line.size == 1) return distanceM(p, line[0])
        val kx = cos(Math.toRadians(p.lat)) * 111_320.0
        val ky = 110_540.0
        fun x(q: NavPoint) = (q.lon - p.lon) * kx
        fun y(q: NavPoint) = (q.lat - p.lat) * ky
        var best = Double.MAX_VALUE
        for (i in 0 until line.size - 1) {
            val ax = x(line[i]); val ay = y(line[i])
            val bx = x(line[i + 1]); val by = y(line[i + 1])
            val dx = bx - ax; val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else max(0.0, min(1.0, (-ax * dx - ay * dy) / len2))
            val cx = ax + t * dx; val cy = ay + t * dy
            best = min(best, sqrt(cx * cx + cy * cy))
        }
        return best
    }
}
