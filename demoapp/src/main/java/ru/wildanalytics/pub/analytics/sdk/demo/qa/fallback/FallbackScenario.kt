package ru.wildanalytics.pub.analytics.sdk.demo.qa.fallback

import androidx.annotation.StringRes
import ru.wildanalytics.pub.analytics.sdk.demo.R
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.DEFAULT_FALLBACK_URL
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.DEFAULT_MAIN_URL

enum class FallbackScenario(
    @StringRes val title: Int,
    @StringRes val expected: Int,
    @StringRes val precondition: Int?,
    val mainUrl: String,
    val fallbackUrl: String,
    val expectation: FallbackExpectation,
) {
    FALLBACK_SUCCESS(
        title = R.string.qa_fallback_success_title,
        expected = R.string.qa_fallback_success_expected,
        precondition = null,
        mainUrl = UNREACHABLE_MAIN_URL,
        fallbackUrl = DEFAULT_FALLBACK_URL,
        expectation = FallbackExpectation.SWITCH_SUCCESS,
    ),
    FALLBACK_FAILED(
        title = R.string.qa_fallback_failed_title,
        expected = R.string.qa_fallback_failed_expected,
        precondition = null,
        mainUrl = UNREACHABLE_MAIN_URL,
        fallbackUrl = UNREACHABLE_FALLBACK_URL,
        expectation = FallbackExpectation.SWITCH_FAILED,
    ),
    NO_FALLBACK(
        title = R.string.qa_no_fallback_title,
        expected = R.string.qa_no_fallback_expected,
        precondition = null,
        mainUrl = UNREACHABLE_MAIN_URL,
        fallbackUrl = "",
        expectation = FallbackExpectation.NO_SWITCH,
    ),
    HTTP_4XX(
        title = R.string.qa_http_4xx_title,
        expected = R.string.qa_http_4xx_expected,
        precondition = null,
        mainUrl = CLIENT_ERROR_MAIN_URL,
        fallbackUrl = DEFAULT_FALLBACK_URL,
        expectation = FallbackExpectation.NO_SWITCH,
    ),
    AIRPLANE_MODE(
        title = R.string.qa_airplane_title,
        expected = R.string.qa_airplane_expected,
        precondition = R.string.qa_airplane_precondition,
        mainUrl = UNREACHABLE_MAIN_URL,
        fallbackUrl = DEFAULT_FALLBACK_URL,
        expectation = FallbackExpectation.NO_SWITCH,
    ),
    PRIMARY_AFTER_RESTART(
        title = R.string.qa_primary_title,
        expected = R.string.qa_primary_expected,
        precondition = R.string.qa_primary_precondition,
        mainUrl = DEFAULT_MAIN_URL,
        fallbackUrl = DEFAULT_FALLBACK_URL,
        expectation = FallbackExpectation.PRIMARY_ONLY,
    ),
    PRIVATE_DNS(
        title = R.string.qa_private_dns_title,
        expected = R.string.qa_private_dns_expected,
        precondition = R.string.qa_private_dns_precondition,
        mainUrl = DEFAULT_MAIN_URL,
        fallbackUrl = DEFAULT_FALLBACK_URL,
        expectation = FallbackExpectation.SWITCH_SUCCESS,
    ),
    ;

    val number: Int
        get() = ordinal + 1
}

enum class FallbackExpectation {
    SWITCH_SUCCESS,
    SWITCH_FAILED,
    NO_SWITCH,
    PRIMARY_ONLY,
}

const val QA_EVENTS = 5
private const val UNREACHABLE_MAIN_URL = "https://wba-x7q9z.invalid/m/batch"
private const val UNREACHABLE_FALLBACK_URL = "https://wba-fb-x7q9z.invalid/m/batch"
private const val CLIENT_ERROR_MAIN_URL = "https://www.wildberries.ru/m/batch"
