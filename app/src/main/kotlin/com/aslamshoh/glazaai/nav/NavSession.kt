package com.aslamshoh.glazaai.nav

import kotlin.math.roundToInt

enum class NavPhase { IDLE, WALKING, WAITING, RIDING, ARRIVED }

/** Фраза для озвучивания. interrupt=true — прервать текущую речь (важное: «выходите!»). */
data class NavSpeech(val text: String, val interrupt: Boolean = false)

enum class WalkPurpose { TO_STOP, TO_DEST }

/** Просьба к сети: построить пеший маршрут (после выхода из транспорта или при уходе с пути). */
data class WalkRequest(
    val from: NavPoint,
    val to: NavPoint,
    val purpose: WalkPurpose,
    /** Если задано — после построения пути скажем «<introPrefix> N метров.» (напр. после выхода из транспорта). */
    val introPrefix: String? = null
)

class NavOutput(
    val speech: List<NavSpeech> = emptyList(),
    val walkRequest: WalkRequest? = null
) {
    companion object {
        val NONE = NavOutput()
    }
}

/**
 * Ведёт человека по выбранному маршруту: пешие повороты, остановка, ожидание, поездка по
 * остановкам, выход, дорога до цели. Чистая логика без Android и без сети: на вход — координаты и
 * команды пользователя, на выход — что сказать вслух и какой пеший маршрут запросить. Поэтому её
 * можно проверять обычными тестами «как будто идём по улице».
 *
 * Допуски выбраны с запасом на ошибку GPS в городе (10–20 м): «дошли» — 25–30 м от цели,
 * «поворот» — 18 м, остановка по пути засчитывается в 70 м.
 */
class NavSession(
    val destination: NavPlace,
    val transit: TransitOption? = null
) {
    var phase: NavPhase = NavPhase.IDLE
        private set

    /** Последняя подсказка — для кнопки «Повторить». */
    var instruction: String = ""
        private set

    /** Сколько осталось до ближайшей цели этапа (остановка/пункт назначения), метры. */
    var remainingM: Int? = null
        private set

    private var steps: List<WalkStep> = emptyList()
    private var stepIdx = 0
    private var announcedNear = false
    private var shape: List<NavPoint> = emptyList()
    private var walkTarget: NavPoint? = null
    private var walkPurpose = WalkPurpose.TO_DEST
    private var offRouteCount = 0
    private var lastRerouteMs = -1_000_000L
    private var lastMilestoneM = Int.MAX_VALUE
    private var rideNextIdx = 1
    private var rideWarnedFar = false
    private var fastCount = 0
    private var lastPoint: NavPoint? = null
    private var lastPointMs = 0L

    val expectedRef: String? get() = transit?.ref

    // ── старт этапов ────────────────────────────────────────────────────────────────────

    /** Начать пеший этап: до остановки посадки (TO_STOP) или до цели (TO_DEST). */
    fun startWalking(route: WalkRoute, purpose: WalkPurpose, intro: String? = null): NavOutput {
        walkPurpose = purpose
        walkTarget = if (purpose == WalkPurpose.TO_STOP) transit!!.boardStop.point else destination.point
        steps = route.steps ?: emptyList()
        shape = (route.shape ?: emptyList()).filter { it.size >= 2 }.map { NavPoint(it[0], it[1]) }
        stepIdx = 0
        announcedNear = false
        offRouteCount = 0
        lastMilestoneM = route.distanceM
        remainingM = route.distanceM
        phase = NavPhase.WALKING

        val out = ArrayList<NavSpeech>()
        intro?.let { out += NavSpeech(it) }
        steps.firstOrNull()?.text?.takeIf { it.isNotBlank() }?.let { out += NavSpeech(it) }
        instruction = out.joinToString(" ") { it.text }
        return NavOutput(out)
    }

    /** Пеший путь не нужен/не построился: мы уже у остановки — сразу переходим к ожиданию. */
    fun beginWaiting(): NavOutput {
        val t = transit ?: return NavOutput.NONE
        phase = NavPhase.WAITING
        fastCount = 0
        val dir = t.direction?.takeIf { it.isNotBlank() }?.let { " в сторону «$it»" } ?: ""
        val text = "Вы на остановке «${t.boardStop.name}». Ждите ${t.vehicle} ${t.ref}$dir. " +
            "Камера подскажет, когда она подъедет. Когда сядете, скажите «сел»."
        instruction = text
        return NavOutput(listOf(NavSpeech(text, interrupt = true)))
    }

    fun board(): NavOutput {
        val t = transit ?: return NavOutput.NONE
        if (phase == NavPhase.RIDING) return NavOutput.NONE
        phase = NavPhase.RIDING
        rideNextIdx = 1
        rideWarnedFar = false
        val text = "Хорошо, вы едете. Выходить на остановке «${t.alightStop.name}», через ${NavText.stops(t.stopsCount)}."
        instruction = text
        return NavOutput(listOf(NavSpeech(text)))
    }

    // ── обновление положения ────────────────────────────────────────────────────────────

    fun onLocation(p: NavPoint, nowMs: Long): NavOutput {
        val speed = lastPoint?.let { prev ->
            val dt = (nowMs - lastPointMs) / 1000.0
            if (dt > 0.3) NavGeo.distanceM(prev, p) / dt else null
        }
        lastPoint = p
        lastPointMs = nowMs

        return when (phase) {
            NavPhase.WALKING -> walkingUpdate(p, nowMs)
            NavPhase.WAITING -> waitingUpdate(p, speed)
            NavPhase.RIDING -> ridingUpdate(p)
            else -> NavOutput.NONE
        }
    }

    private fun walkingUpdate(p: NavPoint, nowMs: Long): NavOutput {
        val target = walkTarget ?: return NavOutput.NONE
        val speech = ArrayList<NavSpeech>()
        val dTarget = NavGeo.distanceM(p, target)
        remainingM = dTarget.toInt()

        val arrival = if (walkPurpose == WalkPurpose.TO_STOP) 25.0 else 30.0
        if (dTarget <= arrival) {
            return if (walkPurpose == WalkPurpose.TO_STOP) {
                beginWaiting()
            } else {
                phase = NavPhase.ARRIVED
                val text = "Вы прибыли: ${destination.name}."
                instruction = text
                NavOutput(listOf(NavSpeech(text, interrupt = true)))
            }
        }

        // Повороты: следующий манёвр — steps[stepIdx + 1]; последний («прибыли») не берём,
        // о прибытии сообщает проверка расстояния до цели выше.
        if (stepIdx + 1 < steps.size - 1) {
            val next = steps[stepIdx + 1]
            if (next.lat != null && next.lon != null) {
                val d = NavGeo.distanceM(p, NavPoint(next.lat, next.lon))
                if (d <= 18.0) {
                    stepIdx++
                    announcedNear = false
                    val text = steps[stepIdx].text
                    if (text.isNotBlank()) {
                        speech += NavSpeech(text)
                        instruction = text
                    }
                } else if (!announcedNear && d <= 45.0) {
                    announcedNear = true
                    val text = "Через ${NavText.meters(d.toInt())}: ${NavText.lowerFirst(next.text)}"
                    speech += NavSpeech(text)
                    instruction = text
                }
            }
        } else if (steps.isEmpty() || stepIdx + 1 >= steps.size - 1) {
            // Прямой участок до цели: напоминаем остаток каждые ~100 м.
            val rem = dTarget.toInt()
            if (lastMilestoneM - rem >= 100) {
                lastMilestoneM = rem
                val text = "Осталось ${NavText.meters(rem)}."
                speech += NavSpeech(text)
                instruction = text
            }
        }

        // Ушли с маршрута: три подряд замера дальше 45 м от линии — просим новый маршрут.
        var request: WalkRequest? = null
        if (shape.size >= 2) {
            offRouteCount = if (NavGeo.distanceToPolylineM(p, shape) > 45.0) offRouteCount + 1 else 0
            if (offRouteCount >= 3 && nowMs - lastRerouteMs >= 20_000) {
                lastRerouteMs = nowMs
                offRouteCount = 0
                request = WalkRequest(p, target, walkPurpose)
                speech += NavSpeech("Вы отклонились от маршрута. Строю новый.", interrupt = true)
            }
        }
        return NavOutput(speech, request)
    }

    private fun waitingUpdate(p: NavPoint, speed: Double?): NavOutput {
        val t = transit ?: return NavOutput.NONE
        val dStop = NavGeo.distanceM(p, t.boardStop.point)
        remainingM = dStop.toInt()
        // Уехали от остановки быстрее пешехода — значит, уже едем, даже если не сказали «сел».
        fastCount = if (dStop > 120.0 && speed != null && speed > 3.5) fastCount + 1 else 0
        return if (fastCount >= 3) board() else NavOutput.NONE
    }

    private fun ridingUpdate(p: NavPoint): NavOutput {
        val t = transit ?: return NavOutput.NONE
        val stops = t.stops ?: emptyList()
        val speech = ArrayList<NavSpeech>()
        val dAlight = NavGeo.distanceM(p, t.alightStop.point)
        remainingM = dAlight.toInt()

        if (stops.size >= 2) {
            val last = stops.size - 1
            var j = rideNextIdx
            while (j <= last) {
                if (NavGeo.distanceM(p, stops[j].point) <= 70.0) {
                    rideNextIdx = j + 1
                    if (j == last) return exitVehicle(p)
                    val remaining = last - j
                    val tail = if (remaining == 1) {
                        "Следующая — ваша: «${t.alightStop.name}». Приготовьтесь."
                    } else {
                        "Осталось ${NavText.stops(remaining)}."
                    }
                    val text = "Остановка «${stops[j].name}». $tail"
                    speech += NavSpeech(text, interrupt = remaining == 1)
                    instruction = text
                    break
                }
                j++
            }
            // Мы можем пропустить остановки из-за GPS — но выйти надо всё равно.
            if (speech.isEmpty() && dAlight <= 90.0) return exitVehicle(p)
        } else {
            if (dAlight <= 90.0) return exitVehicle(p)
            if (!rideWarnedFar && dAlight <= 400.0) {
                rideWarnedFar = true
                val text = "Скоро ваша остановка «${t.alightStop.name}». Приготовьтесь."
                speech += NavSpeech(text, interrupt = true)
                instruction = text
            }
        }
        return NavOutput(speech)
    }

    private fun exitVehicle(p: NavPoint): NavOutput {
        val t = transit ?: return NavOutput.NONE
        phase = NavPhase.WALKING
        walkPurpose = WalkPurpose.TO_DEST
        walkTarget = destination.point
        steps = emptyList()
        shape = emptyList()
        stepIdx = 0
        lastMilestoneM = Int.MAX_VALUE
        val text = "Выходите! Остановка «${t.alightStop.name}». Сейчас построю путь до цели."
        instruction = text
        return NavOutput(
            listOf(NavSpeech(text, interrupt = true)),
            WalkRequest(p, destination.point, WalkPurpose.TO_DEST, "Теперь пешком до «${destination.name}»,")
        )
    }

    // ── «Где я?» ────────────────────────────────────────────────────────────────────────

    fun statusPhrase(): String {
        val rem = remainingM
        return when (phase) {
            NavPhase.IDLE -> "Маршрут ещё не выбран."
            NavPhase.WALKING -> {
                val goal = if (walkPurpose == WalkPurpose.TO_STOP) "до остановки «${transit?.boardStop?.name}»" else "до цели «${destination.name}»"
                val left = if (rem != null) "Осталось ${NavText.meters(rem)} $goal. " else ""
                left + instruction
            }
            NavPhase.WAITING -> "Вы на остановке «${transit?.boardStop?.name}». Ждите ${transit?.vehicle} ${transit?.ref}."
            NavPhase.RIDING -> {
                val stops = transit?.stops ?: emptyList()
                val next = stops.getOrNull(rideNextIdx)?.name ?: transit?.alightStop?.name
                "Вы едете. Следующая остановка «$next». Выходить на «${transit?.alightStop?.name}»."
            }
            NavPhase.ARRIVED -> "Вы прибыли: ${destination.name}."
        }
    }
}

/**
 * Что сказать, когда камера на остановке увидела машины. Правила: «ваша» — часто, пока машина
 * близко и номер совпал; «чужая» — по разу в 20 с на каждый номер; «номер не разобрать» — редко.
 * Ложное «это ваша» хуже молчания, поэтому на несовпадение/нечитаемый номер говорим осторожно.
 */
class VehicleAnnouncer {
    private var lastMatchMs = -1_000_000L
    private var lastUnreadMs = -1_000_000L
    private val lastWrong = HashMap<String, Long>()

    fun process(vehicles: List<VehicleSighting>, expectedRef: String, nowMs: Long): NavSpeech? {
        for (v in vehicles) {
            if (v.match == true) {
                if (nowMs - lastMatchMs < 6_000) return null
                lastMatchMs = nowMs
                val near = v.distanceM != null && v.distanceM <= 30
                return NavSpeech("Это ваша: номер $expectedRef. ${distancePhrase(v)}", interrupt = near)
            }
        }
        for (v in vehicles) {
            val ref = v.refs?.firstOrNull()
            if (v.match == false && ref != null) {
                if (nowMs - (lastWrong[ref] ?: -1_000_000L) < 20_000) continue
                lastWrong[ref] = nowMs
                return NavSpeech("Подъезжает номер $ref. Это не ваша.")
            }
        }
        val unread = vehicles.firstOrNull { it.match == null && (it.distanceM == null || it.distanceM <= 40) }
        if (unread != null && nowMs - lastUnreadMs >= 20_000) {
            lastUnreadMs = nowMs
            return NavSpeech("Подъезжает ${unread.label}, номер не разобрать. Спросите у водителя.")
        }
        return null
    }

    private fun distancePhrase(v: VehicleSighting): String {
        val d = v.distanceM ?: return v.direction.replaceFirstChar { it.uppercase() } + "."
        val r = ((d / 5.0).roundToInt() * 5).coerceAtLeast(5)
        return "${v.direction.replaceFirstChar { it.uppercase() }}, примерно ${NavText.meters(r)}."
    }
}
