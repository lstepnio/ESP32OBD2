package com.lstepnio.egauge

import com.lstepnio.egauge.ui.state.secondAdapterSetupIssue
import org.junit.Assert.*
import org.junit.Test

class SecondAdapterSetupTest {
    private val primary = AdapterBinding("engine", "AA:BB:CC:DD:EE:01", "public")
    private val child = AdapterBinding("transmission", "AA:BB:CC:DD:EE:02", "public")
    private val vehicle = VehicleProfile("jeep", "Jeep", Draft(), primary, TransmissionConnection())
    @Test fun choosingChildPreservesPrimaryAndWholeDashboard() {
        val updated = vehicle.withSecondAdapter(child)
        assertEquals(primary, updated.primaryAdapter)
        assertEquals(vehicle.dashboardDraft(), updated.dashboardDraft())
        assertEquals(child, updated.transmission!!.adapter)
        assertNull(updated.withSecondAdapter(null).transmission!!.adapter)
        assertEquals(primary, updated.withSecondAdapter(null).primaryAdapter)
    }
    @Test fun missingChildCannotBecomeAPrimaryWrite() {
        assertThrows(IllegalArgumentException::class.java) { vehicle.copy(transmission = null).withSecondAdapter(child) }
        assertThrows(IllegalArgumentException::class.java) { vehicle.copy(draft = TransmissionSetup.draft()).withSecondAdapter(child) }
    }
    @Test fun rejectsSamePhysicalAdapterByAddressOrIdentity() {
        assertThrows(IllegalArgumentException::class.java) { vehicle.withSecondAdapter(primary.copy(id = "different.id")) }
        assertThrows(IllegalArgumentException::class.java) { vehicle.withSecondAdapter(child.copy(id = primary.id)) }
    }
    @Test fun missingSelectionDoesNotTellUserToUpdateFirmware() {
        assertTrue(secondAdapterSetupIssue(primary, null, false, false)!!.startsWith("Choose the second"))
        assertTrue(secondAdapterSetupIssue(null, child, true, true)!!.contains("primary"))
    }
    @Test fun disconnectedCapabilityIsNotMistakenForOldFirmware() {
        assertTrue(secondAdapterSetupIssue(primary, child, false, false)!!.startsWith("Reconnect"))
        assertTrue(secondAdapterSetupIssue(primary, child, true, false)!!.startsWith("Update"))
        assertNull(secondAdapterSetupIssue(primary, child, true, true))
    }
}
