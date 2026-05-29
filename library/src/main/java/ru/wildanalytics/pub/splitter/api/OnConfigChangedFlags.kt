package ru.wildanalytics.pub.splitter.api

/**
 * A data class that holds flags indicating the required actions after a configuration change.
 *
 * @param softFetch If `true`, a soft fetch of properties is required. if [forceFetch] `true`, [softFetch] will be ignored.
 * @param updateCacheFromLocalSource If `true`, the cache will be updated from the local data source.
 * @param forceFetch If `true`, a forced fetch of properties is required, ignoring any throttling.
 */
public data class OnConfigChangedFlags(
    val softFetch: Boolean = false,
    val updateCacheFromLocalSource: Boolean = false,
    val forceFetch: Boolean = false,
)
