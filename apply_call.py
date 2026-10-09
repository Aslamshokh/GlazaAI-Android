"""Видеозвонок внутри приложения (LiveKit) — донастройка проекта «ИИ Глаз» после распаковки архива.

Запускать в папке проекта (там, где лежат папки app и volunteer):   python apply_call.py

Что делает (безопасно, повторный запуск ничего не ломает):
  • добавляет в модель HelpStatus поля livekit и room, а также класс LiveKitInfo;
  • подключает библиотеку LiveKit в app/build.gradle.kts и репозиторий JitPack в settings.gradle.kts;
  • добавляет разрешения для звонка в манифест основного приложения;
  • удаляет устаревший файл старого экрана волонтёра (VolunteerUi.kt), который заменён новыми экранами.
Остальные файлы (экран звонка, приложение волонтёра) приходят в архиве.
"""
import sys
from pathlib import Path

K = "app/src/main/kotlin/com/aslamshoh/glazaai/"

LIVEKIT_CLASS = '''
/** Адрес и персональный токен для подключения к комнате LiveKit. publishVideo — включать ли камеру (у пользователя да, у волонтёра нет). */
data class LiveKitInfo(
    val url: String = "",
    val token: String = "",
    val room: String = "",
    val publishVideo: Boolean = false
)
'''

# (файл, что искать, на что заменить, пропустить, если в файле уже есть это)
EDITS = [
    (K + "network/ApiModels.kt",
     "    val roomUrl: String? = null,\n    val volunteersOnline: Int? = null\n)",
     "    val roomUrl: String? = null,\n    val room: String? = null,\n"
     "    /** Данные для видеозвонка внутри приложения; null — LiveKit на сервере не настроен, тогда открывается Jitsi по roomUrl. */\n"
     "    val livekit: LiveKitInfo? = null,\n    val volunteersOnline: Int? = null\n)\n" + LIVEKIT_CLASS,
     "val livekit: LiveKitInfo?"),
    ("app/build.gradle.kts",
     '    implementation("com.google.code.gson:gson:2.11.0")\n',
     '    implementation("com.google.code.gson:gson:2.11.0")\n\n'
     '    // Видеозвонок с волонтёром прямо в приложении (LiveKit). Репозиторий JitPack подключён в settings.gradle.kts.\n'
     '    implementation("io.livekit:livekit-android:2.29.0")\n',
     "io.livekit:livekit-android"),
    ("settings.gradle.kts",
     "        mavenCentral()\n    }\n}\n\nrootProject.name",
     "        mavenCentral()\n        // LiveKit тянет библиотеку переключения звука с JitPack\n"
     '        maven { url = uri("https://jitpack.io") }\n    }\n}\n\nrootProject.name',
     "jitpack.io"),
    ("app/src/main/AndroidManifest.xml",
     '    <uses-permission android:name="android.permission.RECORD_AUDIO" />\n',
     '    <uses-permission android:name="android.permission.RECORD_AUDIO" />\n'
     '    <!-- Видеозвонок с волонтёром внутри приложения -->\n'
     '    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />\n'
     '    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />\n',
     "MODIFY_AUDIO_SETTINGS"),
]


# файлы, которые заменены новыми (старая версия мешает сборке)
OBSOLETE = ["volunteer/src/main/kotlin/com/aslamshoh/glazaai/volunteer/ui/VolunteerUi.kt"]


def main():
    root = Path.cwd()
    if not (root / "app").is_dir():
        print("Ошибка: запустите скрипт в папке проекта (там, где лежит папка app).")
        sys.exit(1)
    changed = already = 0
    problems = []
    for rel, old, new, marker in EDITS:
        p = root / rel
        if not p.exists():
            problems.append(f"нет файла {rel}")
            continue
        s = p.read_text(encoding="utf-8")
        if marker in s:
            already += 1
            continue
        if s.count(old) != 1:
            problems.append(f"{rel}: не нашёл место для правки (нужна ручная правка)")
            continue
        p.write_text(s.replace(old, new, 1), encoding="utf-8")
        changed += 1
    removed = 0
    for rel in OBSOLETE:
        p = root / rel
        if p.exists():
            p.unlink()
            removed += 1
    print(f"Готово: изменено мест — {changed}, уже было — {already}, удалено старых файлов — {removed}.")
    for t in problems:
        print("ВНИМАНИЕ:", t)
    if problems:
        sys.exit(2)


if __name__ == "__main__":
    main()
