# Wild Analytics Android Client
[![ru](https://img.shields.io/badge/lang-ru-green.svg)](https://github.com/wildberries-tech/wild_analytics_sdk_android/blob/develop/README.md)
[![version](https://img.shields.io/badge/version-1.0.48-blue.svg)](./library/releases/ru/wildanalytics/pub/1.0.48)

Android client library for Wildberries analytics.

## Adding the Library

For now, download the aar [from here](library/releases/ru/wildberries/analytics2.public).

> [!IMPORTANT]
> If you are migrating from the old `WBAnalytics2` version, please refer to the [Migration Guide](./Migration_from_WBAnalytics2_to_WildAnalytics-en.md).

## Using the Library

### Getting an Instance

Usually, an instance of `WildAnalytics` is provided via DI (e.g., Hilt/Dagger):

```kotlin
@Inject
lateinit var analytics: WildAnalytics
```

Or you can create it manually:

```kotlin
val analytics = WildAnalytics(
    apiUrlProvider = { "https://analytics.wb.ru/m/batch" }, // URL for sending events
    apiKey = "YOUR_API_KEY",                        // Your API key
    isCollectionEnabled = true                      // Enable/disable event collection
)
```

In real projects, DI and providers are often used, where parameters can be substituted dynamically, for example:

```kotlin
return WildAnalytics(
    apiUrlProvider = { infraLocalizationUrlOverride.overrideIfNeeded(DEFAULT_PROD_URL) },
    apiKey = "YOUR_API_KEY",
    isCollectionEnabled = true,
)
```

- `apiUrlProvider` — function returning the URL for sending analytics (usually
  `"https://wba.wb.ru/m/batch"`).
- `apiKey` — API key for authentication in the analytics service.
- `enableAutomaticEvents: Boolean` — enable (`true`) for automatic tracking of installations, application launches, and active time. If your product is a library for integration, keep `false`. Defaults to `false`.
- `isCollectionEnabled` — flag that controls whether event processing is enabled by default (can be changed at runtime).
- `requiredCommonParameters: Set<String>` — keys of common parameters without which events should not be sent (see [Required Common Parameters](#required-common-parameters)). Defaults to empty.

## Analytics Configuration (WildAnalyticsConfig)

The current version uses the standard configuration `WildAnalyticsConfig.Default`, whose parameters are optimized for library operation.  
In the future, it will be possible to configure these externally.

### Default values (WildAnalyticsConfig.Default):

- **delays: SendingDelays**
    - `delayBetweenBatches = 2 seconds` — delay between sending event batches.
    - `delayBetweenOperations = 10 seconds` — delay after one complete send operation from DB.
    - `initialDelay = 2 seconds` — initial delay before starting sending.

- **batching: BatchingConfig**
    - `maxEventsInBatch = 200` — maximum number of events in one batch.
    - `maxBatchSizeInBites = 1_048_576` (1 MB) — maximum batch size in bytes (if an event is larger, it will be sent separately).

- **maxEventsInCache = 10,000** — maximum number of events stored in local cache (DB).

- **retryPolicy: RetryPolicy.Exponential**
    - `attempts = 10` — number of attempts.
    - `base = 1.5` — exponential base for retry delays.
    - `additional = 2.5` — additional delay.
    - Minimum delay: 4 sec, maximum: ~60 sec, total retry duration: ~115 sec.

- **transport: HttpTransport** — sending events via HTTP using OkHttp.

> ⚠️ In the future, these parameters will be configurable via external interface or DI.

### Event Logging

#### Simple Event

```kotlin
analytics.logEvent("screen_open", mapOf("screen" to "Main"))
```

#### Complex Event (e.g., purchase)

> **Note:**  
> [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) is used for serializing complex parameters and building JSON structures.  
> It is already included in WildAnalytics dependencies, but if you create your own structures, ensure it’s available in your project.

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
                    put("name", "Socks")
                    put("category", "Clothing")
                    put("brand", "Wildberries")
                    put("variant", "Black")
                    put("price", 1234)
                    put("quantity", 3)
                })
            }
        }
    )
)
```

#### Setting Common Parameters

```kotlin
analytics.setCommonParameter("client_id", "123")
analytics.setCommonParameters(mapOf("user_id" to "456", "app_version" to "1.2.3"))

// typed values are serialized into event JSON as number/boolean rather than string
analytics.setCommonParameter("vpn_status", 1)
analytics.setCommonParameter("is_authorized", true)

// remove a parameter of any type
analytics.setCommonParameter("vpn_status", null)
```

#### Required Common Parameters

Common parameters are merged into an event at the moment of `logEvent`. If the application sets them asynchronously (for example, `client_id` is read from a database), the earliest events — pushes received when the application is closed, automatic SDK events — could be sent without them. To prevent this, list those keys when initializing the instance:

```kotlin
val analytics = WildAnalytics(
    context = context,
    apiKey = "YOUR_API_KEY",
    enableAutomaticEvents = true,
    requiredCommonParameters = setOf("client_id", "lang", "country"),
)
```

The SDK will hold event processing until each of the keys has been set at least once via `setCommonParameter` / `setCommonParameters` (setting a `null` value also counts as setting — this is how anonymous users can be marked), after which missing common parameters will be added to the accumulated events. The wait is one-time and bounded by `REQUIRED_COMMON_PARAMETERS_TIMEOUT` (5 seconds): if a key is not set before timeout, a warning is logged, events proceed as-is, and subsequent events are processed without delay.

Priorities do not change: explicit event parameters and common parameters already known at `logEvent` time take precedence — only missing keys are appended.

#### Custom HTTP Headers (e.g., Anti-bot Token)

```kotlin
import okhttp3.Headers

analytics.setCustomHeader("X-Wbaas-Token", token)
// or several at once:
analytics.setCustomHeaders(
    Headers.Builder()
        .add("X-Wbaas-Token", token)
        .build()
)
```

To remove a header, pass `null`:

```kotlin
analytics.setCustomHeader("X-Wbaas-Token", null)
```

#### Event Enrichment (EventEnricher)

If you need to dynamically add common fields to every event (e.g., experiment flags), register an enricher:

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

An enricher cannot overwrite existing event parameters — it can only add missing ones.

#### Enable/Disable Event Collection

```kotlin
analytics.isCollectionEnabled = false // or true
```

#### Finish Analytics Permanently

```kotlin
analytics.finish()
```

### Parameter Description

- **name** — event name (1–40 characters).
- **parameters** — map of parameters (key = string, value = string or serializable object).
- For complex events, use `JsonObject` serialization.
- Nested structures are supported (see purchase example).

### Real Code Examples

- Screen open:  
  `analytics.logEvent("Lottery_V", mapOf("name" to "Lottery", "circulation" to "123"))`
- Purchase:  
  (see example above)
- Banner click:  
  `analytics.logEvent("Banner_T", mapOf("banner_id" to "789", "location" to "main"))`

### Transition Attribution Events

> [!IMPORTANT]
> The events below are **mandatory for correct user transition attribution** into the app.

#### 1. Open via Dynamic Link / Deeplink (`dynamic_link_app_open`)

Sent when the application is opened via a dynamic link or intercepted deep link.

| Parameter | Type | Description |
| --- | --- | --- |
| `link` | String | URL used to open the application |
| `referrerURL` | String | Referrer URL of the link that opened the app (optional) |

Example:
```kotlin
analytics.logEvent(
    "dynamic_link_app_open",
    mapOf(
        "link" to "https://wb.ru/product/12345",
        "referrerURL" to "https://yandex.ru"
    )
)

// Or via extension function:
analytics.logDeepLink(uri = intent.data, referrerUrl = referrerUri?.toString())
```

#### 2. Open via Push Notification (`dynamic_push_app_open`)

Sent when the application is opened by clicking a push notification.

| Parameter | Type | Description |
| --- | --- | --- |
| `link` | String | Push URL path (deeplink / URL) |
| `push` | String | Push notification text or title |

Example:
```kotlin
analytics.logEvent(
    "dynamic_push_app_open",
    mapOf(
        "link" to "https://wb.ru/promo/sale",
        "push" to "Discounts up to 70% on everything!"
    )
)

// Or via extension function:
analytics.logPushOpen(link = pushUrl, push = pushText)
```

### Automatic System Events

> **enableAutomaticEvents** — enables sending automatic events (default is `false`).  
> If you want to send automatic events, set this flag to `true`.  
> If your product will be integrated into another product, keep it `false` to avoid interference.  
> In the `meta` object of each batch, the `enable_automatic_events` key is sent with `true` or `false`.

The SDK automatically collects and sends core system lifecycle events for each initialized analytics instance:

#### 1. First App Launch (`first_open`)
- **Trigger:** Sent automatically once upon the very first launch of the application on the device.
- **Parameters:** No parameters (`{}`).

#### 2. Application Start (`application_start`)
- **Trigger:** Sent automatically when an analytics instance is initialized.
- **Parameters:**
  | Parameter | Type | Description |
  | --- | --- | --- |
  | `start_location` | String | Start location: `"foreground"` (started with active screen) or `"background"` (background start, push service, etc.) |
  | `cpu` | Double / Number | Maximum CPU clock frequency in GHz (e.g., `2.84`) |
  | `ram` | Int / Number | Total device RAM in GB (e.g., `8`) |
  | `processor_name` | String | Processor / SoC model (e.g., `SM8450`, `gki`) |

#### 3. Heartbeat (`heartbeat`)
- **Trigger:** Sent 30 seconds after the app enters the foreground, and every 30 seconds continuously while the app is active. The timer resets and stops when the app goes into the background.
- **Parameters:** No parameters (`{}`).

## FAQ

- **Can I call methods from different threads?**  
  Yes, all WildAnalytics public methods are thread-safe.
- **How to specify apiKey?**  
  Via constructor parameter or DI.
- **How to add a custom token?**  
  Use `setCommonParameter("user_token", "TOKEN")`.
- **What if there is no network?**  
  Events are stored and sent once the network is available.
- **How to stop event collection?**  
  Call `finish()`. After that, the process cannot be resumed.

# Wild Attribution Tracker

Part of the library starting from version 1.0.12.

## Adding the Library

Same as [adding the wild analytics client](#adding-the-library).

## Using the Library

### Creating an Instance

```kotlin
WildAttributionTracker.Factory.create(
    context = context, // application context
    withSystemLogs = BuildConfig.DEBUG, // whether to send system logs
    systemLogsTag = "WildAttribution", // log tag
)
```

### Checking Attribution

```kotlin
attributionTracker.checkAttribution(
    analytics = analytics, // analytics instance for logging "app_install"
    onResult = { data: AttributionData? -> // called once with attribution data or null if no matching ad link
        val link = data?.link
        if (link != null) {
            TODO("handle deeplink here")
        }
    }
)
```