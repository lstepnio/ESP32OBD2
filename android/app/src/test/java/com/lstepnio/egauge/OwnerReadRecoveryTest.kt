package com.lstepnio.egauge

import org.junit.Assert.*
import org.junit.Test
class OwnerReadRecoveryTest {
    @Test fun invalidOffsetCanSelectOnlyReadStatusOnce() {
        assertArrayEquals(byteArrayOf(0x17, 1, 0, 0, 0), ownerReadResetCommand(7, false))
        assertNull(ownerReadResetCommand(7, true))
    }
    @Test fun authenticationAndOtherFailuresNeverResetOrBypassOwnerChecks() {
        for (status in listOf(0, 5, 8, 13, 15, 133)) assertNull(ownerReadResetCommand(status, false))
    }
}
