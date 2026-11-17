# Архитектура PLNA Smart Check

## Слои

```
┌──────────────────────────────────────────┐
│ UI (Compose)                             │
│ Home / Scan / Stats / Profile            │
│ ViewModels + AppViewModelFactory         │
└──────────────────┬───────────────────────┘
                   │
┌──────────────────▼───────────────────────┐
│ Domain-ish repositories                  │
│ AccountRepository, LocalExpenseRepository│
│ ExpenseRepository (remote)               │
└──────────────────┬───────────────────────┘
                   │
     ┌─────────────┴─────────────┐
     ▼                           ▼
┌─────────────┐           ┌──────────────┐
│ Room        │           │ Firebase     │
│ expenses    │  sync ◄── │ Auth         │
│ user_session│           │ Firestore    │
│ pending_del │           └──────────────┘
└─────────────┘
```

## Поток «добавить чек»

1. Пользователь: QR / камера / галерея.
2. `RussianOcrEngine` — текст (модели из `filesDir`, без повторной загрузки).
3. `ReceiptParser` — магазин, УНП, товары, итоги.
4. Экран проверки / правки (`ReceiptTicket`).
5. `LocalExpenseRepository.saveReceipt` → **сразу Room**.
6. `ExpenseSyncManager` в фоне → Firestore + moderation_queue.
7. Товарам ставится `healthTag` (полезно / вредно / нейтрально).

## Поток «удалить чек»

1. Удаление из Room мгновенно.
2. Если есть `firestoreId` и есть сеть → `ExpenseRepository.deleteExpense`.
3. Если офлайн → запись в `pending_cloud_deletes`, уйдёт при следующем синке.

## Аккаунт

- Локально: таблица `user_session` (16-digit ID, имя, язык).
- Облако: `accounts/{accountId}` + Firebase Auth UID.
- Вход на другом телефоне: Auth по служебному email → профиль → `pullExpensesFromCloud`.

## OCR и Room — не путать

| | Room | Tesseract models |
|--|------|------------------|
| Что | Чеки, сессия | `rus`/`eng` traineddata |
| Где | SQLite БД приложения | `filesDir/tesseract/tessdata` |
| Когда качается | — | Один раз при первом OCR |
| Зачем | CRUD + офлайн | Распознавание кириллицы |

## Ключевые пакеты

| Пакет | Назначение |
|-------|------------|
| `ui/home` | Чек-лист + детали CRUD |
| `ui/scan` | Сканер, OCR, лента чека |
| `ui/statistics` | Неделя/месяц, топ, польза |
| `ui/profile` | Auth, язык, тема, About |
| `data/parser` | Hippo / РБ формат чека |
| `data/health` | Классификатор товаров |
| `data/sync` | Минутный sync + pending deletes |

## DI

Простой ручной контейнер: `PlnaApplication.container` → `AppContainer`.
Без Hilt/Koin — меньше boilerplate для личного проекта.
