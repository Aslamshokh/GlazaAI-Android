"""Новый сканер QR и штрихкодов (ML Kit) — донастройка проекта после распаковки архива.

Запускать в папке проекта (там, где лежит папка app):   python apply_scanner.py

Что делает (безопасно, повторный запуск ничего не ломает):
  app/build.gradle.kts: добавляет зависимость ML Kit Barcode Scanning (модель встроена в
  приложение, работает без интернета и без Google Play Services).
"""
import re
import sys
from pathlib import Path

GRADLE = Path("app/build.gradle.kts")
DEP = 'implementation("com.google.mlkit:barcode-scanning:17.3.0")'

if not GRADLE.exists():
    print(f"Не найден {GRADLE}. Запустите скрипт из папки проекта (где лежит папка app).")
    sys.exit(1)

text = GRADLE.read_text(encoding="utf-8")
if "mlkit:barcode-scanning" in text:
    print("Зависимость ML Kit Barcode Scanning уже есть — ничего менять не нужно.")
    sys.exit(0)

anchor = re.search(r'^(\s*)implementation\("com\.google\.code\.gson:gson:[^"]+"\)\s*$', text, flags=re.M)
if not anchor:
    print("Не нашёл в build.gradle.kts строку с gson, не знаю, куда вставить зависимость.")
    print(f"Добавьте вручную в блок dependencies строку:  {DEP}")
    sys.exit(1)

indent = anchor.group(1)
insert = (
    f"{anchor.group(0)}\n\n{indent}// Сканер QR и штрихкодов: быстрее и надёжнее ZXing, работает без интернета.\n{indent}{DEP}"
)
GRADLE.write_text(text[: anchor.start()] + insert + text[anchor.end():], encoding="utf-8")
print("Готово: в app/build.gradle.kts добавлен ML Kit Barcode Scanning.")
