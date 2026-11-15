package com.gsx.googleadcompose.GoogleAds

import org.junit.Assert.assertEquals
import org.junit.Test

class AdCounterRuleTest {

    /** Những lượt (1..n) sẽ hiện ad. */
    private fun dueTurns(startAds: Int, loopAds: Int, n: Int = 12): List<Int> =
        (1..n).filter { AdCounterRule.isDue(it, startAds, loopAds) }

    @Test
    fun `hien o luot dau roi cu 2 luot mot lan`() {
        assertEquals(listOf(1, 3, 5, 7, 9, 11), dueTurns(startAds = 1, loopAds = 2))
    }

    @Test
    fun `chua toi luot bat dau thi khong hien`() {
        assertEquals(listOf(3, 6, 9, 12), dueTurns(startAds = 3, loopAds = 3))
    }

    @Test
    fun `lan nao cung hien khi bat dau 1 va lap 1`() {
        assertEquals((1..12).toList(), dueTurns(startAds = 1, loopAds = 1))
    }

    @Test
    fun `lap 0 hoac am thi chi hien dung luot bat dau`() {
        assertEquals(listOf(2), dueTurns(startAds = 2, loopAds = 0))
        assertEquals(listOf(2), dueTurns(startAds = 2, loopAds = -1))
    }

    @Test
    fun `giong cong thuc countToShowAds cua ban 1_0_20`() {
        // 1.0.20: count < start -> false; count == start -> true; còn lại (count - start) % loop == 0
        for (start in 0..4) for (loop in 1..4) for (count in 1..20) {
            val old = if (count < start) false else if (count == start) true else (count - start) % loop == 0
            assertEquals("start=$start loop=$loop count=$count", old, AdCounterRule.isDue(count, start, loop))
        }
    }

    @Test
    fun `nap san chi khi luot ke tiep toi han`() {
        // preloadIfDue nạp khi isDue(count + 1): với start 1 loop 3, đã bấm 0, 3, 6 lần thì lượt kế (1, 4, 7) hiện
        val preloadAt = (0..8).filter { AdCounterRule.isDue(it + 1, startAds = 1, loopAds = 3) }
        assertEquals(listOf(0, 3, 6), preloadAt)
    }
}
