# Transformation and Exclusion Rules

This document outlines the exact mapping and filtration rules applied when porting diffs from the private repository (`wbanalytics2android`) to the public repository (`wbanalytics2android-public`).

---

## 1. Strict Exclusions

The following files and components **must never** be ported to the public repository:

### 1.1. E-Commerce Subsystem (`ecom` / `ecommerce`)
- **Source Paths**:
  - `library/src/main/java/ru/wildberries/analytics/ecommerce/**`
  - `library/src/test/java/ru/wildberries/analytics/ecommerce/**`
  - `docs/subsystems/ecommerce.md`
- **Tests**:
  - `WBAnalytics2ProductTest.kt`
  - `WBAnalyticsAffiliatedDiscountTest.kt`
- **Code References**:
  - Any direct references to `ecommerce` classes or initializers in common service locators or builder extensions.

### 1.2. Private & Internal Files
- **GitLab CI**: `.gitlab-ci.yml` and any internal pipeline scripts.
- **Internal Scripts**: Tools under `.ai/tools/`, `.opencode/`, or internal harness syncer scripts.
- **Machine/Dev Configs**: `local.properties`, `.idea/`, build folders.
- **Internal Credentials**: Internal Nexus publishing tokens, internal WB server endpoints.
- **Experimental / Non-Master Branches**: Private feature branches (e.g., WIP LogLevel) unless explicitly requested.

---

## 2. Package and Path Mappings

The public SDK resides under the `ru.wildberries.pub.*` namespace to clearly distinguish public distribution from the private monolith while maintaining consistent `WBAnalytics2` class naming.

### 2.1. File System Path Transformation
| Private Source Path | Public Destination Path |
| :--- | :--- |
| `library/src/<set>/java/ru/wildberries/analytics/**` | `library/src/<set>/java/ru/wildberries/pub/analytics/**` |
| `library/src/<set>/java/ru/wildberries/attribution/**` | `library/src/<set>/java/ru/wildberries/pub/attribution/**` |
| `library/src/<set>/java/ru/wildberries/WBAnalytics2/**` | `library/src/<set>/java/ru/wildberries/pub/WBAnalytics2/**` |
| `demoapp/src/<set>/java/ru/wildberries/analytics/sdk/demo/**` | `demoapp/src/<set>/java/ru/wildberries/pub/analytics/sdk/demo/**` |
| `library/schemas/ru.wildberries.analytics.db.WBAnalytics2Database/**` | `library/schemas/ru.wildberries.pub.analytics.db.WBAnalytics2Database/**` |

### 2.2. In-Code Package Declarations & Imports
| Private Code Symbol | Public Code Symbol |
| :--- | :--- |
| `ru.wildberries.analytics` | `ru.wildberries.pub.analytics` |
| `ru.wildberries.attribution` | `ru.wildberries.pub.attribution` |
| `ru.wildberries.WBAnalytics2` | `ru.wildberries.pub.WBAnalytics2` |
| `ru.wildberries.analytics.sdk.demo` | `ru.wildberries.pub.analytics.sdk.demo` |

---

## 3. Public Platform Compatibility Guards

### 3.1. `minSdk = 23` Invariants
The public library supports `minSdk = 23` (Android 6.0 Marshmallow), whereas the private library may use `minSdk = 24`.
- **`SendOperationScheduler.kt`**:
  - `JobScheduler.getPendingJob(Int)` was introduced in API 24.
  - In public SDK, use:
    ```kotlin
    scheduler.allPendingJobs.any { it.id == SEND_OPERATION_JOB_ID }
    ```
  - `JobInfo.NETWORK_TYPE_NOT_ROAMING` is API 24+. Guard with:
    ```kotlin
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) JobInfo.NETWORK_TYPE_NOT_ROAMING else JobInfo.NETWORK_TYPE_ANY
    ```

### 3.2. Legacy DB Migration
- Public library maintains `OldDatabaseMigrationHelper` to seamlessly migrate users upgrading from previous public releases (`ru.wildanalytics.pub.analytics.db`) or legacy private naming (`ru.wildberries.analytics.db`) into `ru.wildberries.pub.analytics.db`.

### 3.3. ProGuard & Publishing
- `library/consumer-rules.pro` uses keep rules targeting `ru.wildberries.pub.**`.
- `library/build.gradle.kts` publishes coordinates `ru.wildberries.pub:analytics:<version>`.
