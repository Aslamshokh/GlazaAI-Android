package com.aslamshoh.glazaai.util

import com.aslamshoh.glazaai.network.PeopleNearbyResult

/** Что сказать про людей в кадре. Только счёт и стороны — лица не распознаются и не запоминаются. */
object PeopleText {
    private val order = listOf("слева", "по центру", "справа")

    fun speak(r: PeopleNearbyResult): String {
        val n = if (r.peopleCount > 0 || r.people.isEmpty()) r.peopleCount else r.people.size
        if (n <= 0) return "В кадре людей не видно."
        val head = when {
            n == 1 -> "В кадре один человек"
            else -> "В кадре ${count(n)}"
        }
        val byDir = r.people.groupingBy { if (it.direction in order) it.direction!! else "по центру" }.eachCount()
        if (byDir.isEmpty()) return "$head."
        val parts = order.filter { it in byDir }.map { dir ->
            val k = byDir.getValue(dir)
            if (k == 1) "один $dir" else "${k} $dir"
        }
        return "$head: " + parts.joinToString(", ") + "."
    }

    /** «2 человека», «5 человек» — правильное склонение. */
    fun count(n: Int): String {
        val mod100 = n % 100
        val mod10 = n % 10
        val word = when {
            mod100 in 11..14 -> "человек"
            mod10 == 1 -> "человек"
            mod10 in 2..4 -> "человека"
            else -> "человек"
        }
        return "$n $word"
    }
}
