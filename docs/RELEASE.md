# Подготовка release / публикация

## Что сделано для hardening

| Мера | Зачем |
|------|--------|
| R8 minify + shrink + optimize | Обфускация имён, удаление мёртвого кода |
| `proguard-rules.pro` | Keep для Firebase, Room, Tesseract JNI |
| `usesCleartextTraffic=false` | Только HTTPS |
| `network_security_config` | Явный запрет cleartext |
| `allowBackup=false` | Сложнее вытащить локальную БД через backup |
| Исключения backup для DB / DataStore / tessdata | На случай частичного backup |
| Убраны test-исходники и test-зависимости | Меньше мусора в репозитории |
| Убран неиспользуемый Firebase RTDB | Меньше SDK-поверхности |

**Важно:** это **затрудняет** анализ APK, но не даёт 100% защиты от декомпиляции. Секреты (API keys с полными правами) в клиент класть нельзя — доступ режут Firebase Rules.

## Версия

В `app/build.gradle.kts`:

- `versionName = "1.0.0"`
- `versionCode = 1`
- `BuildConfig.AUTHOR = "DevOpsTem"`

Перед каждым стором увеличивайте `versionCode` и при необходимости `versionName`.

## Сборка

```bash
./gradlew :app:assembleRelease
```

APK: `app/build/outputs/apk/release/`.

Для Play Store обычно нужен **AAB** и подпись keystore (не коммитьте `.jks` в git):

```bash
./gradlew :app:bundleRelease
```

## Автор в UI

Экран **Профиль → О приложении**: версия + строка **Created by DevOpsTem**.
