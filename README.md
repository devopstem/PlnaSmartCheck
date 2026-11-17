# PLNA Smart Check

Личное Android-приложение для хранения чеков: фото / QR / галерея → распознавание русского текста → подтверждение → сохранение на телефоне и в облаке.

**Стек:** Kotlin, Jetpack Compose (Material 3), Room, Firebase Auth + Firestore, CameraX, Tesseract (rus+eng), DataStore.

---

## Что умеет приложение

| Функция | Описание |
|--------|----------|
| Чек-лист | Список покупок, открытие чека, правка, удаление |
| Сканер | QR, фото текста, выбор из галереи |
| OCR | Русский + английский (Tesseract), модели скачиваются **один раз** |
| Профиль | Регистрация / вход по **16-значному ID** (без email в UI) |
| Языки UI | Русский, беларуская, English |
| Тема | Системная / светлая / тёмная |
| Статистика | Неделя / месяц, сумма, топ товаров |
| Польза товаров | Метки «полезно / вредно / нейтрально» (по названию + вручную) |
| Синхронизация | Сначала Room на телефоне → потом Firestore в фоне |

---

## Room vs OCR-модели — в чём смысл

Это **разные хранилища** под разные задачи:

```
┌─────────────────────┐     ┌──────────────────────────────┐
│ Room (SQLite)       │     │ filesDir/tesseract/tessdata  │
│ • чеки, товары      │     │ • rus.traineddata            │
│ • сессия (16-digit) │     │ • eng.traineddata            │
│ • очередь удалений  │     │ скачивается 1 раз, остаётся  │
└─────────────────────┘     └──────────────────────────────┘
         │                                │
         ▼                                ▼
   Firebase Firestore              Tesseract OCR
   (облако чеков)                  (распознавание текста)
```

- **Room** — ваши покупки и аккаунт. Работает офлайн.
- **OCR-модели** — большие языковые файлы. Не кладут в SQLite: они уже на диске приложения и **не качаются заново**, пока вы не удалите приложение или «данные» в настройках Android.

---

## Быстрый старт (разработка)

### Требования

- Android Studio (AGP 9.x / JDK 11+)
- Firebase-проект + файл `app/google-services.json`
- Интернет **только** для: первого скачивания OCR, регистрации/входа, синка

### Сборка

```bash
./gradlew :app:assembleDebug
```

Или Run ▶ в Android Studio.

### Firebase (обязательно перед тестом облака)

См. подробности: [docs/FIREBASE_SETUP.md](docs/FIREBASE_SETUP.md)

Кратко:

1. **Authentication → Email/Password → Enable**
2. Создать **Firestore**
3. Вставить правила из `firestore.rules` → Publish
4. Положить `google-services.json` в `app/`

---

## Архитектура (коротко)

```
ui/          Compose экраны + ViewModel
data/
  local/     Room (чеки, сессия, pending deletes)
  repository/ Account + Expense + LocalExpense
  scan/      Tesseract OCR, Camera analyzers
  parser/    Парсер чека Гиппо / РБ
  health/    Классификатор полезно/вредно
  sync/      Фоновая синхронизация раз в минуту
di/          AppContainer
```

Точка входа: `PlnaApplication` → `AppContainer` → `MainActivity`.

Подробнее: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## Аккаунт без email

Пользователь видит только:

- имя (или случайное `User_XXXX`);
- **16-значный ID**.

Под капотом Firebase Auth использует служебный email `{id}@plna-smartcheck.app` — в интерфейсе его нет. ID можно скопировать в Профиле (нажатие на номер).

---

## Структура данных Firestore

```
accounts/{accountId}          # профиль (16-digit)
users/{firebaseUid}/expenses/{id}  # чеки
moderation_queue/{id}         # новые штрихкоды
```

Локально (Room): `expenses`, `user_session`, `pending_cloud_deletes`.

---

## Лицензия / публикация

Перед клонированием / сборкой:

1. Скопируйте `app/google-services.json.example` → `app/google-services.json`
2. Или скачайте свой файл из Firebase Console (package = `com.plna.smartcheck`)
3. Опубликуйте `firestore.rules`
4. Включите Authentication → Email/Password

Репозиторий: https://github.com/devopstem/PlnaSmartCheck

---

## Версия и автор

- **Version:** `1.0.0` (`versionCode = 1`)
- **Created by DevOpsTem**

Release-сборка включает R8 (обфускация + shrink), запрет cleartext HTTP, `allowBackup=false`.

```bash
./gradlew :app:assembleRelease
```

> Полностью защитить APK от декомпиляции нельзя — R8 усложняет разбор кода, но не делает его невозможным.

## Автор

PLNA Smart Check — личный проект для учёта покупок по чекам (Беларусь / Гиппо и аналоги).  
**Created by DevOpsTem**
