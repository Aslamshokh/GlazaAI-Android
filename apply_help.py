"""«Помощь волонтёра» — донастройка проекта «ИИ Глаз» после распаковки архива.

Запускать в папке проекта (там, где лежат папки app и volunteer):   python apply_help.py

Что делает (безопасно, повторный запуск ничего не ломает):
  • подключает экран «Помощь» к главному экрану, профилю, навигации и голосовым командам;
  • добавляет приложение волонтёра (модуль volunteer) в сборку GitHub Actions — получится два APK.
Новые файлы приходят в архиве; этот скрипт правит только уже существующие файлы проекта.
"""
import sys
from pathlib import Path

K = "app/src/main/kotlin/com/aslamshoh/glazaai/"

# (файл, что искать, на что заменить, обязательно ли)
EDITS = [
    (K + "GlazaApplication.kt",
     "import com.aslamshoh.glazaai.store.HistoryStore\n",
     "import com.aslamshoh.glazaai.store.HelpStore\nimport com.aslamshoh.glazaai.store.HistoryStore\n", True),
    (K + "GlazaApplication.kt",
     "        MemoryStore.init(this)\n",
     "        MemoryStore.init(this)\n        HelpStore.init(this)\n", True),

    (K + "ui/Chrome.kt",
     '    const val INSPECT = "scan/inspect"\n',
     '    const val INSPECT = "scan/inspect"\n    const val HELP = "help"\n', True),
    (K + "ui/Chrome.kt",
     "    onBack: (() -> Unit)? = null,\n    onSettings: (() -> Unit)? = null\n) {\n    Box(",
     "    onBack: (() -> Unit)? = null,\n    onSettings: (() -> Unit)? = null,\n    onHelp: (() -> Unit)? = null\n) {\n    Box(", True),
    (K + "ui/Chrome.kt",
     "        if (onSettings != null) {\n            IconButton(onClick = onSettings, modifier = Modifier.align(Alignment.CenterEnd)) {",
     "        if (onHelp != null) {\n            IconButton(onClick = onHelp, modifier = Modifier.align(Alignment.CenterStart)) {\n"
     "                Icon(Icons.Outlined.SupportAgent, contentDescription = \"Помощь: позвать волонтёра или близкого\", tint = Color.White)\n"
     "            }\n        }\n"
     "        if (onSettings != null) {\n            IconButton(onClick = onSettings, modifier = Modifier.align(Alignment.CenterEnd)) {", True),
    (K + "ui/Chrome.kt",
     "import androidx.compose.material.icons.outlined.Settings\n",
     "import androidx.compose.material.icons.outlined.Settings\nimport androidx.compose.material.icons.outlined.SupportAgent\n", True),

    (K + "ui/HomeScreen.kt",
     "    onSettings: () -> Unit,\n    onVoiceCommand: (VoiceCommand) -> Unit\n) {",
     "    onSettings: () -> Unit,\n    onHelp: () -> Unit,\n    onVoiceCommand: (VoiceCommand) -> Unit\n) {", True),
    (K + "ui/HomeScreen.kt",
     "            onSettings = onSettings,\n            modifier = Modifier.align(Alignment.TopCenter)",
     "            onSettings = onSettings,\n            onHelp = onHelp,\n            modifier = Modifier.align(Alignment.TopCenter)", True),
    (K + "ui/HomeScreen.kt",
     "прочитай чек или навигация.", "прочитай чек, навигация или позови волонтёра.", False),

    (K + "ui/Navigation.kt",
     "route == Routes.PROFILE || route == Routes.SETTINGS",
     "route.startsWith(Routes.HELP) || route == Routes.PROFILE || route == Routes.SETTINGS", True),
    (K + "ui/Navigation.kt",
     "                    onSettings = { navController.navigate(Routes.SETTINGS) },\n                    onVoiceCommand",
     "                    onSettings = { navController.navigate(Routes.SETTINGS) },\n"
     "                    onHelp = { navController.navigate(\"${Routes.HELP}?auto=0\") { launchSingleTop = true } },\n"
     "                    onVoiceCommand", True),
    (K + "ui/Navigation.kt",
     "            composable(Routes.SETTINGS) {",
     "            composable(\n                route = \"${Routes.HELP}?auto={auto}\",\n"
     "                arguments = listOf(navArgument(\"auto\") { type = NavType.IntType; defaultValue = 0 })\n"
     "            ) { entry ->\n                HelpScreen(\n"
     "                    autoStart = (entry.arguments?.getInt(\"auto\") ?: 0) == 1,\n"
     "                    onBack = { navController.popBackStack() }\n                )\n            }\n"
     "            composable(Routes.SETTINGS) {", True),
    (K + "ui/Navigation.kt",
     "                    onOpenFeedback = { navController.navigate(Routes.FEEDBACK) },",
     "                    onOpenFeedback = { navController.navigate(Routes.FEEDBACK) },\n"
     "                    onOpenHelp = { navController.navigate(\"${Routes.HELP}?auto=0\") { launchSingleTop = true } },", True),
    (K + "ui/Navigation.kt",
     "        VoiceCommand.Feedback -> nav.navigate(Routes.FEEDBACK) { launchSingleTop = true }",
     "        VoiceCommand.Feedback -> nav.navigate(Routes.FEEDBACK) { launchSingleTop = true }\n"
     "        is VoiceCommand.Help -> nav.navigate(\"${Routes.HELP}?auto=${if (command.volunteer) 1 else 0}\") { launchSingleTop = true }", True),

    (K + "ui/ProfileScreens.kt",
     "    onOpenFeedback: () -> Unit,\n    onOpenPro",
     "    onOpenFeedback: () -> Unit,\n    onOpenHelp: () -> Unit,\n    onOpenPro", True),
    (K + "ui/ProfileScreens.kt",
     '            ProfileRow(Icons.Outlined.RateReview, "Оставить отзыв", null, onClick = onOpenFeedback)\n',
     '            ProfileRow(Icons.Outlined.SupportAgent, "Помощь: волонтёр и близкие", null, onClick = onOpenHelp)\n'
     '            HorizontalDivider(color = Theme.divider)\n'
     '            ProfileRow(Icons.Outlined.RateReview, "Оставить отзыв", null, onClick = onOpenFeedback)\n', True),
    (K + "ui/ProfileScreens.kt",
     "import androidx.compose.material.icons.outlined.RateReview\n",
     "import androidx.compose.material.icons.outlined.RateReview\nimport androidx.compose.material.icons.outlined.SupportAgent\n", True),
    (K + "ui/ProfileScreens.kt",
     "«прочитай чек», «оставить отзыв».", "«прочитай чек», «позови волонтёра», «оставить отзыв».", False),

    (K + "util/VoiceCommands.kt",
     "    /** «Оставить отзыв». */\n    object Feedback : VoiceCommand()",
     "    /** «Оставить отзыв». */\n    object Feedback : VoiceCommand()\n\n"
     "    /** «Позови волонтёра» (volunteer = true — сразу начать вызов) / «нужна помощь», «позвони близкому». */\n"
     "    data class Help(val volunteer: Boolean) : VoiceCommand()", True),
    (K + "util/VoiceCommands.kt",
     "        parseMemory(text)?.let { return it }\n",
     "        parseMemory(text)?.let { return it }\n\n"
     "        // Помощь: «найди волонтёра» — это вызов, а не поиск предмета, поэтому раньше «найди».\n"
     "        parseHelp(text)?.let { return it }\n", True),
    (K + "util/VoiceCommands.kt",
     "    private val lightPhrases = listOf(",
     "    private val helpVolunteerWords = listOf(\n"
     "        \"волонтер\", \"волонтир\", \"добровол\", \"позови человека\", \"нужен человек\", \"позвать человека\", \"живой человек\"\n    )\n"
     "    private val helpGeneralWords = listOf(\n"
     "        \"нужна помощь\", \"нужна мне помощь\", \"помогите\", \"помоги мне\", \"помоги\", \"на помощь\", \"экстренн\", \"позвони близк\",\n"
     "        \"позвонить близк\", \"позвони родным\", \"позвони маме\", \"тревога\", \"открой помощь\", \"раздел помощь\"\n    )\n\n"
     "    private fun parseHelp(text: String): VoiceCommand? {\n"
     "        if (helpVolunteerWords.any { text.contains(it) }) return VoiceCommand.Help(volunteer = true)\n"
     "        if (helpGeneralWords.any { text.contains(it) } || Regex(\"(?:^| )(?:помощь|sos|сос)(?: |$)\").containsMatchIn(text)) {\n"
     "            return VoiceCommand.Help(volunteer = false)\n        }\n        return null\n    }\n\n"
     "    private val lightPhrases = listOf(", True),
]

MODELS = '''
// ---------- «Помощь волонтёра» ----------
data class HelpRequestBody(
    val deviceId: String,
    val name: String,
    val language: String,
    val urgent: Boolean,
    val lat: Double?,
    val lon: Double?
)
data class HelpDeviceBody(val deviceId: String)
data class HelpRateBody(val deviceId: String, val rating: Int, val comment: String?)
data class HelpReportBody(val deviceId: String, val reason: String)
data class HelpOk(val ok: Boolean = true)

/** Состояние вызова: waiting → accepted → finished; либо expired / cancelled. */
data class HelpStatus(
    val requestId: Int,
    val status: String,
    val urgent: Boolean = false,
    val waitedSeconds: Int = 0,
    val leftSeconds: Int = 0,
    val volunteerName: String? = null,
    val roomUrl: String? = null,
    val volunteersOnline: Int? = null
)
'''

SERVICE = '''
/** «Позвать волонтёра»: сервер подбирает свободного волонтёра и выдаёт секретную ссылку на видеокомнату. */
object HelpService {
    suspend fun request(body: HelpRequestBody): HelpStatus = ApiClient.post("/help/requests", body)

    suspend fun status(requestId: Int, deviceId: String): HelpStatus =
        ApiClient.get("/help/requests/$requestId?deviceId=${URLEncoder.encode(deviceId, "UTF-8")}")

    suspend fun cancel(requestId: Int, deviceId: String): HelpStatus =
        ApiClient.post("/help/requests/$requestId/cancel", HelpDeviceBody(deviceId))

    suspend fun rate(requestId: Int, deviceId: String, rating: Int, comment: String? = null): HelpOk =
        ApiClient.post("/help/requests/$requestId/rate", HelpRateBody(deviceId, rating, comment))

    suspend fun report(requestId: Int, deviceId: String, reason: String): HelpOk =
        ApiClient.post("/help/requests/$requestId/report", HelpReportBody(deviceId, reason))
}
'''


def read(p):
    return Path(p).read_text(encoding="utf-8")


def write(p, s):
    Path(p).write_text(s, encoding="utf-8")


def main():
    if not Path("app/build.gradle.kts").exists():
        print("Не найден app/build.gradle.kts. Запустите скрипт из папки проекта (где лежит папка app).")
        sys.exit(1)
    for need in (K + "ui/HelpScreen.kt", K + "store/HelpStore.kt", K + "util/HelpText.kt", "volunteer/build.gradle.kts"):
        if not Path(need).exists():
            print(f"Не найден файл {need}. Сначала распакуйте архив в папку проекта (с заменой), потом запустите скрипт.")
            sys.exit(1)

    problems = []
    done = skipped = 0
    for path, old, new, required in EDITS:
        p = Path(path)
        if not p.exists():
            if required:
                problems.append(f"нет файла {path}")
            continue
        text = read(path)
        if new in text:
            skipped += 1
            continue
        if old not in text:
            if required:
                problems.append(f"в {path} не найден нужный фрагмент: {old.strip().splitlines()[0][:70]}")
            continue
        write(path, text.replace(old, new, 1))
        done += 1

    for path, marker, block in ((K + "network/ApiModels.kt", "data class HelpRequestBody", MODELS),
                                (K + "network/GlazaServices.kt", "object HelpService", SERVICE)):
        text = read(path)
        if marker in text:
            skipped += 1
        else:
            write(path, text.rstrip("\n") + "\n" + block)
            done += 1

    # сборка: второй модуль и второй APK
    g = Path("settings.gradle.kts")
    t = read("settings.gradle.kts")
    if ":volunteer" not in t:
        write("settings.gradle.kts", t.rstrip("\n") + '\n// Приложение волонтёра «ИИ Глаз Помощь» (отдельный APK)\ninclude(":volunteer")\n')
        done += 1
    wf = Path(".github/workflows/build-apk.yml")
    if wf.exists():
        t = read(str(wf))
        if "volunteer-debug.apk" not in t:
            t = t.replace("./gradlew assembleDebug", "./gradlew :app:assembleDebug :volunteer:assembleDebug")
            t = t.rstrip("\n") + (
                "\n\n      - name: Upload volunteer APK as build artifact\n        uses: actions/upload-artifact@v4\n"
                "        with:\n          name: GlazaVolunteer-debug-apk\n"
                "          path: volunteer/build/outputs/apk/debug/volunteer-debug.apk\n          retention-days: 30\n")
            write(str(wf), t)
            done += 1
    else:
        problems.append("не найден .github/workflows/build-apk.yml — второй APK не будет собираться")
    gi = Path(".gitignore")
    if gi.exists() and "volunteer/build/" not in read(".gitignore"):
        write(".gitignore", read(".gitignore").rstrip("\n") + "\nvolunteer/build/\n")

    print(f"Готово: изменено мест — {done}, уже было — {skipped}.")
    if problems:
        print("\nНе удалось применить (покажите этот текст разработчику):")
        for x in problems:
            print("  •", x)
        sys.exit(2)


if __name__ == "__main__":
    main()
