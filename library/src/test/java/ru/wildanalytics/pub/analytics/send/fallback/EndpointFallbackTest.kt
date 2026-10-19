package ru.wildanalytics.pub.analytics.send.fallback

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import ru.wildanalytics.pub.analytics.domain.ApiData

internal class EndpointFallbackTest {

    private val fallback = EndpointFallback()
    private val primary = ApiData(apiUrl = PRIMARY_URL, apiKey = API_KEY)

    @Test
    fun `given registered fallback, when host is not activated, then primary url is used`() {
        // given
        fallback.register(API_KEY, FALLBACK_URL)

        // when
        val url = fallback.effectiveUrl(primary)

        // then
        url shouldBe PRIMARY_URL
    }

    @Test
    fun `given registered fallback, when host is activated, then fallback url is used`() {
        // given
        fallback.register(API_KEY, FALLBACK_URL)

        // when
        val activated = fallback.activate(primary)

        // then
        activated shouldBe true
        fallback.effectiveUrl(primary) shouldBe FALLBACK_URL
    }

    @Test
    fun `given activated host, when it is activated again, then activation is not first`() {
        // given
        fallback.register(API_KEY, FALLBACK_URL)
        fallback.activate(primary)

        // when
        val activated = fallback.activate(primary)

        // then
        activated shouldBe false
    }

    @Test
    fun `given two keys with fallback, when host is activated, then both keys use fallback`() {
        // given
        fallback.register(API_KEY, FALLBACK_URL)
        fallback.register(OTHER_API_KEY, FALLBACK_URL)

        // when
        fallback.activate(primary)

        // then
        fallback.effectiveUrl(ApiData(PRIMARY_URL, OTHER_API_KEY)) shouldBe FALLBACK_URL
    }

    @Test
    fun `given key without fallback, when host is activated by another key, then it keeps primary`() {
        // given
        fallback.register(API_KEY, FALLBACK_URL)

        // when
        fallback.activate(primary)

        // then
        fallback.effectiveUrl(ApiData(PRIMARY_URL, OTHER_API_KEY)) shouldBe PRIMARY_URL
        fallback.activate(ApiData(PRIMARY_URL, OTHER_API_KEY)) shouldBe false
    }

    @Test
    fun `given proxy url with other path, when fallback is requested, then it is not applicable`() {
        // given
        val proxy = ApiData(apiUrl = "https://api.wildberries.kz/a.wb/m/batch", apiKey = API_KEY)
        fallback.register(API_KEY, FALLBACK_URL)

        // when
        val url = fallback.fallbackUrlOrNull(proxy)

        // then
        url.shouldBeNull()
        fallback.activate(proxy) shouldBe false
        fallback.effectiveUrl(proxy) shouldBe proxy.apiUrl
    }

    @Test
    fun `given invalid or http url, when registered, then it is rejected`() {
        listOf("not a url", "http://afb.wb.ru/m/batch").forEach { value ->
            // when
            val accepted = fallback.register(API_KEY, value)

            // then
            accepted shouldBe false
            fallback.fallbackUrlOrNull(primary).shouldBeNull()
        }
    }

    @Test
    fun `given empty url, when registered, then it is accepted without fallback`() {
        listOf(null, "", " ").forEach { value ->
            // when
            val accepted = fallback.register(API_KEY, value)

            // then
            accepted shouldBe true
            fallback.fallbackUrlOrNull(primary).shouldBeNull()
        }
    }

    @Test
    fun `given fallback on the same host, when requested, then it is not applicable`() {
        // given
        fallback.register(API_KEY, PRIMARY_URL)

        // when
        val url = fallback.fallbackUrlOrNull(primary)

        // then
        url.shouldBeNull()
    }

    @Test
    fun `given registered fallback, when another instance registers the key without fallback, then fallback is kept`() {
        // given
        fallback.register(API_KEY, FALLBACK_URL)

        // when
        fallback.register(API_KEY, null)

        // then
        fallback.fallbackUrlOrNull(primary) shouldBe FALLBACK_URL
    }

    @Test
    fun `given activated host, when other host is checked, then it keeps primary`() {
        // given
        val other = ApiData(apiUrl = OTHER_PRIMARY_URL, apiKey = OTHER_API_KEY)
        fallback.register(API_KEY, FALLBACK_URL)
        fallback.register(OTHER_API_KEY, FALLBACK_URL)

        // when
        fallback.activate(primary)

        // then
        fallback.effectiveUrl(primary) shouldBe FALLBACK_URL
        fallback.effectiveUrl(other) shouldBe OTHER_PRIMARY_URL
    }

    private companion object {
        const val API_KEY = "apiKey"
        const val OTHER_API_KEY = "otherApiKey"
        const val PRIMARY_URL = "https://a.wb.ru/m/batch"
        const val OTHER_PRIMARY_URL = "https://a-eu.wb.ru/m/batch"
        const val FALLBACK_URL = "https://afb.wb.ru/m/batch"
    }
}
