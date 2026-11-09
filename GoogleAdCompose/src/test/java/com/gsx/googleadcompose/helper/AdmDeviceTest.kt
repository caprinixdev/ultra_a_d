package com.gsx.googleadcompose.helper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdmDeviceTest {

    @Test
    fun `may Android Go luon la may yeu`() {
        assertTrue(AdmDevice.isLowEnd(isLowRamDevice = true, totalMemoryMb = 8000))
    }

    @Test
    fun `duoi nguong RAM la may yeu`() {
        assertTrue(AdmDevice.isLowEnd(isLowRamDevice = false, totalMemoryMb = 3199))
    }

    @Test
    fun `tu nguong RAM tro len la may thuong`() {
        assertFalse(AdmDevice.isLowEnd(isLowRamDevice = false, totalMemoryMb = 3200))
    }

    @Test
    fun `chua doc duoc RAM thi khong ket luan may yeu`() {
        assertFalse(AdmDevice.isLowEnd(isLowRamDevice = false, totalMemoryMb = 0))
    }
}
