package ru.wildanalytics.pub.analytics.transport

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import okhttp3.Headers
import okhttp3.Headers.Companion.headersOf

internal class CustomHeadersRepositoryTest : StringSpec({

    "GIVEN empty repository WHEN current THEN returns empty headers" {
        val repo = CustomHeadersRepositoryImpl()
        repo.current().size shouldBe 0
    }

    "GIVEN set value WHEN current THEN contains key" {
        val repo = CustomHeadersRepositoryImpl()
        repo.set("X-Wbaas-Token", "token")
        repo.current().values("X-Wbaas-Token") shouldBe listOf("token")
    }

    "GIVEN existing key WHEN set again THEN value overwritten" {
        val repo = CustomHeadersRepositoryImpl()
        repo.set("X-Wbaas-Token", "old")
        repo.set("X-Wbaas-Token", "new")
        repo.current().values("X-Wbaas-Token") shouldBe listOf("new")
    }

    "GIVEN existing key WHEN set null THEN key removed" {
        val repo = CustomHeadersRepositoryImpl()
        repo.set("X-Wbaas-Token", "token")
        repo.set("X-Wbaas-Token", null)
        repo.current().values("X-Wbaas-Token") shouldBe emptyList()
    }

    "GIVEN existing keys WHEN setAll THEN present keys replaced and untouched keys kept" {
        val repo = CustomHeadersRepositoryImpl()
        repo.set("keep", "1")
        repo.set("replace", "old")
        repo.setAll(headersOf("replace", "new", "add", "x"))
        repo.current().values("keep") shouldBe listOf("1")
        repo.current().values("replace") shouldBe listOf("new")
        repo.current().values("add") shouldBe listOf("x")
    }

    "GIVEN multiple values for one name WHEN setAll THEN all values preserved" {
        val repo = CustomHeadersRepositoryImpl()
        val multi = Headers.Builder()
            .add("X-Multi", "a")
            .add("X-Multi", "b")
            .build()
        repo.setAll(multi)
        repo.current().values("X-Multi") shouldBe listOf("a", "b")
    }

    "GIVEN blank key WHEN set THEN ignored" {
        val repo = CustomHeadersRepositoryImpl()
        repo.set("   ", "value")
        repo.current().size shouldBe 0
    }

    "GIVEN current snapshot WHEN repository mutated THEN snapshot unchanged" {
        val repo = CustomHeadersRepositoryImpl()
        repo.set("a", "1")
        val snapshot = repo.current()
        repo.set("b", "2")
        snapshot.values("a") shouldBe listOf("1")
        snapshot.values("b") shouldBe emptyList()
    }
})
