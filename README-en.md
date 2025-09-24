# WB Analytics 2 Android Client
[![ru](https://img.shields.io/badge/lang-ru-green.svg)](https://github.com/wildberries-tech/wba_analytics_sdk_android/blob/develop/README.md)

Android client library for Wildberries analytics.

## Adding the Library

For now, download the aar [from here](library/releases/ru/wildberries/analytics2.public).

## Using the Library

### Getting an Instance

Usually, an instance of `WBAnalytics2` is provided via DI (e.g., Hilt/Dagger):

```kotlin
@Inject
lateinit var wba: WBAnalytics2
```

Or you can create it manually:

```kotlin
val analytics = WBAnalytics2(
    apiUrlProvider = { "https://a.wb.ru/m/batch" }, // URL for sending events
    apiKey = "YOUR_API_KEY",                        // Your API key
    isCollectionEnabled = true                      // Enable/disable event collection
)
```

In real projects, DI and providers are often used, where parameters can be substituted dynamically, for example:

```kotlin
return WBAnalytics2(
    apiUrlProvider = { infraLocalizationUrlOverride.overrideIfNeeded(DEFAULT_PROD_URL) },
    apiKey = "YOUR_API_KEY",
    isCollectionEnabled = true,
)
```

**Constructor parameters explanation:**

- `apiUrlProvider` — function returning the URL for sending analytics (usually
  `"https://a.wb.ru/m/batch"`).
- `apiKey` — API key for authentication in the analytics service.
- `isCollectionEnabled` — flag that controls whether event processing is enabled by default (can be changed at runtime).

## Analytics Configuration (WBA2Config)

The current version uses the standard configuration `WBA2Config.Default`, whose parameters are optimized for library operation.  
In the future, it will be possible to configure these externally.

### Default values (WBA2Config.Default):

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
wba.logEvent("screen_open", mapOf("screen" to "Main"))
```

#### Complex Event (e.g., purchase)

> **Note:**  
> [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) is used for serializing complex parameters and building JSON structures.  
> It is already included in WBAnalytics2 dependencies, but if you create your own structures, ensure it’s available in your project.

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
wba.setCommonParameter("client_id", "123")
wba.setCommonParameters(mapOf("user_id" to "456", "app_version" to "1.2.3"))
```

#### Enable/Disable Event Collection

```kotlin
wba.isCollectionEnabled = false // or true
```

#### Finish Analytics Permanently

```kotlin
wba.finish()
```

### Parameter Description

- **name** — event name (1–40 characters).
- **parameters** — map of parameters (key = string, value = string or serializable object).
- For complex events, use `JsonObject` serialization.
- Nested structures are supported (see purchase example).

### Real Code Examples

- Screen open:  
  `wba.logEvent("Lottery_V", mapOf("name" to "Lottery", "circulation" to "123"))`
- Purchase:  
  (see example above)
- Banner click:  
  `wba.logEvent("Banner_T", mapOf("banner_id" to "789", "location" to "main"))`

## FAQ

- **Can I call methods from different threads?**  
  Yes, all WBAnalytics2 public methods are thread-safe.
- **How to specify apiKey?**  
  Via constructor parameter or DI.
- **How to add a custom token?**  
  Use `setCommonParameter("user_token", "TOKEN")`.
- **What if there is no network?**  
  Events are stored and sent once the network is available.
- **How to stop event collection?**  
  Call `finish()`. After that, the process cannot be resumed.

# WB Attribution Tracker

Part of the library starting from version 1.0.12.

## Adding the Library

Same as [adding the wb analytics client](#adding-the-library).

## Using the Library

### Creating an Instance

```kotlin
WBAttributionTracker.Factory.create(
    context = context, // application context
    withSystemLogs = BuildConfig.DEBUG, // whether to send system logs
    systemLogsTag = "WBAttribution", // log tag
)
```

### Checking Attribution

```kotlin
attributionTracker.checkAttribution(
    analytics = wba2, // analytics instance for logging "app_install"
    onResult = { data: AttributionData? -> // called once with attribution data or null if no matching ad link
        val link = data?.link
        if (link != null) {
            TODO("handle deeplink here")
        }
    }
)
```