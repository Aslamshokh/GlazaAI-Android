"""Меняет название приложения на «GLAZA AI» (не трогая остальные строки strings.xml)."""
import re
from pathlib import Path

NEW_NAME = "GLAZA AI"
p = Path("app/src/main/res/values/strings.xml")
s = p.read_text(encoding="utf-8")
new, n = re.subn(r'(<string name="app_name">)[^<]*(</string>)', rf"\g<1>{NEW_NAME}\g<2>", s, count=1)
if n == 0:
    raise SystemExit("Не нашёл app_name в strings.xml — запустите скрипт в папке Android-проекта (где лежит папка app).")
p.write_text(new, encoding="utf-8")
print(f"Готово: название приложения теперь «{NEW_NAME}».")
