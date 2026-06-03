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
*   **Event Contracts:** The JSON format and event parameter names have not changed.
*   **Database Schema:** The table structure is identical to version 7 of the old library.
*   **Method APIs:** Signatures of `logEvent`, `setCommonParameters`, and other methods remain the same.

## 7. Post-migration Checklist

- [ ] Dependency updated in `build.gradle`.
- [ ] Bulk replace of imports from `ru.wildberries` to `ru.wildanalytics.pub` completed.
- [ ] `AndroidManifest.xml` checked (if it contained references to SDK classes).
- [ ] ProGuard/R8 rules updated (if custom paths were used).
- [ ] Project cleaned and rebuilt (`./gradlew clean assemble`).

## 8. Useful Tips for Android Studio

For quick migration, use `Ctrl+Shift+R`:
1. **Search:** `WBAnalytics2` -> **Replace with:** `WildAnalytics`
2. **Search:** `WBA2` -> **Replace with:** `WildAnalytics`
3. **Search:** `WB` -> **Replace with:** `Wild` (be careful with variable names where `WB` might be part of a word).
