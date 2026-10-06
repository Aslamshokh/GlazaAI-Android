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

        if (has("найди", "найти", "найдите", "поищи", "ищи", "где ")) {
            val query = text.split(" ").filter { it.isNotBlank() && it !in fillers }.joinToString(" ")
            return VoiceCommand.Find(query)
        }
        if (has("навигац", "маршрут", "как добраться", "как дойти", "как пройти", "веди", "отведи", "проводи", "дойти до")) {
            return VoiceCommand.Navigate(destinationFrom(text))
        }
        if (has("qr", "кью", "кю ар", "куар")) return VoiceCommand.Qr
        if (has("купюр", "банкнот", "деньги", "денег", "рубл", "сколько стоит купюра")) return VoiceCommand.Currency
        if (has("штрих", "товар", "продукт", "сканер")) return VoiceCommand.Product
        if (has("прочитай", "прочти", "читай", "текст", "надпись", "что написано")) return VoiceCommand.ReadText
        if (has("истори")) return VoiceCommand.History
        if (has("вокруг", "передо мной", "предмет", "объект", "что тут", "что здесь")) return VoiceCommand.WhatsAround
        return VoiceCommand.Unknown
    }

    private fun destinationFrom(text: String): String? {
        val m = Regex("(?:^| )(?:до|в|во|на|к|ко) (.+)$").find(text) ?: return null
        val dest = m.groupValues[1].trim()
        return dest.takeIf { it.isNotEmpty() }
    }
}
