# Настройка Firebase для PLNA Smart Check

Без этих шагов регистрация, вход и облачный синк чеков работать не будут.

## 1. Проект и Android-приложение

1. Откройте [Firebase Console](https://console.firebase.google.com/).
2. Создайте проект (или выберите существующий).
3. Добавьте Android-приложение с `applicationId`: `com.plna.smartcheck`.
4. Скачайте `google-services.json` → положите в папку `app/`  
   (файл **не** в git; образец: `app/google-services.json.example`).  
   **Важно:** `package_name` внутри JSON должен быть ровно `com.plna.smartcheck`.

## 2. Authentication

**Build → Authentication → Sign-in method**

| Провайдер | Значение |
|-----------|----------|
| Email/Password | **Enable = true** |
| Email link | false |
| Anonymous / Google / Phone | не нужны |

Приложение само создаёт служебный аккаунт вида `{16digit}@plna-smartcheck.app`. Пользователь этого не видит — только 16-значный ID.

## 3. Cloud Firestore

1. **Build → Firestore Database → Create database**.
2. Выберите регион (например `europe-west`).
3. Режим: Production (правила ниже) или временно Test mode для отладки.

## 4. Security Rules

Скопируйте содержимое файла [`firestore.rules`](../firestore.rules) в консоль **Firestore → Rules** и нажмите **Publish**.

Правила дают:

- чтение/запись своих чеков `users/{uid}/expenses`;
- создание/обновление своего профиля `accounts/{accountId}`;
- добавление в `moderation_queue` для авторизованных.

## 5. Что не включать на старте

- **App Check** — пока не настроен в коде; включение без SDK заблокирует запросы.
- **Realtime Database** — не используется.
- Жёсткий **email enumeration protection** может мешать отладке входа; при странных ошибках Auth проверьте настройки.

## 6. Проверка

1. Установите debug-сборку.
2. Профиль → Регистрация → скопируйте ID.
3. Добавьте чек → убедитесь, что в Firestore появились документы.
4. На втором устройстве: Вход → тот же ID → чеки подтягиваются.
