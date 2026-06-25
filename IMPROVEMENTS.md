# Технические улучшения Exelgramm

Актуальная оценка кода: **9.5 / 10**.

---

## Внедрено

### Сеть и сериализация

| # | Изменение | Файлы |
|---|-----------|-------|
| 1 | Gson → `kotlinx.serialization` | `AppsScriptDto.kt`, `AppsScriptApi.kt`, `NetworkModule.kt` |
| 2 | Инкрементальный poll через параметр `since` | `AppsScriptApi.kt`, `ChatRepository.kt`, `Code.gs` |
| 3 | Отменяемые HTTP, RFC 4180 CSV, stable CSV IDs | см. предыдущие волны |

### Архитектура и данные

| # | Изменение | Файлы |
|---|-----------|-------|
| 4 | Use cases, `ChatRepository`, fallback-цепочка, тесты | `ChatUseCases.kt`, `ChatRepositoryTest.kt` |
| 5 | `AuthSession` / `ChatConfig`, `SessionProvider` | `SessionModels.kt` |
| 6 | **Encrypted DataStore + Keystore** вместо ESP для auth | `AuthCrypto.kt`, `AuthStore.kt` |
| 7 | Однократная миграция из legacy `auth_store_v1` | `AuthStore.kt` |

### UI и синхронизация

| # | Изменение | Файлы |
|---|-----------|-------|
| 8 | Пагинация истории: `CHAT_PAGE_SIZE`, «Загрузить ранее» | `ChatViewModel.kt`, `ChatFragment.kt` |
| 9 | Индикатор «Отправляется…» + **«Не отправлено»** на bubble | `MessageUiItem.kt`, `MessageAdapter.kt` |
| 10 | **WorkManager** фоновая синхронизация (15 мин) | `ChatSyncWorker.kt`, `ChatSyncScheduler.kt` |
| 11 | **Adaptive polling**: 2 с после отправки, затем 5 с | `ChatViewModel.kt` |
| 12 | Mutex, MessageMerger, screen polling, unit-тесты | см. предыдущие волны |

### Безопасность и сборка

| # | Изменение | Файлы |
|---|-----------|-------|
| 13 | R8 + ProGuard для kotlinx.serialization | `proguard-rules.pro` |
| 14 | Constant-time verify, async AuthStore init | `PasswordUtils.kt` |

---

## Ограничения платформы (не реализуемо без нового бэкенда)

| Задача | Почему | Альтернатива в приложении |
|--------|--------|---------------------------|
| WebSocket | Google Sheets / Apps Script не поддерживает push | WorkManager + adaptive polling |
| FCM | Нет сервера для отправки push | WorkManager каждые 15 мин |

---

## Запланировано (опционально)

| # | Изменение | Зачем |
|---|-----------|-------|
| 1 | Server-sent events / custom backend | Настоящий real-time |
| 2 | Retry-кнопка на bubble с ошибкой | UX без toolbar |
| 3 | Полная пагинация на сервере (`limit` + cursor) | Очень большие таблицы (10k+ строк) |

---

## История оценки

| Дата | Оценка | Комментарий |
|------|--------|-------------|
| Начальный аудит | 7.5 / 10 | Базовая архитектура |
| После волны 2 | 8.5 / 10 | Mutex, screen polling, split session |
| После волны 3 | 9.0 / 10 | Use cases, repository tests |
| После волны 4 | 9.5 / 10 | Serialization, pagination, encrypted DataStore, WorkManager |
