package com.gsx.googleadcompose.helper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineGateTest {

    @Test
    fun `nguoi dung mien phi phai co mang`() {
        assertTrue(OfflineGate.mustBeOnline(enabled = true, premium = false))
    }

    @Test
    fun `nguoi da mua khong can mang`() {
        assertFalse(OfflineGate.mustBeOnline(enabled = true, premium = true))
    }

    @Test
    fun `app tat chot thi khong ai bi chan`() {
        assertFalse(OfflineGate.mustBeOnline(enabled = false, premium = false))
        assertFalse(OfflineGate.mustBeOnline(enabled = false, premium = true))
    }
}
