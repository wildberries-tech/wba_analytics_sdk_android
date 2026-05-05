package ru.wildberries.splitter.api

/**
 * @param apiKey The value for the X-API-KEY header.
 * @param fetchUrl The base URL for the splitter service.
 * @param userId The value for the X-USER-ID header.
 * @param clientId The value for the X-CLIENT-ID header (optional).
 * @param appVersion The value for the Wb-AppVersion header (optional).
 */
public data class WBSplitterConfig(
    val apiKey: String,
    val fetchUrl: String,
    val userId: String,
    val clientId: String? = null,
    val appVersion: String? = null,
)
