# Wild Analytics Android Client
[![en](https://img.shields.io/badge/lang-en-red.svg)](https://github.com/wildberries-tech/wild_analytics_sdk_android/blob/develop/README-en.md)
[![version](https://img.shields.io/badge/version-1.0.48-blue.svg)](./library/releases/ru/wildanalytics/pub/1.0.48)

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
  `"https://wba.wb.ru/m/batch"`).
- `apiKey` — API-ключ для авторизации в сервисе аналитики.
- `enableAutomaticEvents: Boolean` — включите (`true`) для автоматического отслеживания установок, запусков приложения и времени активности. Если ваш продукт — библиотека для интеграции, держите `false`. По умолчанию `false`.
- `isCollectionEnabled` — флаг, отвечающий за то, включена ли обработка событий по умолчанию (можно
  менять в рантайме).
- `requiredCommonParameters: Set<String>` — ключи общих параметров, без которых события не должны уходить
  (см. [Обязательные общие параметры](#обязательные-общие-параметры)). По умолчанию пусто.

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

// типизированные значения попадают в JSON события как число/boolean, а не строка
analytics.setCommonParameter("vpn_status", 1)
analytics.setCommonParameter("is_authorized", true)

// удаление параметра любого типа
analytics.setCommonParameter("vpn_status", null)
```

#### Обязательные общие параметры

Общие параметры подмешиваются в событие в момент `logEvent`. Если приложение выставляет их
асинхронно (например, `client_id` читается из БД), самые ранние события — пуши при закрытом
приложении, автоматические события SDK — могут уйти без них. Чтобы этого не происходило,
перечислите такие ключи при создании экземпляра:

```kotlin
val analytics = WildAnalytics(
    context = context,
    apiKey = "ВАШ_API_KEY",
    enableAutomaticEvents = true,
    requiredCommonParameters = setOf("client_id", "lang", "country"),
)
```

SDK придержит обработку событий, пока каждый из ключей не будет присвоен через
`setCommonParameter` / `setCommonParameters` (значение `null` тоже считается присвоением —
так, например, помечают анонимного пользователя), после чего допишет в накопленные события
отсутствующие общие параметры. Ожидание одноразовое и ограничено
`REQUIRED_COMMON_PARAMETERS_TIMEOUT` (5 секунд): если какой-то ключ так и не был выставлен,
в лог пишется предупреждение, события уходят как есть, а дальше обрабатываются без задержки.

Приоритеты не меняются: явные параметры события и общие параметры, уже известные на момент
`logEvent`, остаются как были — дописываются только недостающие ключи.

#### Кастомные HTTP-заголовки (например, антибот-токен)

```kotlin
import okhttp3.Headers

analytics.setCustomHeader("X-Wbaas-Token", token)
// или несколько сразу:
analytics.setCustomHeaders(
    Headers.Builder()
        .add("X-Wbaas-Token", token)
        .build()
)
```

Для удаления заголовка передайте `null`:

```kotlin
analytics.setCustomHeader("X-Wbaas-Token", null)
```

#### Обогащение событий (EventEnricher)

Если какое-то событие нужно дополнить общими полями динамически (например,Experiment-флаги), зарегистрируйте enricher:

```kotlin
import kotlinx.serialization.json.JsonPrimitive
import ru.wildanalytics.pub.analytics.AnalyticsField
import ru.wildanalytics.pub.analytics.EventEnricher

analytics.addEventEnricher { eventName, _ ->
    listOf(
        AnalyticsField("ab_test_flag", JsonPrimitive("value")),
    )
}
```

Enricher не может перезаписать существующие параметры события — только добавить отсутствующие.

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

### События атрибуции переходов

> [!IMPORTANT]
> События ниже **обязательны для корректной атрибуции** переходов пользователя в приложение.

#### 1. Открытие по ссылке / диплинку (`dynamic_link_app_open`)

Отправляется при открытии приложения по динамической ссылке или перехваченному диплинку.

| Параметр | Тип | Описание |
| --- | --- | --- |
| `link` | String | URL, по которому было открыто приложение |
| `referrerURL` | String | Referrer ссылки, по которой было открыто приложение (опционально) |

Пример отправки:
```kotlin
analytics.logEvent(
    "dynamic_link_app_open",
    mapOf(
        "link" to "https://wb.ru/product/12345",
        "referrerURL" to "https://yandex.ru"
    )
)

// Или через функцию-расширение:
analytics.logDeepLink(uri = intent.data, referrerUrl = referrerUri?.toString())
```

#### 2. Открытие из push-уведомления (`dynamic_push_app_open`)

Отправляется при открытии приложения по клику на push-уведомление.

| Параметр | Тип | Описание |
| --- | --- | --- |
| `link` | String | Путь URL пуша (deeplink / URL) |
| `push` | String | Текст или заголовок push-уведомления |

Пример отправки:
```kotlin
analytics.logEvent(
    "dynamic_push_app_open",
    mapOf(
        "link" to "https://wb.ru/promo/sale",
        "push" to "Скидки до 70% на всё!"
    )
)

// Или через функцию-расширение:
analytics.logPushOpen(link = pushUrl, push = pushText)
```

### Автоматические системные события

> **enableAutomaticEvents** — включает отправку автоматических событий (по умолчанию `false`).  
> Если хотите отправлять автоматические события — укажите `true` у этого флага.  
> Если ваш продукт будет интегрироваться в другой продукт — оставьте положение флага неизменным (`false`), при изменении у интегрируемых продуктов сбор аналитики может работать нестабильно.  
> В объекте `meta` каждого батча передаётся ключ `enable_automatic_events` со значением `true` или `false`.

SDK автоматически собирает и отправляет базовые системные события жизненного цикла приложения для каждого инициализированного инстанса:

#### 1. Первый запуск приложения (`first_open`)
- **Триггер:** Отправляется автоматически один раз при самом первом запуске приложения на устройстве.
- **Параметры:** без параметров (`{}`).

#### 2. Запуск приложения (`application_start`)
- **Триггер:** Отправляется автоматически при инициализации экземпляра аналитики.
- **Параметры:**
  | Параметр | Тип | Описание |
  | --- | --- | --- |
  | `start_location` | String | Локация старта: `"foreground"` (запуск с активным экраном) или `"background"` (фоновый старт, push-сервис и т.д.) |
  | `cpu` | Double / Number | Максимальная тактовая частота процессора в GHz (например, `2.84`) |
  | `ram` | Int / Number | Общий объём оперативной памяти устройства в GB (например, `8`) |
  | `processor_name` | String | Модель процессора / SoC (например, `SM8450`, `gki`) |

#### 3. Пульс активности (`heartbeat`)
- **Триггер:** Отправляется через 30 секунд после перехода приложения на передний план (foreground) и далее каждые 30 секунд непрерывно, пока приложение активно. При уходе приложения в фон таймер сбрасывается и останавливается.
- **Параметры:** без параметров (`{}`).

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
