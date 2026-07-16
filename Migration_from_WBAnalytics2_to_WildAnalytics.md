# Руководство по миграции: с WBAnalytics2 на WildAnalytics (v1.0.36)

Это руководство описывает процесс обновления Android SDK с версии `WBAnalytics2` (пакет `ru.wildberries`) на новую версию `WildAnalytics` (пакет `ru.wildanalytics.pub`).

## 1. Обновление зависимости

Библиотека теперь публикуется по новым координатам. Все новые версии (начиная с v1.0.36) теперь располагаются в группе `ru.wildanalytics`.

```diff
- implementation("ru.wildberries:analytics2:1.0.35")
+ implementation("ru.wildanalytics:pub:1.0.36")
```

Выбор способа получения артефактов остается на усмотрение команды разработки. Артефакты расположены по следующим путям:
*   **Новые версии (начиная с 1.0.36):** [library/releases/ru/wildanalytics/pub](./library/releases/ru/wildanalytics/pub)
*   **Старые версии (до 1.0.35 включительно):** [library/releases/ru/wildberries/analytics2.public](./library/releases/ru/wildberries/analytics2.public)

## 2. Изменение импортов

Пакет библиотеки был полностью изменен. Выполните глобальную замену (Replace in Path) во всём проекте:

```diff
-import ru.wildberries.analytics.*
+import ru.wildanalytics.pub.analytics.*
```

**Рекомендуемые паттерны для замены:**
*   `ru.wildberries.analytics` -> `ru.wildanalytics.pub.analytics`
*   `ru.wildberries.attribution` -> `ru.wildanalytics.pub.attribution`
*   `ru.wildberries.splitter` -> `ru.wildanalytics.pub.splitter`

## 3. Переименование точки входа

Основной интерфейс библиотеки теперь называется `WildAnalytics`. Названия методов остались прежними.

```diff
-val analytics: WBAnalytics2 = ...
+val analytics: WildAnalytics = ...
```

## 4. Обновление публичных типов

Все типы с префиксом `WB` или `WBA2` были переименованы для соответствия новому брендингу:

| Старое имя | Новое имя |
| :--- | :--- |
| `WBAnalytics2` | `WildAnalytics` |
| `WBA2Config` | `WildAnalyticsConfig` |
| `WBSplitter` | `WildSplitter` |
| `WBAttributionTracker` | `WildAttributionTracker` |
| `WBDeviceInfoProvider` | `WildDeviceInfoProvider` |
| `WBAttributionLogger` | `WildAttributionLogger` |

## 5. Автоматическая миграция данных

Библиотека включает встроенный механизм `OldDatabaseMigrationHelper`, который обеспечивает перенос данных из старой БД в новую при первом запуске.

*   **Если приватная либа отсутствует:** Все события и статистика копируются в новую БД, старый файл `ru.wildberries.analytics.db` удаляется.
*   **Если приватная либа присутствует:** Копируется только статистика (`sentInfo`), чтобы обеспечить непрерывность счетчиков. Старый файл остается нетронутым для работы приватной либы.

> [!IMPORTANT]
> Миграция происходит автоматически в фоновом потоке при первой инициализации `WildAnalytics`. Вам не нужно писать дополнительный код.

## 6. Что НЕ изменилось

Несмотря на изменение названий, следующие компоненты остались совместимыми:
*   **API методов:** Сигнатуры методов `logEvent`, `setCommonParameters` и др. остались прежними.
*   **Схема базы данных:** Структура таблиц идентична версии 7 старой библиотеки.

## 7. Миграция с WildAnalytics 1.0.36 на 1.0.37+

> [!IMPORTANT]
> Если вы уже используете `WildAnalytics` 1.0.36 или ранее, обратите внимание на следующие изменения, появившиеся в версиях 1.0.37+.

### 7.1. OkHttp теперь часть публичного API

Библиотека теперь экспортирует `okhttp3` как `api`-зависимость, потому что кастомные HTTP-заголовки передаются через `okhttp3.Headers`.

*   Вам не нужно добавлять `okhttp` вручную, если он вам не нужен напрямую.
*   Если у вас уже есть собственная зависимость `okhttp`, убедитесь, что она совместима с версией, используемой WildAnalytics.

### 7.2. Новые методы публичного API

В интерфейс `WildAnalytics` добавлены методы:

*   `setCustomHeader(key: String, value: String?)` — установить HTTP-заголовок для всех запросов аналитики (например, антибот-токен `X-Wbaas-Token`).
*   `setCustomHeaders(headers: okhttp3.Headers)` — установить несколько заголовков разом.
*   `addEventEnricher(enricher: EventEnricher)` — зарегистрировать обработчик, который может добавлять поля к каждому событию.

Методы `logEvent`, `logImportantEvent`, `setCommonParameter(s)`, `finish` сохранили прежние сигнатуры.

### 7.3. Изменения в JSON-контракте событий

В отправляемых событиях и мета-информации появились новые поля. Они заполняются автоматически и не требуют изменений в коде интеграции, но могут быть важны для обработки на бэкенде:

*   В событии — `session_value`.
*   В мете — `timezone` и `device_ad_id_type` (`gaid` или `oaid`).

### 7.4. База данных

База данных библиотеки обновлена до версии **7**. Для существующих установок автоматическая миграция выполняется средствами Room без участия приложения.

### 7.5. Поддержка OAID

Помимо GAID, библиотека теперь может использовать рекламный идентификатор Huawei (OAID). Если на устройстве недоступен GAID, будет предпринята попытка получить OAID. Тип использованного идентификатора передаётся в поле `device_ad_id_type`.

## 8. Чек-лист после миграции

- [ ] Обновлена зависимость в `build.gradle`.
- [ ] Выполнена массовая замена импортов `ru.wildberries` -> `ru.wildanalytics.pub`.
- [ ] Проверен файл `AndroidManifest.xml` (если в нем были ссылки на классы SDK).
- [ ] Обновлены ProGuard/R8 правила (если использовались кастомные пути).
- [ ] Выполнена очистка и пересборка проекта (`./gradlew clean assemble`).

## 9. Полезные советы для Android Studio

Для быстрой миграции используйте `Ctrl+Shift+R`:
1. **Текст:** `WBAnalytics2` -> **Заменить на:** `WildAnalytics`
2. **Текст:** `WBA2` -> **Заменить на:** `WildAnalytics`
3. **Текст:** `WB` -> **Заменить на:** `Wild` (будьте осторожны с именами переменных, где `WB` может быть частью слова).
