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
*   **Контракты событий:** Формат JSON и названия параметров событий не изменились.
*   **Схема базы данных:** Структура таблиц идентична версии 7 старой библиотеки.
*   **API методов:** Сигнатуры методов `logEvent`, `setCommonParameters` и др. остались прежними.

## 7. Чек-лист после миграции

- [ ] Обновлена зависимость в `build.gradle`.
- [ ] Выполнена массовая замена импортов `ru.wildberries` -> `ru.wildanalytics.pub`.
- [ ] Проверен файл `AndroidManifest.xml` (если в нем были ссылки на классы SDK).
- [ ] Обновлены ProGuard/R8 правила (если использовались кастомные пути).
- [ ] Выполнена очистка и пересборка проекта (`./gradlew clean assemble`).

## 8. Полезные советы для Android Studio

Для быстрой миграции используйте `Ctrl+Shift+R`:
1. **Текст:** `WBAnalytics2` -> **Заменить на:** `WildAnalytics`
2. **Текст:** `WBA2` -> **Заменить на:** `WildAnalytics`
3. **Текст:** `WB` -> **Заменить на:** `Wild` (будьте осторожны с именами переменных, где `WB` может быть частью слова).
