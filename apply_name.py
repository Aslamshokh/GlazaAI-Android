# Переименование в EYES AI везде: название под иконкой, заголовки внутри приложения и голосовые команды
# («EYES AI, найди ключи»; старое «ИИ Глаз» тоже продолжает работать). Запуск из корня репозитория: python apply_name.py
import pathlib

EDITS = [
 [
  "app/src/main/res/values/strings.xml",
  "<string name=\"app_name\">GLAZA AI</string>",
  "<string name=\"app_name\">EYES AI</string>"
 ],
 [
  "app/src/main/kotlin/com/aslamshoh/glazaai/ui/SettingsScreen.kt",
  "версии GLAZA AI (glaza-ai-backend)",
  "версии EYES AI (glaza-ai-backend)"
 ],
 [
  "app/src/main/kotlin/com/aslamshoh/glazaai/ui/ProfileScreens.kt",
  "Text(\"ИИ Глаз\", color",
  "Text(\"EYES AI\", color"
 ],
 [
  "app/src/main/kotlin/com/aslamshoh/glazaai/ui/ProfileScreens.kt",
  "\"ИИ Глаз · версия 1.0.0\"",
  "\"EYES AI · версия 1.0.0\""
 ],
 [
  "app/src/main/kotlin/com/aslamshoh/glazaai/ui/ProfileScreens.kt",
  "Text(\"ИИ Глаз PRO\"",
  "Text(\"EYES AI PRO\""
 ],
 [
  "app/src/main/kotlin/com/aslamshoh/glazaai/ui/HomeScreen.kt",
  "title = \"ИИ Глаз\",",
  "title = \"EYES AI\","
 ],
 [
  "app/src/main/kotlin/com/aslamshoh/glazaai/ui/HomeScreen.kt",
  "«ИИ Глаз, найди ключи»",
  "«EYES AI, найди ключи»"
 ],
 [
  "app/src/main/kotlin/com/aslamshoh/glazaai/util/VoiceCommands.kt",
  "(«ИИ Глаз, найди ключи»)",
  "(«EYES AI, найди ключи»)"
 ],
 [
  "app/src/main/kotlin/com/aslamshoh/glazaai/util/VoiceCommands.kt",
  "\"ии\", \"глаз\", \"ай\", \"пожалуйста\", \"мне\", \"мои\", \"мой\", \"моя\", \"моё\", \"мою\", \"пожалуйста\",",
  "\"ии\", \"глаз\", \"ай\", \"айс\", \"айз\", \"eyes\", \"эй\", \"ai\", \"пожалуйста\", \"мне\", \"мои\", \"мой\", \"моя\", \"моё\", \"мою\", \"пожалуйста\","
 ],
 [
  "app/src/main/kotlin/com/aslamshoh/glazaai/util/VoiceCommands.kt",
  "\"моих\", \"ии\", \"глаз\",",
  "\"моих\", \"ии\", \"глаз\", \"айс\", \"айз\", \"eyes\", \"эй\", \"ai\","
 ],
 [
  "app/src/main/kotlin/com/aslamshoh/glazaai/util/VoiceCommands.kt",
  "(?:(?:ии|глаз|ай) )*(?:запомни",
  "(?:(?:ии|глаз|ай|айс|айз|eyes|эй|ai) )*(?:запомни"
 ],
 [
  "app/src/main/kotlin/com/aslamshoh/glazaai/util/VoiceCommands.kt",
  "(?:(?:ии|глаз|ай) )*я (?:",
  "(?:(?:ии|глаз|ай|айс|айз|eyes|эй|ai) )*я (?:"
 ],
 [
  "app/src/main/kotlin/com/aslamshoh/glazaai/util/VoiceCommands.kt",
  "(?:(?:ии|глаз|ай) )*(?:забудь",
  "(?:(?:ии|глаз|ай|айс|айз|eyes|эй|ai) )*(?:забудь"
 ],
 [
  "app/src/main/kotlin/com/aslamshoh/glazaai/util/VoiceCommands.kt",
  "text.startsWith(\"ии\") || text.startsWith(\"глаз\")",
  "text.startsWith(\"ии\") || text.startsWith(\"глаз\") || text.startsWith(\"eyes\") || text.startsWith(\"айс\") || text.startsWith(\"айз\")"
 ],
 [
  "app/src/main/kotlin/com/aslamshoh/glazaai/util/MemoryText.kt",
  "\"мне\", \"ии\", \"глаз\", \"ай\",",
  "\"мне\", \"ии\", \"глаз\", \"ай\", \"айс\", \"айз\", \"eyes\", \"эй\", \"ai\","
 ]
]

done = skip = 0
for rel, old, new in EDITS:
    p = pathlib.Path(rel)
    if not p.exists():
        print("нет файла:", rel); continue
    t = p.read_text(encoding="utf-8")
    if new in t:
        skip += 1; continue
    if t.count(old) != 1:
        print("не найдено место в", rel, "->", old[:50]); continue
    p.write_text(t.replace(old, new), encoding="utf-8"); done += 1
print("Готово: изменено мест — %d, уже было — %d." % (done, skip))
