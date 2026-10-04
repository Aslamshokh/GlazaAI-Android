package com.aslamshoh.glazaai.nav

import kotlin.math.roundToInt

/** Команды, которые пользователь произносит во время навигации. */
sealed class NavCommand {
    data class Pick(val index: Int) : NavCommand()          // «первый», «второй», «3»
    data class PickRef(val ref: String) : NavCommand()      // «двенадцатая»/«12»
    object Walk : NavCommand()                              // «пешком»
    object Repeat : NavCommand()                            // «повтори»
    object Cancel : NavCommand()                            // «отмена», «стоп»
    object Boarded : NavCommand()                           // «сел», «я в маршрутке»
    object WhereAmI : NavCommand()                          // «где я», «что дальше»
    object Around : NavCommand()                            // «что вокруг»
    object Unknown : NavCommand()
}

/** Тексты и разбор речи для навигации. Чистая логика без Android — проверяется обычными тестами. */
object NavText {

    fun plural(n: Int, one: String, few: String, many: String): String {
        val m100 = n % 100
        val m10 = n % 10
        return when {
            m100 in 11..14 -> many
            m10 == 1 -> one
            m10 in 2..4 -> few
            else -> many
        }
    }

    /** «120 метров», «1,2 километра», «2 километра». Мелкие расстояния округляем, чтобы речь не
     * была занудной («137 метров» -> «140 метров»). */
    fun meters(m: Int): String {
        if (m < 1000) {
            val r = when {
                m < 20 -> maxOf(m, 1)
                m < 100 -> (m / 5.0).roundToInt() * 5
                else -> (m / 10.0).roundToInt() * 10
            }
            return "$r ${plural(r, "метр", "метра", "метров")}"
        }
        val km = (m / 100.0).roundToInt() / 10.0
        return if (km == km.toInt().toDouble()) {
            val k = km.toInt()
            "$k ${plural(k, "километр", "километра", "километров")}"
        } else {
            "${km.toString().replace('.', ',')} километра"
        }
    }

    fun minutes(min: Int): String {
        val m = maxOf(min, 1)
        return "$m ${plural(m, "минута", "минуты", "минут")}"
    }

    fun stops(n: Int): String = "$n ${plural(n, "остановка", "остановки", "остановок")}"

    fun lowerFirst(s: String): String = if (s.isEmpty()) s else s[0].lowercaseChar() + s.substring(1)

    /** «Хочу пойти на вокзал» -> «вокзал»: выбрасываем служебные слова в начале фразы. */
    fun cleanDestination(spoken: String): String {
        var t = spoken.trim().trim('.', ',', '!', '?').lowercase().replace('ё', 'е')
        val prefixes = listOf(
            "я хочу пойти", "хочу пойти", "мне нужно попасть", "мне надо попасть", "мне нужно", "мне надо",
            "как добраться до", "как пройти до", "как пройти на", "как пройти в", "как добраться в", "как добраться на",
            "проложи маршрут до", "проложи маршрут в", "проложи маршрут на", "построй маршрут до",
            "построй маршрут в", "построй маршрут на", "я хочу", "хочу", "пойти", "идти", "едем", "поехали",
            "отведи меня", "веди меня", "иду"
        )
        var changed = true
        while (changed) {
            changed = false
            for (p in prefixes) {
                if (t.startsWith("$p ")) { t = t.removePrefix("$p ").trim(); changed = true }
            }
            for (w in listOf("в ", "на ", "до ", "к ")) {
                if (t.startsWith(w) && t.length > w.length + 2) { t = t.removePrefix(w).trim(); changed = true }
            }
        }
        return t
    }

    fun parseCommand(spoken: String): NavCommand {
        val t = spoken.lowercase().replace('ё', 'е').trim()
        if (t.isEmpty()) return NavCommand.Unknown
        val tokens = t.split(Regex("[^а-яa-z0-9]+")).filter { it.isNotEmpty() }
        fun has(vararg parts: String) = parts.any { t.contains(it) }
        fun word(vararg ws: String) = tokens.any { it in ws }
        fun stem(vararg st: String) = tokens.any { tk -> st.any { tk.startsWith(it) } }
        return when {
            has("отмен", "стоп", "хватит", "закончи", "останови", "прекрати") -> NavCommand.Cancel
            has("что вокруг", "что рядом", "что впереди", "что передо мной") -> NavCommand.Around
            has("где я", "где мы", "что дальше", "куда идти", "куда дальше", "далеко ли") -> NavCommand.WhereAmI
            word("сел", "села", "сели", "поехали", "поехал") ||
                has("я в маршрутке", "я в автобусе", "я в транспорте", "я еду") -> NavCommand.Boarded
            has("повтор", "еще раз", "не расслышал", "что ты сказал") -> NavCommand.Repeat
            has("пешком", "пешая", "пешеход", "своим ходом") -> NavCommand.Walk
            stem("перв") || word("один", "раз") -> NavCommand.Pick(0)
            stem("втор") || word("два") -> NavCommand.Pick(1)
            stem("трет") || word("три") -> NavCommand.Pick(2)
            stem("четверт") || word("четыре") -> NavCommand.Pick(3)
            else -> {
                val num = tokens.firstOrNull { Regex("\\d{1,3}[а-яa-z]?").matches(it) }
                when {
                    num == null -> NavCommand.Unknown
                    num.length == 1 && num[0] in '1'..'4' -> NavCommand.Pick(num[0] - '1')
                    else -> NavCommand.PickRef(num.uppercase())
                }
            }
        }
    }

    /** Порядок вариантов: транспорт (не больше 3), «пешком» — последним, а при коротком пути
     * (до 600 м) — первым: на 400 метров маршрутку никто не ждёт. */
    fun buildChoices(routes: RoutesResult): List<RouteChoice> {
        val transit = (routes.transit ?: emptyList()).take(3).map { RouteChoice.Transit(it) }
        val walk = routes.walking?.let { RouteChoice.Walk(it) }
        return when {
            walk == null -> transit
            walk.route.distanceM <= 600 -> listOf<RouteChoice>(walk) + transit
            else -> transit + walk
        }
    }

    private val ordinals = listOf("Первый", "Второй", "Третий", "Четвёртый")

    fun describeChoice(index: Int, c: RouteChoice): String {
        val head = ordinals.getOrElse(index) { "Вариант ${index + 1}" }
        return when (c) {
            is RouteChoice.Walk ->
                "$head — пешком, ${meters(c.route.distanceM)}, около ${minutes(maxOf(c.route.durationS / 60, 1))}."
            is RouteChoice.Transit -> {
                val o = c.option
                val dir = o.direction?.takeIf { it.isNotBlank() }?.let { " в сторону «$it»" } ?: ""
                "$head — ${o.vehicle} ${o.ref}$dir. Сесть на остановке «${o.boardStop.name}», " +
                    "${meters(o.boardStop.walkM)} от вас. ${stops(o.stopsCount)}, выйти на «${o.alightStop.name}». " +
                    "Примерно ${minutes(o.etaMin)}, не считая ожидания."
            }
        }
    }

    fun describeChoices(choices: List<RouteChoice>): String {
        val sb = StringBuilder()
        sb.append(if (choices.size == 1) "Нашёл один вариант. " else "Нашёл ${choices.size} ${plural(choices.size, "вариант", "варианта", "вариантов")}. ")
        choices.forEachIndexed { i, c -> sb.append(describeChoice(i, c)).append(' ') }
        sb.append(if (choices.size == 1) "Идём?" else "Какой выбираете?")
        return sb.toString()
    }

    fun describePlaces(places: List<NavPlace>): String {
        val sb = StringBuilder("Нашёл несколько мест. ")
        places.forEachIndexed { i, p ->
            val d = p.distanceM?.let { ", ${meters(it)} от вас" } ?: ""
            val addr = p.address.takeIf { it.isNotBlank() && !it.startsWith(p.name) }?.let { ", $it" } ?: ""
            sb.append("${ordinals.getOrElse(i) { "Вариант ${i + 1}" }} — ${p.name}$addr$d. ")
        }
        sb.append("Какое?")
        return sb.toString()
    }

    /** Одно место можно брать без переспрашивания: оно единственное или заметно ближе остальных. */
    fun isUnambiguous(places: List<NavPlace>): Boolean {
        if (places.size <= 1) return true
        val a = places[0].distanceM ?: return false
        val b = places[1].distanceM ?: return false
        return a * 2 < b
    }
}
