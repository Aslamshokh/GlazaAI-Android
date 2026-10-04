"""Подключает экран «Навигация» к существующему проекту (запускать в папке проекта, где лежит
папка app). Безопасно: если хоть одно место не найдено — ничего не меняет и объясняет, что не так;
повторный запуск ничего не ломает."""
import re
import sys
from pathlib import Path

ROOT = Path("app/src/main")
KT = ROOT / "kotlin/com/aslamshoh/glazaai/ui"
errors: list[str] = []
changes: dict[Path, str] = {}


def load(path: Path) -> str | None:
    if not path.exists():
        errors.append(f"Не найден файл {path}")
        return None
    return path.read_text(encoding="utf-8")


# ── Navigation.kt ──
nav_path = KT / "Navigation.kt"
s = load(nav_path)
if s is not None and "ROUTE_NAV" not in s:
    ok = True
    if not re.search(r'private const val ROUTE_LIVE = "live"', s):
        errors.append("Navigation.kt: не нашёл ROUTE_LIVE"); ok = False
    s2 = re.sub(r'(private const val ROUTE_LIVE = "live")', r'\1\nprivate const val ROUTE_NAV = "navigation"', s, count=1)
    s3, n = re.subn(r'(onOpenSettings = \{ navController\.navigate\(ROUTE_SETTINGS\) \})(\s*\))',
                    r'\1,\n                    onOpenNavigation = { navController.navigate(ROUTE_NAV) }\2', s2, count=1)
    if n == 0:
        errors.append("Navigation.kt: не нашёл вызов HomeScreen(... onOpenSettings = ...)"); ok = False
    s4, n = re.subn(r'^(\s*)composable\(ROUTE_CAPTURE\)', r'\1composable(ROUTE_NAV) { NavigationScreen() }\n\1composable(ROUTE_CAPTURE)', s3, count=1, flags=re.M)
    if n == 0:
        errors.append("Navigation.kt: не нашёл composable(ROUTE_CAPTURE)"); ok = False
    s5, n = re.subn(r'^(\s*)(route == ROUTE_LIVE -> .+)$', r'\1\2\n\1route == ROUTE_NAV -> "Навигация"', s4, count=1, flags=re.M)
    if n == 0:
        errors.append("Navigation.kt: не нашёл заголовок для ROUTE_LIVE"); ok = False
    if ok:
        changes[nav_path] = s5

# ── HomeScreen.kt ──
home_path = KT / "HomeScreen.kt"
s = load(home_path)
if s is not None and "NavigationEntryCard" not in s:
    ok = True
    s2, n = re.subn(r'(onOpenSettings: \(\) -> Unit)(\s*)\)', r'\1,\n    onOpenNavigation: () -> Unit = {}\2)', s, count=1)
    if n == 0:
        errors.append("HomeScreen.kt: не нашёл параметры HomeScreen(...)"); ok = False
    s3, n = re.subn(r'(HeroSection\(onStartCamera = \{ onOpenMode\(RecognitionMode\.OBJECTS\) \}\))',
                    r'\1\n\n        NavigationEntryCard(onClick = onOpenNavigation)', s2, count=1)
    if n == 0:
        errors.append("HomeScreen.kt: не нашёл вызов HeroSection(...)"); ok = False
    s4, n = re.subn(r'(\.background\(Theme\.background\)\s*)(\.padding\(16\.dp\),)', r'\1.verticalScroll(rememberScrollState())\n            \2', s3, count=1)
    if n == 0:
        errors.append("HomeScreen.kt: не нашёл основной Column"); ok = False
    imp = "import androidx.compose.foundation.rememberScrollState\nimport androidx.compose.foundation.verticalScroll\n"
    s5, n = re.subn(r'^(import .+\n)', r'\1' + imp, s4, count=1, flags=re.M)
    if n == 0:
        errors.append("HomeScreen.kt: не нашёл import"); ok = False
    if ok:
        changes[home_path] = s5

# ── AndroidManifest.xml ──
man_path = ROOT / "AndroidManifest.xml"
s = load(man_path)
if s is not None and "ACCESS_FINE_LOCATION" not in s:
    perms = (
        '    <uses-permission android:name="android.permission.RECORD_AUDIO" />\n'
        '    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />\n'
        '    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />\n'
        '    <uses-feature android:name="android.hardware.location.gps" android:required="false" />\n'
        '    <uses-feature android:name="android.hardware.microphone" android:required="false" />\n'
    )
    queries = (
        '    <!-- Без этого Android 11+ скрывает службу распознавания речи, и голосовой ввод «недоступен». -->\n'
        '    <queries>\n        <intent>\n            <action android:name="android.speech.RecognitionService" />\n        </intent>\n    </queries>\n\n'
    )
    s2, n = re.subn(r'(<uses-permission android:name="android\.permission\.INTERNET" />\s*\n)', r'\1' + perms, s, count=1)
    if n == 0:
        errors.append("AndroidManifest.xml: не нашёл uses-permission INTERNET")
    else:
        s3, n = re.subn(r'(\s*)(<application)', r'\n\n' + queries.rstrip("\n") + r'\n\n    \2', s2, count=1)
        if n == 0:
            errors.append("AndroidManifest.xml: не нашёл <application")
        else:
            changes[man_path] = s3

if errors:
    print("Ничего не изменено. Проблемы:")
    for e in errors:
        print(" -", e)
    print("Запускайте скрипт в папке проекта (где лежит папка app). Если проблема осталась — пришлите этот текст.")
    sys.exit(1)

if not changes:
    print("Навигация уже подключена — менять нечего.")
else:
    for path, text in changes.items():
        path.write_text(text, encoding="utf-8")
        print("Обновлён:", path)
    print("Готово: экран «Навигация» подключён.")
