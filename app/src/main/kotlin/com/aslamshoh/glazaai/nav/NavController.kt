package com.aslamshoh.glazaai.nav

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aslamshoh.glazaai.network.ApiClient
import com.aslamshoh.glazaai.speech.SpeechSynthesizer
import com.aslamshoh.glazaai.store.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class NavDialog { IDLE, ASK_DEST, CONFIRM_PLACE, CHOOSE_ROUTE, GUIDING }

/** Что камера делает сейчас: ищет препятствия, читает номер подъезжающей машины или выключена. */
enum class CameraMode { OFF, OBSTACLES, VEHICLES }

/**
 * Голосовой диалог навигации: «куда идём» -> варианты -> выбор голосом -> подсказки по пути.
 * Здесь же склейка с сетью, GPS и речью; сама логика ведения по маршруту — в NavSession,
 * разбор фраз — в NavText (обе без Android, проверены тестами).
 *
 * Вызывать с главного потока (rememberCoroutineScope в Compose это обеспечивает).
 */
class NavController(
    private val scope: CoroutineScope,
    private val location: LocationProvider,
    private val voice: VoiceInput,
    /** «Что вокруг?» — подробное описание предметов вокруг (даёт камерный конвейер). */
    private val describeAround: () -> String
) {
    var dialog by mutableStateOf(NavDialog.IDLE)
        private set
    var heard by mutableStateOf("")
        private set
    var hint by mutableStateOf("Нажмите кнопку и скажите, куда идти")
        private set
    var busy by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var places by mutableStateOf<List<NavPlace>>(emptyList())
        private set
    var choices by mutableStateOf<List<RouteChoice>>(emptyList())
        private set
    var destination by mutableStateOf<NavPlace?>(null)
        private set
    var phase by mutableStateOf(NavPhase.IDLE)
        private set
    var remainingM by mutableStateOf<Int?>(null)
        private set
    var vehicleLabel by mutableStateOf<String?>(null)
        private set

    private var session: NavSession? = null
    private var misses = 0
    private var destQueryTries = 0
    private var routeBusy = false
    private var lastRepeat: String = ""

    val expectedRef: String? get() = session?.expectedRef
    val vehicleAnnouncer = VehicleAnnouncer()

    val cameraMode: CameraMode
        get() = when {
            dialog != NavDialog.GUIDING -> CameraMode.OFF
            phase == NavPhase.WAITING -> CameraMode.VEHICLES
            phase == NavPhase.WALKING -> CameraMode.OBSTACLES
            else -> CameraMode.OFF
        }

    init {
        location.listener = { p, acc -> onLocation(p, acc) }
    }

    // ── кнопки ──────────────────────────────────────────────────────────────────────────

    /** Главная кнопка-микрофон: в покое начинает диалог, иначе слушает ответ/команду. */
    fun onMicClick() {
        SpeechSynthesizer.stop()
        errorMessage = null
        misses = 0
        if (dialog == NavDialog.IDLE) startDialog() else listenOnce()
    }

    fun onTextSubmitted(text: String) {
        if (text.isBlank()) return
        SpeechSynthesizer.stop()
        voice.cancel()
        errorMessage = null
        if (dialog == NavDialog.IDLE) {
            dialog = NavDialog.ASK_DEST
            destQueryTries = 0
        }
        handleSpoken(text)
    }

    fun onRepeat() {
        val s = session
        val text = when {
            dialog == NavDialog.GUIDING && s != null -> s.instruction.ifBlank { s.statusPhrase() }
            lastRepeat.isNotBlank() -> lastRepeat
            else -> hint
        }
        say(text, interrupt = true)
    }

    fun onWhere() {
        val s = session
        say(if (s != null) s.statusPhrase() else "Маршрут ещё не выбран.", interrupt = true)
    }

    fun onBoarded() {
        session?.let { dispatch(it.board()) }
    }

    fun pickPlace(index: Int) {
        val p = places.getOrNull(index) ?: return
        SpeechSynthesizer.stop()
        voice.cancel()
        proceedWithPlace(p)
    }

    fun pickChoice(index: Int) {
        val c = choices.getOrNull(index) ?: return
        SpeechSynthesizer.stop()
        voice.cancel()
        startChoice(c)
    }

    fun stop(announce: Boolean = true) {
        voice.cancel()
        SpeechSynthesizer.stop()
        session = null
        dialog = NavDialog.IDLE
        phase = NavPhase.IDLE
        remainingM = null
        places = emptyList()
        choices = emptyList()
        destination = null
        busy = false
        heard = ""
        hint = "Навигация остановлена. Нажмите кнопку, чтобы выбрать новую цель."
        if (announce) say("Навигация остановлена.", interrupt = true)
    }

    fun release() {
        voice.cancel()
        SpeechSynthesizer.stop()
        location.listener = null
    }

    // ── диалог ─────────────────────────────────────────────────────────────────────────

    private fun startDialog() {
        dialog = NavDialog.ASK_DEST
        destQueryTries = 0
        places = emptyList()
        choices = emptyList()
        destination = null
        session = null
        scope.launch {
            if (!location.isEnabled()) {
                fail("Геолокация выключена. Включите «Местоположение» в шторке телефона и попробуйте снова.")
                dialog = NavDialog.IDLE
                return@launch
            }
            if (location.point == null) {
                hint = "Определяю, где вы находитесь…"
                say("Определяю, где вы находитесь. Подождите несколько секунд.", interrupt = true)
                val p = awaitLocation()
                if (p == null) {
                    fail("Не удалось определить местоположение. Выйдите на открытое место и повторите.")
                    dialog = NavDialog.IDLE
                    return@launch
                }
            }
            speakThenListen("Куда идём?")
        }
    }

    private fun handleSpoken(raw: String) {
        heard = raw
        val cmd = NavText.parseCommand(raw)
        if (cmd == NavCommand.Cancel) {
            stop()
            return
        }
        when (dialog) {
            NavDialog.ASK_DEST -> searchPlace(NavText.cleanDestination(raw).ifBlank { raw })
            NavDialog.CONFIRM_PLACE -> {
                val idx = (cmd as? NavCommand.Pick)?.index
                when {
                    idx != null && idx in places.indices -> proceedWithPlace(places[idx])
                    cmd == NavCommand.Repeat -> speakThenListen(NavText.describePlaces(places))
                    else -> speakThenListen("Скажите «первое», «второе» или «третье». Или «отмена».")
                }
            }
            NavDialog.CHOOSE_ROUTE -> {
                val choice = when (cmd) {
                    is NavCommand.Pick -> choices.getOrNull(cmd.index)
                    NavCommand.Walk -> choices.firstOrNull { it is RouteChoice.Walk }
                    is NavCommand.PickRef -> choices.firstOrNull { it is RouteChoice.Transit && it.option.ref.equals(cmd.ref, true) }
                    else -> null
                }
                when {
                    choice != null -> startChoice(choice)
                    cmd == NavCommand.Repeat -> speakThenListen(NavText.describeChoices(choices))
                    else -> speakThenListen("Не понял. Скажите «первый», «второй» или «пешком».")
                }
            }
            NavDialog.GUIDING -> handleGuidingCommand(cmd)
            NavDialog.IDLE -> {}
        }
    }

    private fun handleGuidingCommand(cmd: NavCommand) {
        when (cmd) {
            NavCommand.Repeat -> onRepeat()
            NavCommand.WhereAmI -> onWhere()
            NavCommand.Boarded -> onBoarded()
            NavCommand.Around -> say(describeAround(), interrupt = true)
            else -> say("Не понял. Скажите: повтори, где я, сел, что вокруг или стоп.", interrupt = true)
        }
    }

    private fun searchPlace(query: String) {
        val origin = location.point
        busy = true
        hint = "Ищу «$query»…"
        scope.launch {
            try {
                val result = NavService.geocode(query, origin?.lat, origin?.lon)
                val found = result.places.orEmpty().take(3)
                busy = false
                if (found.isEmpty()) {
                    destQueryTries++
                    if (destQueryTries >= 3) {
                        say("Не нашёл. Нажмите кнопку и назовите точнее: улицу и номер дома.", interrupt = true)
                        hint = "Не нашёл «$query». Попробуйте назвать точнее."
                    } else {
                        hint = "Не нашёл «$query». Повторите."
                        speakThenListen("Не нашёл «$query». Назовите точнее, например улицу и номер дома.")
                    }
                    return@launch
                }
                places = found
                if (NavText.isUnambiguous(found)) {
                    proceedWithPlace(found[0])
                } else {
                    dialog = NavDialog.CONFIRM_PLACE
                    hint = "Выберите место"
                    speakThenListen(NavText.describePlaces(found))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                busy = false
                fail(ApiClient.messageFor(e))
            }
        }
    }

    private fun proceedWithPlace(place: NavPlace) {
        destination = place
        places = emptyList()
        val origin = location.point
        if (origin == null) {
            fail("Не знаю, где вы. Подождите GPS и повторите.")
            return
        }
        busy = true
        hint = "Строю маршрут до «${place.name}»…"
        say("Нашёл: ${place.name}. Строю маршрут.", interrupt = true)
        scope.launch {
            try {
                val routes = NavService.routes(origin, place.point)
                busy = false
                val list = NavText.buildChoices(routes)
                if (list.isEmpty()) {
                    fail((routes.notes ?: emptyList()).firstOrNull() ?: "Не удалось построить маршрут.")
                    dialog = NavDialog.IDLE
                    return@launch
                }
                choices = list
                val prefix = if (routes.transit.isNullOrEmpty()) "Маршруток и автобусов по этому пути не нашёл. " else ""
                if (list.size == 1 && list[0] is RouteChoice.Walk) {
                    say(prefix + "Пойдём пешком.", interrupt = true)
                    startChoice(list[0])
                } else {
                    dialog = NavDialog.CHOOSE_ROUTE
                    hint = "Выберите, как добраться"
                    speakThenListen(prefix + NavText.describeChoices(list))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                busy = false
                dialog = NavDialog.IDLE
                fail(ApiClient.messageFor(e))
            }
        }
    }

    private fun startChoice(choice: RouteChoice) {
        val dest = destination ?: return
        val origin = location.point ?: run { fail("Не знаю, где вы. Подождите GPS."); return }
        dialog = NavDialog.GUIDING
        choices = emptyList()
        when (choice) {
            is RouteChoice.Walk -> {
                val s = NavSession(dest)
                session = s
                dispatch(s.startWalking(choice.route, WalkPurpose.TO_DEST,
                    "Иду пешком до «${dest.name}», ${NavText.meters(choice.route.distanceM)}."))
            }
            is RouteChoice.Transit -> {
                val o = choice.option
                val s = NavSession(dest, o)
                session = s
                if (o.boardStop.walkM < 40) {
                    dispatch(s.beginWaiting())
                } else {
                    busy = true
                    hint = "Строю путь до остановки…"
                    scope.launch {
                        val intro = "Сначала дойдите до остановки «${o.boardStop.name}», ${NavText.meters(o.boardStop.walkM)}. " +
                            "Потом ${o.vehicle} ${o.ref}."
                        val route = try {
                            NavService.walk(origin, o.boardStop.point)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // Без подробного пути: ведём «по прямой» с остатком расстояния.
                            WalkRoute(o.boardStop.walkM, 0, null, null)
                        }
                        busy = false
                        if (session === s) dispatch(s.startWalking(route, WalkPurpose.TO_STOP, intro))
                    }
                }
            }
        }
    }

    // ── положение и ведение ─────────────────────────────────────────────────────────────

    private fun onLocation(p: NavPoint, accuracyM: Float) {
        val s = session ?: return
        if (dialog != NavDialog.GUIDING || accuracyM > 60f) return
        dispatch(s.onLocation(p, SystemClock.elapsedRealtime()))
    }

    private fun dispatch(out: NavOutput) {
        val s = session ?: return
        out.speech.forEach { say(it.text, it.interrupt) }
        phase = s.phase
        remainingM = s.remainingM
        if (s.instruction.isNotBlank()) hint = s.instruction
        if (s.phase == NavPhase.ARRIVED) hint = "Вы прибыли: ${s.destination.name}"

        val req = out.walkRequest ?: return
        if (routeBusy) return
        routeBusy = true
        scope.launch {
            try {
                val route = NavService.walk(req.from, req.to)
                if (session === s) {
                    val intro = req.introPrefix?.let { "$it ${NavText.meters(route.distanceM)}." }
                    dispatch(s.startWalking(route, req.purpose, intro))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say("Не удалось построить путь: ${ApiClient.messageFor(e)}", interrupt = false)
            } finally {
                routeBusy = false
            }
        }
    }

    /** Подсказка про подъезжающую машину (из камерного конвейера на остановке). */
    fun onVehicleSpeech(text: String, interrupt: Boolean) {
        say(text, interrupt)
        hint = text
    }

    // ── речь и слушание ──────────────────────────────────────────────────────────────────

    private fun say(text: String, interrupt: Boolean = false) {
        if (text.isBlank()) return
        lastRepeat = text
        SpeechSynthesizer.speakQueued(text, SettingsStore.speechRate, interrupt)
    }

    private fun fail(message: String) {
        errorMessage = message
        hint = message
        say(message, interrupt = true)
    }

    private fun speakThenListen(text: String) {
        say(text, interrupt = true)
        scope.launch {
            awaitSpeechDone()
            listenOnce()
        }
    }

    private fun listenOnce() {
        if (!voice.isAvailable) {
            hint = "Голосовой ввод недоступен на этом телефоне. Введите текстом ниже."
            say("Голосовой ввод недоступен. Введите текстом.", interrupt = true)
            return
        }
        voice.listen { text ->
            scope.launch {
                if (text == null) {
                    misses++
                    if (misses <= 2 && dialog != NavDialog.IDLE && dialog != NavDialog.GUIDING) {
                        speakThenListen("Не расслышал. Повторите, пожалуйста.")
                    } else if (dialog != NavDialog.IDLE) {
                        hint = "Не расслышал. Нажмите кнопку и повторите."
                    }
                } else {
                    misses = 0
                    handleSpoken(text)
                }
            }
        }
    }

    private suspend fun awaitSpeechDone() {
        delay(500)
        var waited = 0
        while (SpeechSynthesizer.isSpeaking && waited < 25_000) {
            delay(150)
            waited += 150
        }
        delay(250)
    }

    private suspend fun awaitLocation(): NavPoint? {
        var waited = 0
        while (location.point == null && waited < 25_000) {
            delay(500)
            waited += 500
        }
        return location.point
    }
}
