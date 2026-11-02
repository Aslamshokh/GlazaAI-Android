package com.aslamshoh.glazaai.util

/** Что человек попросил голосом на главном экране («EYES AI, найди ключи»). */
sealed class VoiceCommand {
    data class Find(val query: String) : VoiceCommand()
    object ReadText : VoiceCommand()
    object Currency : VoiceCommand()
    object Product : VoiceCommand()
    object History : VoiceCommand()
    object WhatsAround : VoiceCommand()

    /** «Опиши подробно» / «расскажи подробнее». */
    object Detail : VoiceCommand()

    /** «Сколько людей рядом» / «кто передо мной». */
    object People : VoiceCommand()

    /** «Стоп» — замолчать. */
    object Stop : VoiceCommand()

    /** «Прочитай чек» / «что в документе». */
    object Document : VoiceCommand()

    /** «Что у меня в руке» / «какой это овощ» / «свежий ли помидор». */
    object Inspect : VoiceCommand()

    /** «Оставить отзыв». */
    object Feedback : VoiceCommand()

    /** «Позови волонтёра» (volunteer = true — сразу начать вызов) / «нужна помощь», «позвони близкому». */
    data class Help(val volunteer: Boolean) : VoiceCommand()

    /** «Какого цвета эта рубашка?» — item пустой, если назвали просто «какого цвета это». */
    data class ColorOf(val item: String) : VoiceCommand()

    /** «Рядом» / «что под ногами» — ассистент ближней зоны: ступеньки, бордюры, вход, переход. */
    object Near : VoiceCommand()

    /** Файлы и медиа: kind = photo | graph | handwriting | pdf | video. */
    data class Media(val kind: String) : VoiceCommand()
    object Unknown : VoiceCommand()
}

/**
 * Простой разбор голосовой команды по ключевым словам (без облачного ИИ — работает офлайн).
 * Порядок проверок важен: «найди» и «навигация» раньше общих слов вроде «текст».
 */
object VoiceCommands {
    private val fillers = setOf(
        "ии", "глаз", "ай", "айс", "айз", "eyes", "эй", "ai", "пожалуйста", "мне", "мои", "мой", "моя", "моё", "мою", "пожалуйста",
        "ну", "а", "и", "где", "найди", "найти", "найдите", "поищи", "ищи", "искать", "покажи", "это"
    )

    fun parse(raw: String?): VoiceCommand {
        val text = raw?.lowercase()?.replace('ё', 'е')?.replace(Regex("[^а-яa-z0-9 ]"), " ")
            ?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        if (text.isEmpty()) return VoiceCommand.Unknown

        fun has(vararg keys: String) = keys.any { text.contains(it) }

        if (text == "стоп" || text == "хватит" || text == "остановись" || text == "выключи звук" || text == "тихо") return VoiceCommand.Stop

        // «Сколько людей» и «опиши подробно» — раньше общего «передо мной»/«что здесь».
        if (has("сколько людей", "сколько человек", "есть ли люди", "есть ли кто", "люди рядом", "людей рядом", "кто рядом", "кто передо мной", "кто здесь", "кто тут", "люди вокруг")) return VoiceCommand.People

        // Цвет — раньше «найди»: «какого цвета» не должно уйти в поиск.
        parseColor(text)?.let { return it }

        // Помощь: «найди волонтёра» — это вызов, а не поиск предмета, поэтому раньше «найди».
        parseHelp(text)?.let { return it }

        if (text == "рядом" || has(
                "режим рядом", "что под ногами", "препятстви", "ступеньк", "бордюр", "пешеходный переход",
                "навигац", "маршрут", "как пройти", "как дойти", "веди меня", "проводи"
            )
        ) {
            return VoiceCommand.Near
        }

        if (has("найди", "найти", "найдите", "поищи", "ищи", "где ")) {
            val query = text.split(" ").filter { it.isNotBlank() && it !in fillers }.joinToString(" ")
            return VoiceCommand.Find(query)
        }
        if (has("отзыв", "написать разработчик", "обратная связь", "сообщить об ошибке")) return VoiceCommand.Feedback
        parseMedia(text)?.let { return it }
        if (Regex("(?:^| )(?:чек|чеки|чека|чеке|квитанци|документ|накладн|справк|паспорт)").containsMatchIn(text)) return VoiceCommand.Document
        if (has("купюр", "банкнот", "деньги", "денег", "рубл", "сколько стоит купюра")) return VoiceCommand.Currency
        if (has("штрих", "товар", "продукт", "сканер", "qr", "кью ар", "куар")) return VoiceCommand.Product
        if (has("прочитай", "прочти", "читай", "текст", "надпись", "что написано")) return VoiceCommand.ReadText
        if (has(
                "в руке", "в руках", "что я держу", "что держу", "что это за овощ", "что это за фрукт", "какой это овощ",
                "какой это фрукт", "что за овощ", "что за фрукт", "свежий ли", "свежая ли", "свежее ли", "спелый ли",
                "спелая ли", "спелое ли", "испортил", "созрел", "не гнил"
            )
        ) return VoiceCommand.Inspect
        if (has("истори")) return VoiceCommand.History
        // «Опиши подробно» — после всех конкретных команд («прочитай подробно чек» остаётся чеком).
        if (has("подробно", "подробнее", "детально", "в деталях", "расскажи больше")) return VoiceCommand.Detail
        if (has("вокруг", "передо мной", "предмет", "объект", "что тут", "что здесь")) return VoiceCommand.WhatsAround
        return VoiceCommand.Unknown
    }

    private val helpVolunteerWords = listOf(
        "волонтер", "волонтир", "добровол", "позови человека", "нужен человек", "позвать человека", "живой человек"
    )
    private val helpGeneralWords = listOf(
        "нужна помощь", "нужна мне помощь", "помогите", "помоги мне", "помоги", "на помощь", "экстренн", "позвони близк",
        "позвонить близк", "позвони родным", "позвони маме", "тревога", "открой помощь", "раздел помощь"
    )

    private fun parseMedia(text: String): VoiceCommand? {
        fun has(vararg keys: String) = keys.any { text.contains(it) }
        return when {
            has("видео", "ролик") -> VoiceCommand.Media("video")
            has("pdf", "пдф", "пэдээф") -> VoiceCommand.Media("pdf")
            has("график", "диаграмм", "чертеж", "парабол") -> VoiceCommand.Media("graph")
            has("рукопис", "почерк", "от руки", "записк") -> VoiceCommand.Media("handwriting")
            has("опиши фото", "опиши картинку", "опиши изображение", "опиши снимок", "что на фото", "что на картинке") -> VoiceCommand.Media("photo")
            else -> null
        }
    }

    private fun parseHelp(text: String): VoiceCommand? {
        if (helpVolunteerWords.any { text.contains(it) }) return VoiceCommand.Help(volunteer = true)
        if (helpGeneralWords.any { text.contains(it) } || Regex("(?:^| )(?:помощь|sos|сос)(?: |$)").containsMatchIn(text)) {
            return VoiceCommand.Help(volunteer = false)
        }
        return null
    }

    private val colorFillers = setOf(
        "какого", "какой", "какая", "какое", "какие", "каких", "цвета", "цвет", "цвете", "цветов", "у", "этот", "эта",
        "это", "эти", "этой", "этого", "этих", "этому", "мой", "моя", "мое", "мои", "моей", "моего", "моих", "ии", "глаз", "айс", "айз", "eyes", "эй", "ai",
        "ай", "скажи", "подскажи", "определи", "назови", "пожалуйста", "мне", "у меня", "вот", "здесь", "тут", "то",
        "что", "за", "в", "руках", "руке", "кадре", "камере", "цветом", "ли", "а", "и", "какого-то"
    )

    private fun parseColor(text: String): VoiceCommand? {
        if (text.contains("найди") || text.contains("поищи")) return null
        val isColor = Regex("(?:^| )(?:цвет|цвета|цвете|цветом|цветов)(?: |$)").containsMatchIn(text) ||
            text.contains("что за цвет") || text.contains("определи цвет")
        if (!isColor) return null
        val item = ItemMatch.cleanItem(
            text.split(" ").filter { it.isNotBlank() && it !in colorFillers }.joinToString(" ")
        )
        return VoiceCommand.ColorOf(item)
    }
}
