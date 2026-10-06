"""Дизайн «ИИ Глаз» (10 экранов) — донастройка проекта после распаковки архива.

Запускать в папке проекта (там, где лежит папка app):   python apply_design.py

Что делает (безопасно, повторный запуск ничего не ломает; если чего-то не нашёл —
ничего не меняет и объясняет причину):
  1. app/build.gradle.kts: добавляет зависимость ML Kit Translate (перевод RU⇄EN офлайн).
  2. AndroidManifest.xml: добавляет разрешения микрофона и геолокации, если их ещё нет.
  3. Удаляет старые файлы экранов, которые заменены новыми (CaptureScreen.kt, LiveScreen.kt).
"""
import re
import sys
from pathlib import Path

ROOT = Path("app/src/main")
KT = ROOT / "kotlin/com/aslamshoh/glazaai/ui"
GRADLE = Path("app/build.gradle.kts")
MANIFEST = ROOT / "AndroidManifest.xml"
TRANSLATE_DEP = 'implementation("com.google.mlkit:translate:17.0.3")'

errors = []
changes = {}

# ── build.gradle.kts ──
if not GRADLE.exists():
    errors.append(f"Не найден {GRADLE}. Запустите скрипт из папки проекта (где лежит папка app).")
else:
    g = GRADLE.read_text(encoding="utf-8")
    if "mlkit:translate" not in g:
        anchor = re.search(r'^(\s*)implementation\("com\.google\.code\.gson:gson:[^"]+"\)\s*$', g, flags=re.M)
        if not anchor:
            errors.append("build.gradle.kts: не нашёл строку с gson, не знаю, куда вставить зависимость")
        else:
            ins = (
                f'{anchor.group(0)}\n\n{anchor.group(1)}// Перевод Текст ⇄ Перевод (RU⇄EN): модели скачиваются один раз, дальше работают офлайн.\n'
                f'{anchor.group(1)}{TRANSLATE_DEP}'
            )
            changes[GRADLE] = g[: anchor.start()] + ins + g[anchor.end():]

# ── AndroidManifest.xml ──
if not MANIFEST.exists():
    errors.append(f"Не найден {MANIFEST}")
else:
    m = MANIFEST.read_text(encoding="utf-8")
    new = m
    needed = [
        ('<uses-permission android:name="android.permission.RECORD_AUDIO" />', "android.permission.RECORD_AUDIO"),
        ('<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />', "android.permission.ACCESS_FINE_LOCATION"),
        ('<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />', "android.permission.ACCESS_COARSE_LOCATION"),
        ('<uses-feature android:name="android.hardware.location.gps" android:required="false" />', "android.hardware.location.gps"),
        ('<uses-feature android:name="android.hardware.microphone" android:required="false" />', "android.hardware.microphone"),
    ]
    missing = [line for line, key in needed if key not in m]
    if missing:
        anchor = re.search(r'<uses-permission android:name="android\.permission\.INTERNET" />[ \t]*\n', m)
        if not anchor:
            errors.append("AndroidManifest.xml: не нашёл uses-permission INTERNET")
        else:
            block = "".join(f"    {line}\n" for line in missing)
            new = new[: anchor.end()] + block + new[anchor.end():]
    if "android.speech.RecognitionService" not in new:
        queries = (
            "    <queries>\n        <intent>\n"
            '            <action android:name="android.speech.RecognitionService" />\n'
            "        </intent>\n    </queries>\n\n"
        )
        app = re.search(r"^[ \t]*<application", new, flags=re.M)
        if not app:
            errors.append("AndroidManifest.xml: не нашёл тег <application>")
        else:
            new = new[: app.start()] + queries + new[app.start():]
    if new != m:
        changes[MANIFEST] = new

if errors:
    print("Ничего не изменено. Проблемы:")
    for e in errors:
        print("  -", e)
    sys.exit(1)

for path, text in changes.items():
    path.write_text(text, encoding="utf-8")
    print("Обновлён:", path)

for old in ("CaptureScreen.kt", "LiveScreen.kt"):
    p = KT / old
    if p.exists():
        p.unlink()
        print("Удалён старый файл:", p)

if not changes:
    print("Gradle и манифест уже в порядке.")
print("Готово. Теперь: git add . ; git commit ; git push")
