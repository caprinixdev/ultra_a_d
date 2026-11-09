package com.gsx.googleadcompose.GoogleAds

import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdUnitChainTest {

    private fun AdUnitChain.drain(): List<String> = generateSequence { next()?.unitId }.toList()

    @Test
    fun `xoay vong bat dau tu con tro roi thu cac ID con lai`() {
        val chain = AdUnitChain.build(listOf("a", "b", "c"), index = -1, maxFallback = 2) { 1 }!!
        assertEquals(listOf("b", "c", "a"), chain.drain())
    }

    @Test
    fun `gioi han so ID du phong`() {
        val chain = AdUnitChain.build(listOf("a", "b", "c", "d"), index = -1, maxFallback = 1) { 0 }!!
        assertEquals(listOf("a", "b"), chain.drain())
    }

    @Test
    fun `khong co du phong khi maxFallback bang 0 hoac am`() {
        assertEquals(listOf("a"), AdUnitChain.build(listOf("a", "b"), -1, 0) { 0 }!!.drain())
        assertEquals(listOf("a"), AdUnitChain.build(listOf("a", "b"), -1, -3) { 0 }!!.drain())
    }

    @Test
    fun `ID trung khong thu lai`() {
        val chain = AdUnitChain.build(listOf("a", "a", "a"), index = -1, maxFallback = 2) { 0 }!!
        assertEquals(listOf("a"), chain.drain())
    }

    @Test
    fun `app chi dinh index thi chi dung dung ID do va khong dong con tro`() {
        var cursorUsed = false
        val chain = AdUnitChain.build(listOf("a", "b", "c"), index = 2, maxFallback = 2) {
            cursorUsed = true
            0
        }!!
        assertEquals(listOf("c"), chain.drain())
        assertFalse(cursorUsed)
    }

    @Test
    fun `index ngoai list hoac list rong tra null`() {
        assertNull(AdUnitChain.build(listOf("a"), index = 1, maxFallback = 2) { 0 })
        assertNull(AdUnitChain.build(emptyList(), index = -1, maxFallback = 2) { 0 })
    }

    @Test
    fun `hasNext phan anh con ID de thu`() {
        val chain = AdUnitChain.build(listOf("a", "b"), index = -1, maxFallback = 1) { 0 }!!
        assertTrue(chain.hasNext)
        assertEquals("a", chain.next()?.unitId)
        assertTrue(chain.hasNext)
        assertEquals("b", chain.next()?.unitId)
        assertFalse(chain.hasNext)
        assertNull(chain.next())
    }

    @Test
    fun `con tro xoay vong qua list`() {
        val cursor = AdUnitCursor()
        assertEquals(listOf(0, 1, 2, 0), List(4) { cursor.next(3) })
    }

    @Test
    fun `con tro khong vuot list khi list ngan di`() {
        val cursor = AdUnitCursor()
        repeat(2) { cursor.next(3) }
        assertEquals(0, cursor.next(2))
    }

    @Test
    fun `chi loi co the do ID moi thu ID khac`() {
        assertTrue(LoadAdError.ErrorCode.NO_FILL.allowsFallback())
        assertTrue(LoadAdError.ErrorCode.INTERNAL_ERROR.allowsFallback())
        assertTrue(LoadAdError.ErrorCode.NETWORK_ERROR.allowsFallback())   // ID sai/đã xoá trả về lỗi này
        assertFalse(LoadAdError.ErrorCode.TIMEOUT.allowsFallback())
        assertFalse(LoadAdError.ErrorCode.CANCELLED.allowsFallback())
        assertFalse(LoadAdError.ErrorCode.APP_ID_MISSING.allowsFallback())
    }
}
