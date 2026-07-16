package ru.wildanalytics.pub.analytics

import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import ru.wildanalytics.pub.analytics.utils.TestLogger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
internal class EventEnricherRegistryImplTest {

    private fun registry(log: WildAnalyticsLogger = TestLogger()) = EventEnricherRegistryImpl(log = log)

    private fun jsonObjectOf(vararg pairs: Pair<String, String>): JsonObject =
        JsonObject(pairs.associate { (k, v) -> k to JsonPrimitive(v) as JsonElement })

    @Test
    fun `no enrichers returns the same parameters instance`() = runTest {
        val subject = registry()
        val params = jsonObjectOf("a" to "1")

        val result = subject.enrich("event", params)

        assertSame(params, result)
    }

    @Test
    fun `single enricher adds new field while keeping originals`() = runTest {
        val subject = registry()
        subject.append { _, _ -> listOf(AnalyticsField("added", JsonPrimitive("v"))) }

        val result = subject.enrich("event", jsonObjectOf("a" to "1"))

        result shouldContainExactly mapOf(
            "a" to JsonPrimitive("1"),
            "added" to JsonPrimitive("v"),
        )
    }

    @Test
    fun `single enricher can add several fields at once`() = runTest {
        val subject = registry()
        subject.append { _, _ ->
            listOf(
                AnalyticsField("f1", JsonPrimitive("v1")),
                AnalyticsField("f2", JsonPrimitive("v2")),
            )
        }

        val result = subject.enrich("event", jsonObjectOf("a" to "1"))

        result shouldContainExactly mapOf(
            "a" to JsonPrimitive("1"),
            "f1" to JsonPrimitive("v1"),
            "f2" to JsonPrimitive("v2"),
        )
    }

    @Test
    fun `enricher cannot overwrite an existing parameter`() = runTest {
        val subject = registry()
        subject.append { _, _ -> listOf(AnalyticsField("a", JsonPrimitive("from_enricher"))) }

        val result = subject.enrich("event", jsonObjectOf("a" to "original"))

        result shouldContainExactly mapOf("a" to JsonPrimitive("original"))
    }

    @Test
    fun `only non-colliding fields of an enricher are added`() = runTest {
        val subject = registry()
        subject.append { _, _ ->
            listOf(
                AnalyticsField("a", JsonPrimitive("from_enricher")),
                AnalyticsField("fresh", JsonPrimitive("v")),
            )
        }

        val result = subject.enrich("event", jsonObjectOf("a" to "original"))

        result shouldContainExactly mapOf(
            "a" to JsonPrimitive("original"),
            "fresh" to JsonPrimitive("v"),
        )
    }

    @Test
    fun `within a single enricher list the first occurrence of a key wins`() = runTest {
        val subject = registry()
        subject.append { _, _ ->
            listOf(
                AnalyticsField("k", JsonPrimitive("first")),
                AnalyticsField("k", JsonPrimitive("second")),
            )
        }

        val result = subject.enrich("event", JsonObject(emptyMap()))

        result shouldContainExactly mapOf("k" to JsonPrimitive("first"))
    }

    @Test
    fun `enricher receives the original parameters`() = runTest {
        val subject = registry()
        var seenName: String? = null
        var seenParams: JsonObject? = null
        subject.append { name, params ->
            seenName = name
            seenParams = params
            emptyList()
        }
        val params = jsonObjectOf("a" to "1")

        subject.enrich("my_event", params)

        seenName shouldBe "my_event"
        seenParams shouldBe params
    }

    @Test
    fun `on key collision between enrichers the first registered wins`() = runTest {
        val subject = registry()
        subject.append { _, _ -> listOf(AnalyticsField("k", JsonPrimitive("first"))) }
        subject.append { _, _ -> listOf(AnalyticsField("k", JsonPrimitive("second"))) }

        val result = subject.enrich("event", JsonObject(emptyMap()))

        result shouldContainExactly mapOf("k" to JsonPrimitive("first"))
    }

    @Test
    fun `enricher returning empty list adds nothing`() = runTest {
        val subject = registry()
        subject.append { _, _ -> emptyList() }

        val result = subject.enrich("event", jsonObjectOf("a" to "1"))

        result shouldContainExactly mapOf("a" to JsonPrimitive("1"))
    }

    @Test
    fun `throwing enricher is logged and does not break other enrichers`() = runTest {
        val log = mockk<WildAnalyticsLogger>(relaxed = true)
        every { log.isEnabled } returns true
        val subject = registry(log = log)
        subject.append { _, _ -> error("boom") }
        subject.append { _, _ -> listOf(AnalyticsField("safe", JsonPrimitive("v"))) }

        val result = subject.enrich("event", jsonObjectOf("a" to "1"))

        result shouldContainExactly mapOf(
            "a" to JsonPrimitive("1"),
            "safe" to JsonPrimitive("v"),
        )
        verify { log.logWarn(any<String>()) }
    }

    @Test
    fun `clear removes all enrichers`() = runTest {
        val subject = registry()
        subject.append { _, _ -> listOf(AnalyticsField("added", JsonPrimitive("v"))) }

        subject.clear()
        val result = subject.enrich("event", jsonObjectOf("a" to "1"))

        result shouldContainExactly mapOf("a" to JsonPrimitive("1"))
    }

    @Test
    fun `concurrent append keeps every registered enricher`() = runTest {
        val subject = registry()
        val count = 50
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val done = CountDownLatch(count)
        repeat(count) { index ->
            pool.execute {
                start.await()
                subject.append { _, _ -> listOf(AnalyticsField("k$index", JsonPrimitive("v$index"))) }
                done.countDown()
            }
        }
        start.countDown()
        done.await()
        pool.shutdown()

        val result = subject.enrich("event", JsonObject(emptyMap()))

        result.size shouldBe count
    }
}
