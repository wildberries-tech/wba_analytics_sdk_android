# WB Analytics 2 Android Client

Клиентская андроид библиотека для Wildberries аналитики.

## Подключение библиотеки

В корневом `settings.gradle.kts`:

   ```kotlin
   val needAnalytics = providers.gradleProperty("wb.analytics.include").orNull == "true"
   if (needAnalytics) {
       logger.warn("Including :analytics build. wb.analytics.include=true")
       includeBuild("../wbanalytics2android") {
           dependencySubstitution {
               substitute(module("ru.wildberries:analytics2"))
                   .using(project(":library"))
           }
       }
   }
   ```

**Что это значит:**
- Если в gradle.properties вашего проекта указано `wb.analytics.include=true`, то при сборке
будет подключён исходный код библиотеки из соседней папки `../wbanalytics2android` вместо
артефакта из Nexus.
- Это удобно для локальной разработки: вы можете вносить изменения в библиотеку и сразу видеть
их в основном проекте.
- Если свойство не указано или равно `false`, будет использоваться опубликованная версия из
Nexus.

## Разработка

Как вести разработку одновременно с использованием репозитория, в который подключается библиотека.

1. Склонируйте
   репозиторий [wbanalytics2android](https://gitlab.wildberries.ru/mobile/androidnative/wbanalytics2android)
   в соседнюю директорию к основному проекту:

   ```bash
   git clone git@gitlab.wildberries.ru:mobile/androidnative/wbanalytics2android.git
   ```

2. Подключите репозиторий wbanalytics2android в репозиторий основного проекта используя [Gradle
   `includeBuild` совместно с
   `dependencySubstitution`](https://docs.gradle.org/current/userguide/composite_builds.html#included_build_declaring_substitutions):

   ```gradle
   includeBuild("../wbanalytics2android") {
       dependencySubstitution {
           substitute(module("ru.wildberries:analytics2"))
               .using(project(":library"))
       }
   }
   ```

Таким образом, будет собираться проект `wbanalytics2android` перед сборкой основного проекта, а
вместо подключенной библиотеки `ru.wildberries:analytics2` будет использоваться модуль `:library` из
репозитория `wbanalytics2android`.

При этом, если вы разрабатываете в AndroidStudio у вас будет возможность работать в одном
пространстве сразу с обоими репозиториями.

## Использование библиотеки

### Получение экземпляра

Обычно экземпляр `WBAnalytics2` предоставляется через DI (например, Hilt/Dagger):

```kotlin
@Inject
lateinit var wba: WBAnalytics2
```

Или можно создать вручную:

```kotlin
val analytics = WBAnalytics2(
    apiUrlProvider = { "https://a.wb.ru/m/batch" }, // URL для отправки событий
    apiKey = "ВАШ_API_KEY",                         // Ваш API-ключ
    isCollectionEnabled = true                       // Включить/отключить сбор событий
)
```

В реальных проектах часто используется DI и провайдеры, где параметры могут подставляться
динамически, например:

```kotlin
return WBAnalytics2(
    apiUrlProvider = { infraLocalizationUrlOverride.overrideIfNeeded(DEFAULT_PROD_URL) },
    apiKey = "ВАШ_API_KEY",
    isCollectionEnabled = true,
)
```

**Пояснения к параметрам конструктора:**

- `apiUrlProvider` — функция, возвращающая URL для отправки аналитики (обычно
  `"https://a.wb.ru/m/batch"`).
- `apiKey` — API-ключ для авторизации в сервисе аналитики.
- `isCollectionEnabled` — флаг, отвечающий за то, включена ли обработка событий по умолчанию (можно
  менять в рантайме).

## Конфигурация аналитики (WBA2Config)

В текущей версии используется стандартная конфигурация `WBA2Config.Default`, параметры которой
подобраны для оптимальной работы библиотеки. В будущем планируется добавить возможность
конфигурирования этих параметров извне.

### Значения по умолчанию (WBA2Config.Default):

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
wba.logEvent("screen_open", mapOf("screen" to "Main"))
```

#### Сложное событие (например, покупка)

> **Примечание:**
> Для сериализации сложных параметров и построения JSON-структур
> используется [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization). Эта
> библиотека уже входит в зависимости WBAnalytics2, но если вы строите свои структуры, убедитесь, что
> она также доступна в вашем проекте.

```kotlin
wba.logEvent(
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
wba.setCommonParameter("client_id", "123")
wba.setCommonParameters(mapOf("user_id" to "456", "app_version" to "1.2.3"))
```

#### Отключение/включение сбора событий

```kotlin
wba.isCollectionEnabled = false // или true
```

#### Завершение работы аналитики навсегда

```kotlin
wba.finish()
```

### Описание параметров

- **name** — имя события (от 1 до 40 символов).
- **parameters** — карта параметров (ключ — строка, значение — строка или сериализуемый объект).
- Для сложных событий используйте сериализацию в `JsonObject`.
- Поддерживаются вложенные структуры (см. пример покупки).

### Примеры событий из реального кода

- Открытие экрана:  
  `wba.logEvent("Lottery_V", mapOf("name" to "Лотерея", "circulation" to "123"))`
- Покупка:  
  (см. пример выше)
- Клик по баннеру:  
  `wba.logEvent("Banner_T", mapOf("banner_id" to "789", "location" to "main"))`

## FAQ

- **Можно ли вызывать методы c разных потоков?**  
  Да, все публичные методы WBAnalytics2 реализованы потокобезопасно.
- **Как указать apiKey?**  
  Через параметр конструктора или DI.
- **Как добавить пользовательский токен?**  
  Используйте `setCommonParameter("user_token", "TOKEN")`.
- **Что если нет сети?**  
  События сохраняются и будут отправлены при появлении сети.
- **Как завершить сбор событий?**  
  Вызовите `finish()`. После этого возобновить процесс невозможно.

# WB Attribution Tracker

Входит в состав библиотеки, начиная с 1.0.12

## Подключение библиотеки

Аналогично [подключению wb analytics client](#подключение-библиотеки)

## Использование библиотеки

### Создание экземпляра

```kotlin
WBAttributionTracker.Factory.create(
    context = context, // контекст приложения
    withSystemLogs = BuildConfig.DEBUG, // отправлять ли системные логи
    systemLogsTag = "WBAttribution", // тэг, по которому отправляются логи
)
```

### Проверка атрибуции

```kotlin
attributionTracker.checkAttribution(
    analytics = wba2, // экземпляр аналитики для логирования события "app_install"
    onResult = { data: AttributionData? -> // вызывается один раз с данными атрибуции или без них, если не было перехода в приложение с рекламной ссылки определённого типа
        val link = data?.link
        if (link != null) {
            TODO("handle deeplink here")
        }
    }
)
```
