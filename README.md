# Мойдодыр — Android source (unverified build)

Daily at 10:00, 15:00, 20:00 local time. Only the newest scheduled task remains active; no backlog. After successful reading or parent PIN, the current task is dismissed until the next scheduled time. A full-screen Android overlay is attempted when the alarm fires; Android/Samsung can delay or interrupt it, and users can bypass overlays via system UI. **Not a tamper-proof parental control.**

## Build APK

Upload the project contents to a GitHub repository. In Actions, run **Build Android APK**. The workflow downloads the small Russian Vosk model during *build* and bundles it inside the APK. The installed app does not need Internet for speech recognition. If model download or dependency resolution fails, the build fails. This workflow has not been tested here.

## REQUIRED poem text

The complete text of Chukovsky's *Moydodyr* is **not included**. Add a legally sourced text to `app/src/main/assets/verses.txt`, with **exactly four lines per stanza**, separated by a blank line. Each scheduled time advances to the next stanza; after the final stanza it loops. Without this, the app cannot verify reading. The poem may have copyright restrictions depending on jurisdiction.

## First-run steps

1. Install APK, open it, and set a parent PIN (at least 6 digits). PIN is not included in the source; the app currently uses a basic SHA-256 hash and should be hardened for a production release.
2. Grant microphone, overlay, notification and exact alarm permissions as offered by Android. On Samsung set Battery to Unrestricted and allow background activity as applicable.
3. Test each scheduled slot with the screen both on and off. The overlay is not a secure replacement for Android's lock screen.

## Known limitations

- The Android overlay service may be killed by Samsung battery management or restricted by Android background rules; not guaranteed immediate or unbypassable.
- Current Vosk end-of-speech behavior requires real-device testing, and the recognition flow may need tuning.
- The project has not been compiled or device-tested in this environment; do not deploy as a sole child-safety control.
- The default slot sequence is anchored to 2026-11-02. The first slot is 10:00.

## Импорт текста (обновление)
В приложении предусмотрена кнопка «Загрузить TXT со стихотворением». Выберите UTF-8 TXT через системный выбор файла. Пустые строки игнорируются, каждые четыре непустые строки образуют одно задание. Незавершённая последняя группа пропускается. Текст хранится во внутренней памяти приложения и не требует сети.

## Важно перед использованием
Исходный проект НЕ собран и НЕ проверен на реальном устройстве. Android ограничивает запуск из фона и наложения, поэтому строгая неотключаемая блокировка не гарантируется. PIN задаётся при первом запуске. При отсутствии импортированного текста чтение не работает. Для офлайн-распознавания требуется загрузка Vosk-модели на этапе сборки GitHub Actions. Кнопка «Я закончил читать» завершает запись и запускает проверку.
