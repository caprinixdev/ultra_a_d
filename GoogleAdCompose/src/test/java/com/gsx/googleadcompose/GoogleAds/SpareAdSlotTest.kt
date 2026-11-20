package com.gsx.googleadcompose.GoogleAds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SpareAdSlotTest {

    private class FakeAd(val name: String)

    private val destroyed = mutableListOf<String>()
    private val slot = SpareAdSlot<FakeAd> { destroyed += it.name }

    private fun entry(name: String, loadedAt: Long) = SpareAdSlot.Entry(FakeAd(name), loadedAt, "unit-$name", 0)

    @Test
    fun emptySlotGivesNothing() {
        assertFalse(slot.has(nowMs = 0, ttlMs = 1_000))
        assertNull(slot.take(nowMs = 0, ttlMs = 1_000))
    }

    @Test
    fun takeReturnsTheAdOnceAndKeepsItAlive() {
        val e = entry("a", loadedAt = 100)
        slot.put(e)

        assertTrue(slot.has(nowMs = 500, ttlMs = 1_000))
        assertSame(e, slot.take(nowMs = 500, ttlMs = 1_000))
        assertNull(slot.take(nowMs = 500, ttlMs = 1_000))
        assertEquals(emptyList<String>(), destroyed)
    }

    @Test
    fun expiredAdIsDestroyedInsteadOfReturned() {
        slot.put(entry("a", loadedAt = 100))

        assertFalse(slot.has(nowMs = 1_100, ttlMs = 1_000))
        assertNull(slot.take(nowMs = 1_100, ttlMs = 1_000))
        assertEquals(listOf("a"), destroyed)
    }

    @Test
    fun newerAdReplacesOlderAndOlderIsDestroyed() {
        slot.put(entry("old", loadedAt = 100))
        val newer = entry("new", loadedAt = 200)
        slot.put(newer)

        assertEquals(listOf("old"), destroyed)
        assertSame(newer, slot.take(nowMs = 300, ttlMs = 1_000))
    }

    @Test
    fun olderAdArrivingLateIsDestroyedAndNewerKept() {
        val newer = entry("new", loadedAt = 200)
        slot.put(newer)
        slot.put(entry("old", loadedAt = 100))

        assertEquals(listOf("old"), destroyed)
        assertSame(newer, slot.take(nowMs = 300, ttlMs = 1_000))
    }

    @Test
    fun puttingTheSameAdTwiceDoesNotDestroyIt() {
        val e = entry("a", loadedAt = 100)
        slot.put(e)
        slot.put(e)

        assertEquals(emptyList<String>(), destroyed)
        assertSame(e, slot.take(nowMs = 200, ttlMs = 1_000))
    }

    @Test
    fun clearDestroysTheKeptAd() {
        slot.put(entry("a", loadedAt = 100))
        slot.clear()

        assertEquals(listOf("a"), destroyed)
        assertNull(slot.take(nowMs = 200, ttlMs = 1_000))
    }

    @Test
    fun destroyErrorDoesNotEscape() {
        val throwing = SpareAdSlot<FakeAd> { error("boom") }
        throwing.put(entry("a", loadedAt = 100))
        throwing.clear()
        assertNull(throwing.take(nowMs = 200, ttlMs = 1_000))
    }
}
