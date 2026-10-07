package com.aslamshoh.glazaai.util

/** Что человек попросил голосом на главном экране («ИИ Глаз, найди ключи»). */
sealed class VoiceCommand {
    data class Find(val query: String) : VoiceCommand()
    data class Navigate(val destination: String?) : VoiceCommand()
    object ReadText : VoiceCommand()
    object Currency : VoiceCommand()
    object Product : VoiceCommand()
    object Qr : VoiceCommand()
    object History : VoiceCommand()
    object WhatsAround : VoiceCommand()

    /** «Запомни ключи здесь» / «я положил очки на стол»: item — вещь, place — что человек сказал про место. */
    data class Remember(val item: String, val place: String) : VoiceCommand()

    /** «Где мои ключи?» / «где я оставил очки?». Пустой item — «что ты помнишь». explicit = человек
     *  прямо спросил про оставленное («где я оставил…», «напомни…») — тогда, если вещь не запомнена,
     *  честно отвечаем «не помню», а не запускаем поиск камерой. */
    data class Recall(val item: String, val explicit: Boolean = false) : VoiceCommand()

    /** «Забудь ключи». */
    data class Forget(val item: String) : VoiceCommand()
    object MemoryList : VoiceCommand()

    /** «Прочитай чек» / «что в документе». */
    object Document : VoiceCommand()

    /** «Оставить отзыв». */
    object Feedback : VoiceCommand()

    /** «Какого цвета эта рубашка?» — item пустой, если назвали просто «какого цвета это». */
    data class ColorOf(val item: String) : VoiceCommand()

    /** «Включён ли свет?» / «горит ли свет в комнате?» / «темно ли?». */
    object Light : VoiceCommand()
    object Unknown : VoiceCommand()
}

/**
 * Простой разбор голосовой команды по ключевым словам (без облачного ИИ — работает офлайн).
 * Порядок проверок важен: «найди» и «навигация» раньше общих слов вроде «текст».
 */
object VoiceCommands {
    private val fillers = setOf(
        "ии", "глаз", "ай", "пожалуйста", "мне", "мои", "мой", "моя", "моё", "мою", "пожалуйста",
        "ну", "а", "и", "где", "найди", "найти", "найдите", "поищи", "ищи", "искать", "покажи", "это"
    )

    fun parse(raw: String?): VoiceCommand {
        val text = raw?.lowercase()?.replace('ё', 'е')?.replace(Regex("[^а-яa-z0-9 ]"), " ")
            ?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        if (text.isEmpty()) return VoiceCommand.Unknown

        fun has(vararg keys: String) = keys.any { text.contains(it) }

        // Цвет и свет — раньше всего: «какого цвета» не должно уйти в поиск, а «где» — в память.
        parseLight(text)?.let { return it }
        parseColor(text)?.let { return it }

        // «Память вещей» — раньше «найди», иначе «где ключи» уйдёт в поиск камерой.
        parseMemory(text)?.let { return it }

        if (has("найди", "найти", "найдите", "поищи", "ищи", "где ")) {
            val query = text.split(" ").filter { it.isNotBlank() && it !in fillers }.joinToString(" ")
            return VoiceCommand.Find(query)
        }
        if (has("навигац", "маршрут", "как добраться", "как дойти", "как пройти", "веди", "отведи", "проводи", "дойти до")) {
            return VoiceCommand.Navigate(destinationFrom(text))
        }
        if (has("отзыв", "написать разработчик", "обратная связь", "сообщить об ошибке")) return VoiceCommand.Feedback
        if (Regex("(?:^| )(?:чек|чеки|чека|чеке|квитанци|документ|накладн|справк|паспорт)").containsMatchIn(text)) return VoiceCommand.Document
        if (has("qr", "кью", "кю ар", "куар")) return VoiceCommand.Qr
        if (has("купюр", "банкнот", "деньги", "денег", "рубл", "сколько стоит купюра")) return VoiceCommand.Currency
        if (has("штрих", "товар", "продукт", "сканер")) return VoiceCommand.Product
        if (has("прочитай", "прочти", "читай", "текст", "надпись", "что написано")) return VoiceCommand.ReadText
        if (has("истори")) return VoiceCommand.History
        if (has("вокруг", "передо мной", "предмет", "объект", "что тут", "что здесь")) return VoiceCommand.WhatsAround
        return VoiceCommand.Unknown
    }

    private val lightPhrases = listOf(
        "включен ли свет", "включено ли свет", "включена ли лампа", "горит ли свет", "горит ли лампа",
        "горит ли лампочка", "есть ли свет", "светит ли", "свет включен", "свет горит", "свет есть",
        "темно ли", "светло ли", "темно здесь", "светло здесь", "темно в комнате", "светло в комнате",
        "освещение", "освещенность", "яркость света", "насколько светло", "насколько темно",
        "выключен ли свет", "выключено ли свет", "свет выключен", "включи ли свет"
    )

    private fun parseLight(text: String): VoiceCommand? {
        if (text.contains("найди") || text.contains("поищи")) return null
        if (lightPhrases.any { text.contains(it) }) return VoiceCommand.Light
        // «в комнате свет?» — слово «свет» вместе с «комнат/лампа/включ/горит»
        val words = text.split(" ")
        if (words.any { it == "свет" } && (text.contains("комнат") || text.contains("включ") || text.contains("горит") || text.contains("лампа"))) {
            return VoiceCommand.Light
        }
        return null
    }

    private val colorFillers = setOf(
        "какого", "какой", "какая", "какое", "какие", "каких", "цвета", "цвет", "цвете", "цветов", "у", "этот", "эта",
        "это", "эти", "этой", "этого", "этих", "этому", "мой", "моя", "мое", "мои", "моей", "моего", "моих", "ии", "глаз",
        "ай", "скажи", "подскажи", "определи", "назови", "пожалуйста", "мне", "у меня", "вот", "здесь", "тут", "то",
        "что", "за", "в", "руках", "руке", "кадре", "камере", "цветом", "ли", "а", "и", "какого-то"
    )

    private fun parseColor(text: String): VoiceCommand? {
        if (text.contains("найди") || text.contains("поищи")) return null
        val isColor = Regex("(?:^| )(?:цвет|цвета|цвете|цветом|цветов)(?: |$)").containsMatchIn(text) ||
            text.contains("что за цвет") || text.contains("определи цвет")
        if (!isColor) return null
        val item = MemoryText.cleanItem(
            text.split(" ").filter { it.isNotBlank() && it !in colorFillers }.joinToString(" ")
        )
        return VoiceCommand.ColorOf(item)
    }

    private const val PUT_VERBS =
        "оставил|оставила|положил|положила|поставил|поставила|убрал|убрала|спрятал|спрятала|припрятал|припрятала"
    private val rememberStart = Regex("^(?:(?:ии|глаз|ай) )*(?:запомни|запомните|запомнить|запиши|сохрани)(?: пожалуйста)?(?: (.+))?$")
    private val putStart = Regex("^(?:(?:ии|глаз|ай) )*я (?:$PUT_VERBS) (.+)$")
    private val forgetStart = Regex("^(?:(?:ии|глаз|ай) )*(?:забудь|забыть|удали|сотри) (.+?)(?: из памяти)?$")
    private val askWords = Regex("(?:^| )(?:где|куда) (.+)$")
    private val listPhrases = listOf(
        "что ты помнишь", "что ты запомнил", "что я запомнил", "что я оставил", "что запомнено",
        "что ты запомнила", "что я оставила", "какие вещи", "мои вещи", "память вещей", "что в памяти"
    )
    private val placeMarkers = setOf(
        "здесь", "тут", "сюда", "на", "в", "во", "под", "за", "над", "около", "возле", "у", "рядом", "перед", "между"
    )
    private val hereWords = setOf("здесь", "тут", "сюда")
    private val verbWords = (PUT_VERBS + "|дел|дела|запомнил|запомнила|запомнить").split("|").toSet()

    private fun parseMemory(text: String): VoiceCommand? {
        if (listPhrases.any { text.contains(it) }) return VoiceCommand.MemoryList

        forgetStart.find(text)?.let { m ->
            val first = text.substringBefore(' ')
            val isForget = first.startsWith("забу") || text.contains("из памяти") || text.startsWith("ии") || text.startsWith("глаз")
            val item = MemoryText.cleanItem(m.groupValues[1])
            if (isForget && item.isNotEmpty() && !item.contains("истор")) return VoiceCommand.Forget(item)
        }

        rememberStart.find(text)?.let { m ->
            var rest = m.groupValues[1]
            rest = rest.replace(Regex("^(?:что |где |место |я )+"), "")
            val words = rest.split(" ").filter { it.isNotBlank() && it !in verbWords }
            return splitItemAndPlace(words)
        }
        putStart.find(text)?.let { m ->
            val words = m.groupValues[1].split(" ").filter { it.isNotBlank() }
            return splitItemAndPlace(words)
        }

        if (!text.contains("найди") && !text.contains("поищи")) {
            askWords.find(text)?.let { m ->
                val all = m.groupValues[1].split(" ").filter { it.isNotBlank() }
                val explicit = all.any { it in verbWords } || text.contains("напомни") || text.contains("вспомни")
                val words = all.filter { it !in verbWords }
                return VoiceCommand.Recall(MemoryText.cleanItem(words.joinToString(" ")), explicit)
            }
        }
        return null
    }

    /** «ключи на кухонном столе» -> (ключи, на кухонном столе); «ключи здесь» -> (ключи, ""). */
    private fun splitItemAndPlace(words: List<String>): VoiceCommand.Remember {
        val i = words.indexOfFirst { it in placeMarkers }
        var item = if (i < 0) words else words.subList(0, i)
        var placeWords = if (i < 0) emptyList() else words.subList(i, words.size).filter { it !in hereWords }
        if (item.isEmpty() && placeWords.isNotEmpty() && placeWords.first() !in placeMarkers) {
            item = placeWords
            placeWords = emptyList()
        }
        return VoiceCommand.Remember(MemoryText.cleanItem(item.joinToString(" ")), placeWords.joinToString(" "))
    }

    private fun destinationFrom(text: String): String? {
        val m = Regex("(?:^| )(?:до|в|во|на|к|ко) (.+)$").find(text) ?: return null
        val dest = m.groupValues[1].trim()
        return dest.takeIf { it.isNotEmpty() }
    }
}
