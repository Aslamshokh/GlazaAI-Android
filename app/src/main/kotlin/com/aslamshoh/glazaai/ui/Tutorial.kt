package com.aslamshoh.glazaai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aslamshoh.glazaai.speech.SpeechSynthesizer
import com.aslamshoh.glazaai.store.SettingsStore

/**
 * Обучающий режим (требование со встречи с незрячими пользователями, ноябрь 2026): короткие
 * подсказки «эта функция делает то-то, пользоваться ей так-то». Звучат сами при первом открытии
 * экрана; повторить — кнопка «?» в шапке экрана. Выключаются и сбрасываются в Настройках.
 */
object Hints {
    const val HOME = "Главный экран. Камера сама называет предметы рядом. Внизу режимы: текст, товар, валюта, в руке, поиск и рядом. " +
        "Большая кнопка в центре — что вокруг. Справа кнопка голосовой команды, рядом — ввод команды текстом."
    const val TEXT = "Режим текст. Наведите камеру на страницу, снимок сделается сам. Приложение читает списки по пунктам, скобки и таблицы. " +
        "Таблицу можно слушать по строкам или по столбцам. Кнопки назад и дальше переходят по частям текста."
    const val MEDIA = "Файлы и медиа. Здесь можно описать фото, график, прочитать рукописный текст, PDF или описать видео. " +
        "Сверху выберите, что нужно, затем снимите камерой или выберите файл."
    const val SCAN = "Сканер. Наведите камеру на штрих-код или QR-код — он прочитается сам, без нажатий. " +
        "Для товара приложение назовёт название и состав, если найдёт его в базе."
    const val CURRENCY = "Валюта. Положите купюру на стол или держите в руке, наведите камеру — номинал назовётся сам. " +
        "Если не получилось, переверните купюру другой стороной."
    const val DOCUMENT = "Чек и документы. Снимите чек целиком: приложение назовёт магазин, дату, итог и покупки. " +
        "Для документа — пары «поле — значение»."
    const val INSPECT = "Что в руке. Держите предмет перед камерой: приложение скажет, что это, какого цвета и свежий ли овощ или фрукт."
    const val NEAR = "Режим рядом. Держите телефон на уровне груди, камерой вперёд. Приложение говорит только о том, что близко: " +
        "ступеньки, бордюры, лестницы, людей, столбы, вход, пешеходный переход и светофор. Дальние объекты не называет."
}

fun speakHint(text: String) {
    SpeechSynthesizer.speak(text, SettingsStore.speechRate)
}

/** Подсказка экрана звучит один раз (в очередь, после приветствия экрана). */
fun speakHintOnce(key: String, text: String) {
    if (SettingsStore.takeHint(key)) {
        SpeechSynthesizer.speakQueued(text, SettingsStore.speechRate, false)
    }
}

/** Переключатель с подписью: вся строка — один элемент для TalkBack («Умный режим, выключатель, включено»),
 * а не безымянный «выключатель» отдельно от текста. */
@Composable
fun ToggleRow(label: String, checked: Boolean, description: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Theme.textPrimary, fontSize = 14.sp)
            if (description != null) Text(description, color = Theme.textSecondary, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = Theme.accent))
    }
}

/**
 * Команда текстом — второй способ ввода рядом с голосом: подходит для экранной брайлевской
 * клавиатуры, обычной клавиатуры и диктовки клавиатуры. Брайль не единственный способ ввода.
 */
@Composable
fun TextCommandDialog(onDismiss: () -> Unit, onCommand: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    fun submit() {
        val t = text.trim()
        if (t.isNotEmpty()) {
            onDismiss()
            onCommand(t)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Theme.surface,
        title = { Text("Команда текстом", color = Theme.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Напишите, что сделать: например «прочитай текст», «найди ключи», «рядом», «опиши видео».",
                    color = Theme.textSecondary,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Команда") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Theme.textPrimary,
                        unfocusedTextColor = Theme.textPrimary,
                        focusedContainerColor = Theme.surfaceAlt,
                        unfocusedContainerColor = Theme.surfaceAlt,
                        focusedIndicatorColor = Theme.accent,
                        cursorColor = Theme.accent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = { submit() }) { Text("Выполнить", color = Theme.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Theme.textSecondary) } }
    )
}
