# Migration Guide: from WBAnalytics2 to WildAnalytics (v1.0.36)

This guide describes the process of updating the Android SDK from the `WBAnalytics2` version (`ru.wildberries` package) to the new `WildAnalytics` version (`ru.wildanalytics.pub` package).

## 1. Dependency Update

The library is now published under new coordinates. All new versions (starting from v1.0.36) are located in the `ru.wildanalytics` group.

```diff
- implementation("ru.wildberries:analytics2:1.0.35")
+ implementation("ru.wildanalytics:pub:1.0.36")
```

The choice of how to obtain artifacts (using the local folder [library/releases](./library/releases), publishing to Maven Local, or setting up an external repository) remains at the discretion of the development team. The artifacts are located at the following paths:
*   **New versions (1.0.36 and later):** [library/releases/ru/wildanalytics/pub](./library/releases/ru/wildanalytics/pub)
*   **Old versions (up to 1.0.35):** [library/releases/ru/wildberries/analytics2.public](./library/releases/ru/wildberries/analytics2.public)

## 2. Import Changes

The library package has been completely changed. Perform a global search and replace (Replace in Path) throughout the project:

```diff
-import ru.wildberries.analytics.*
+import ru.wildanalytics.pub.analytics.*
```

**Recommended patterns for replacement:**
*   `ru.wildberries.analytics` -> `ru.wildanalytics.pub.analytics`
*   `ru.wildberries.attribution` -> `ru.wildanalytics.pub.attribution`
*   `ru.wildberries.splitter` -> `ru.wildanalytics.pub.splitter`

## 3. Entry Point Renaming

The main library interface is now called `WildAnalytics`. Method names remain the same.

```diff
-val analytics: WBAnalytics2 = ...
+val analytics: WildAnalytics = ...
```

## 4. Public Types Update

All types with the `WB` or `WBA2` prefix have been renamed to match the new branding:

| Old Name | New Name |
| :--- | :--- |
| `WBAnalytics2` | `WildAnalytics` |
| `WBA2Config` | `WildAnalyticsConfig` |
| `WBSplitter` | `WildSplitter` |
| `WBAttributionTracker` | `WildAttributionTracker` |
| `WBDeviceInfoProvider` | `WildDeviceInfoProvider` |
| `WBAttributionLogger` | `WildAttributionLogger` |

## 5. Automatic Data Migration

The library includes a built-in `OldDatabaseMigrationHelper` mechanism that ensures data is transferred from the old DB to the new one upon the first launch.

*   **If the private library is absent:** All events and statistics are copied to the new DB, and the old file `ru.wildberries.analytics.db` is deleted.
*   **If the private library is present:** Only statistics (`sentInfo`) are copied to ensure counter continuity. The old file remains untouched for the private library to use.

> [!IMPORTANT]
> Migration occurs automatically in a background thread during the first initialization of `WildAnalytics`. You don't need to write any additional code.

## 6. What has NOT changed

Despite the name changes, the following components remain compatible:
*   **Method APIs:** Signatures of `logEvent`, `setCommonParameters`, and other methods remain the same.
*   **Database Schema:** The table structure is identical to version 7 of the old library.

## 7. Migrating from WildAnalytics 1.0.36 to 1.0.37+

> [!IMPORTANT]
> If you are already using `WildAnalytics` 1.0.36 or earlier, please note the following changes introduced in 1.0.37+.

### 7.1. OkHttp is Now Part of the Public API

The library now exports `okhttp3` as an `api` dependency because custom HTTP headers are passed via `okhttp3.Headers`.

*   You do not need to add `okhttp` manually unless you use it directly.
*   If you already have your own `okhttp` dependency, make sure it is compatible with the version used by WildAnalytics.

### 7.2. New Public API Methods

The `WildAnalytics` interface now includes:

*   `setCustomHeader(key: String, value: String?)` — set an HTTP header for all analytics requests (e.g., anti-bot token `X-Wbaas-Token`).
*   `setCustomHeaders(headers: okhttp3.Headers)` — set multiple headers at once.
*   `addEventEnricher(enricher: EventEnricher)` — register a handler that can add fields to every event.

Methods `logEvent`, `logImportantEvent`, `setCommonParameter(s)`, and `finish` retain their previous signatures.

### 7.3. Changes to the Event JSON Contract

The sent events and metadata now include new fields. They are populated automatically and do not require integration code changes, but may be relevant for backend processing:

*   In events — `session_value`.
*   In metadata — `timezone` and `device_ad_id_type` (`gaid` or `oaid`).

### 7.4. Database

The library database has been updated to version **7**. Room performs automatic migration for existing installations without any action required from the app.

### 7.5. OAID Support

In addition to GAID, the library can now use the Huawei advertising identifier (OAID). If GAID is unavailable on the device, it will attempt to use OAID. The identifier type used is sent in the `device_ad_id_type` field.

## 8. Post-migration Checklist

- [ ] Dependency updated in `build.gradle`.
- [ ] Bulk replace of imports from `ru.wildberries` to `ru.wildanalytics.pub` completed.
- [ ] `AndroidManifest.xml` checked (if it contained references to SDK classes).
- [ ] ProGuard/R8 rules updated (if custom paths were used).
- [ ] Project cleaned and rebuilt (`./gradlew clean assemble`).

## 9. Useful Tips for Android Studio

For quick migration, use `Ctrl+Shift+R`:
1. **Search:** `WBAnalytics2` -> **Replace with:** `WildAnalytics`
2. **Search:** `WBA2` -> **Replace with:** `WildAnalytics`
3. **Search:** `WB` -> **Replace with:** `Wild` (be careful with variable names where `WB` might be part of a word).
