# Wild Analytics Android Client
[![en](https://img.shields.io/badge/lang-en-red.svg)](https://github.com/wildberries-tech/wild_analytics_sdk_android/blob/develop/README-en.md)

Клиентская андроид библиотека для Wildberries аналитики.

## Подключение библиотеки

Пока [отсюда](library/releases/ru/wildberries/analytics2.public) доставать aar.

> [!IMPORTANT]
> Если вы переходите со старой версии `WBAnalytics2`, обязательно ознакомьтесь с [Руководством по миграции](./Migration_from_WBAnalytics2_to_WildAnalytics.md).

## Использование библиотеки

### Получение экземпляра

Обычно экземпляр `WildAnalytics` предоставляется через DI (например, Hilt/Dagger):

```kotlin
@Inject
lateinit var analytics: WildAnalytics
```

Или можно создать вручную:

```kotlin
val analytics = WildAnalytics(
    apiUrlProvider = { "https://analytics.wb.ru/m/batch" }, // URL для отправки событий
    apiKey = "ВАШ_API_KEY",                         // Ваш API-ключ
    isCollectionEnabled = true                       // Включить/отключить сбор событий
)
```

В реальных проектах часто используется DI и провайдеры, где параметры могут подставляться
динамически, например:

```kotlin
return WildAnalytics(
    apiUrlProvider = { infraLocalizationUrlOverride.overrideIfNeeded(DEFAULT_PROD_URL) },
    apiKey = "ВАШ_API_KEY",
    isCollectionEnabled = true,
)
```

**Пояснения к параметрам конструктора:**

- `apiUrlProvider` — функция, возвращающая URL для отправки аналитики (обычно
  `"https://analytics.wb.ru/m/batch"`).
- `apiKey` — API-ключ для авторизации в сервисе аналитики.
- `isCollectionEnabled` — флаг, отвечающий за то, включена ли обработка событий по умолчанию (можно
  менять в рантайме).

## Конфигурация аналитики (WildAnalyticsConfig)

В текущей версии используется стандартная конфигурация `WildAnalyticsConfig.Default`, параметры которой
подобраны для оптимальной работы библиотеки. В будущем планируется добавить возможность
конфигурирования этих параметров извне.

### Значения по умолчанию (WildAnalyticsConfig.Default):

- **delays: SendingDelays**
    - `delayBetweenBatches = 2 секунды` — задержка между отправкой отдельных батчей (пакетов)
      событий.
    - `delayBetweenOperations = 10 секунд` — задержка после одной полной операции отправки всех
      событий из БД.
    - `initialDelay = 2 секунды` — первоначальная задержка перед началом отправки.

- **batching: BatchingConfig**
    - `maxEventsInBatch = 200` — максимальное количество событий в одном батче.
    - `maxBatchSizeInBites = 1_048_576` (1 МБ) — максимальный размер батча в байтах (если событие
      больше — оно отправится отдельно).

- **maxEventsInCache = 10_000** — максимальное количество событий, которые могут храниться в
  локальном кеше (БД).

- **retryPolicy: RetryPolicy.Exponential**
    - `attempts = 10` — количество попыток отправки.
    - `base = 1.5` — база экспоненты для расчёта задержки между попытками.
    - `additional = 2.5` — дополнительная задержка.
    - Минимальная задержка: 4 сек, максимальная: ~60 сек, общая длительность всех попыток: ~115 сек.

- **transport: HttpTransport** — отправка событий по HTTP через OkHttp.

> ⚠️ В будущем планируется добавить возможность конфигурирования этих параметров через внешний
> интерфейс или DI.

### Логирование событий

#### Простое событие

```kotlin
analytics.logEvent("screen_open", mapOf("screen" to "Main"))
```

#### Сложное событие (например, покупка)

> **Примечание:**
> Для сериализации сложных параметров и построения JSON-структур
> используется [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization). Эта
> библиотека уже входит в зависимости WildAnalytics, но если вы строите свои структуры, убедитесь, что
> она также доступна в вашем проекте.

```kotlin
analytics.logEvent(
    name = "purchase",
    parameters = buildJsonObject(
        buildJsonObject {
            put("currency", "RUB")
            put("transactionId", 123456)
            putJsonArray("items") {
                add(buildJsonObject {
                    put("id", "1234")
                    put("name", "Носки")
                    put("category", "Одежда")
                    put("brand", "Wildberries")
                    put("variant", "Черный")
                    put("price", 1234)
                    put("quantity", 3)
                })
            }
        }
    )
)
```

#### Установка общих параметров

```kotlin
analytics.setCommonParameter("client_id", "123")
analytics.setCommonParameters(mapOf("user_id" to "456", "app_version" to "1.2.3"))
```

#### Отключение/включение сбора событий

```kotlin
analytics.isCollectionEnabled = false // или true
```

#### Завершение работы аналитики навсегда

```kotlin
analytics.finish()
```

### Описание параметров

- **name** — имя события (от 1 до 40 символов).
- **parameters** — карта параметров (ключ — строка, значение — строка или сериализуемый объект).
- Для сложных событий используйте сериализацию в `JsonObject`.
- Поддерживаются вложенные структуры (см. пример покупки).

### Примеры событий из реального кода

- Открытие экрана:  
  `analytics.logEvent("Lottery_V", mapOf("name" to "Лотерея", "circulation" to "123"))`
- Покупка:  
  (см. пример выше)
- Клик по баннеру:  
  `analytics.logEvent("Banner_T", mapOf("banner_id" to "789", "location" to "main"))`

## Статический анализ (detekt)

В проекте настроен [detekt](https://detekt.dev/) — статический анализатор для Kotlin-кода.
Проверка будет запускаться на CI и должна проходить без ошибок.

### Запуск detekt локально

```bash
./gradlew :library:detekt
```

Отчёт будет выведен в консоль. HTML-отчёт доступен по пути:

```
library/build/reports/detekt/detekt.html
```

### Baseline

Для существующих проблем используется файл `library/detekt-baseline.xml`.
Это позволяет не блокировать CI из-за ошибок на момент добавления, но при этом проверять весь новый код.

**После исправления ошибок из baseline** необходимо перегенерировать его, чтобы исправленные проблемы
больше не игнорировались:

```bash
./gradlew :library:detektBaseline
```

После выполнения команды нужно закоммитить обновлённый `library/detekt-baseline.xml`.

### Конфигурация

Правила detekt описаны в файле `config/detekt/detekt.yml`. При необходимости можно изменить
настройки правил или отключить/включить конкретные проверки. За основу взяты правила из androidnative проекта, без лишних зависимостей

## FAQ

- **Можно ли вызывать методы c разных потоков?**  
  Да, все публичные методы WildAnalytics реализованы потокобезопасно.
- **Как указать apiKey?**  
  Через параметр конструктора или DI.
- **Как добавить пользовательский токен?**  
  Используйте `setCommonParameter("user_token", "TOKEN")`.
- **Что если нет сети?**  
  События сохраняются и будут отправлены при появлении сети.
- **Как завершить сбор событий?**  
  Вызовите `finish()`. После этого возоновить процесс невозможно.

# Wild Attribution Tracker

Входит в состав библиотеки, начиная с 1.0.12

## Подключение библиотеки

Аналогично [подключению wild analytics client](#подключение-библиотеки)

## Использование библиотеки

### Создание экземпляра

```kotlin
WildAttributionTracker.Factory.create(
    context = context, // контекст приложения
    withSystemLogs = BuildConfig.DEBUG, // отправлять ли системные логи
    systemLogsTag = "WildAttribution", // тэг, по которому отправляются логи
)
```

### Проверка атрибуции

```kotlin
attributionTracker.checkAttribution(
    analytics = analytics, // экземпляр аналитики для логирования события "app_install"
    onResult = { data: AttributionData? -> // вызывается один раз с данными атрибуции или без них, если не было перехода в приложение с рекламной ссылки определённого типа
        val link = data?.link
        if (link != null) {
            TODO("handle deeplink here")
        }
    }
)
```
