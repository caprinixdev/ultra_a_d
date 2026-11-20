package com.gsx.googleadcompose.GoogleAds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AdReusePoolTest {

    private class FakeAd(val name: String)

    private val destroyed = mutableListOf<String>()
    private val pool = AdReusePool<FakeAd>(maxSize = 2, ttlMs = 1_000) { destroyed += it.name }

    @Test
    fun takeGivesTheAdOnceWithoutDestroyingIt() {
        val a = FakeAd("a")
        pool.put("k", a, loadedAtMs = 100)

        assertSame(a, pool.take("k", nowMs = 500)?.ad)
        assertNull(pool.take("k", nowMs = 500))
        assertEquals(emptyList<String>(), destroyed)
    }

    @Test
    fun keyKeepsGroupsApartAndNullTakesAny() {
        pool.put("small", FakeAd("s"), loadedAtMs = 100)

        assertNull(pool.take("large", nowMs = 200))
        assertEquals("s", pool.take(null, nowMs = 200)?.ad?.name)
    }

    @Test
    fun takeGivesTheNewestAdOfTheGroup() {
        pool.put("k", FakeAd("old"), loadedAtMs = 100)
        pool.put("k", FakeAd("new"), loadedAtMs = 200)

        assertEquals("new", pool.take("k", nowMs = 300)?.ad?.name)
        assertEquals("old", pool.take("k", nowMs = 300)?.ad?.name)
    }

    @Test
    fun expiredAdsAreDestroyedNotGiven() {
        pool.put("k", FakeAd("stale"), loadedAtMs = 0)

        assertNull(pool.take("k", nowMs = 1_000))
        assertEquals(listOf("stale"), destroyed)
    }

    @Test
    fun overflowDestroysTheOldest() {
        pool.put("k", FakeAd("1"), loadedAtMs = 100)
        pool.put("k", FakeAd("2"), loadedAtMs = 200)
        pool.put("k", FakeAd("3"), loadedAtMs = 300)

        assertEquals(listOf("1"), destroyed)
    }

    @Test
    fun puttingTheSameAdTwiceKeepsOneCopy() {
        val a = FakeAd("a")
        pool.put("k", a, loadedAtMs = 100)
        pool.put("k", a, loadedAtMs = 100)

        assertSame(a, pool.take("k", nowMs = 200)?.ad)
        assertNull(pool.take("k", nowMs = 200))
    }

    @Test
    fun clearDestroysEverything() {
        pool.put("a", FakeAd("a"), loadedAtMs = 100)
        pool.put("b", FakeAd("b"), loadedAtMs = 100)
        pool.clear()

        assertEquals(listOf("a", "b"), destroyed)
        assertNull(pool.take(null, nowMs = 200))
    }
}
