package com.lstepnio.egauge

import org.junit.Assert.*
import org.junit.Test

class VehicleDeletionTest {
    private val first = VehicleProfile("first", "First", Draft())
    private val second = VehicleProfile("second", "Second", Draft(), AdapterBinding("a", "AA:BB:CC:DD:EE:01", "public"), TransmissionConnection())
    @Test fun deletingActiveVehicleSelectsRemainingVehicleAndKeepsGaugeAssignmentsUnresolved() {
        val collection = ProfileCollection(second.id, listOf(first, second))
        val gauges = GaugeAssociations("g1", listOf(KnownGauge("g1", "Gauge 1", second.id, "TCM"), KnownGauge("g2", "Gauge 2", first.id, "ECM")))
        val removed = collection.withoutVehicle(second.id)
        assertEquals(first, removed.active)
        assertNull(gauges.context("g1", removed))
        assertEquals(first.id, gauges.context("g2", removed)!!.first)
        assertEquals(second.id, gauges.gauges.first().vehicleId)
        assertEquals(removed, ProfileDocumentCodec.decode(ProfileDocumentCodec.encode(removed)))
    }
    @Test fun explicitReassignmentDefaultsToSingleAdapterAndRetainsOtherGauges() {
        val gauges = GaugeAssociations("g1", listOf(KnownGauge("g1", "Gauge 1", second.id, "TCM", true), KnownGauge("g2", "Gauge 2", first.id, "ECM")))
        assertTrue(gauges.assign("g1", second.id, "ECM").gauges.first().bothAdapters)
        val reassigned = gauges.assign("g1", first.id, "ECM")
        assertFalse(reassigned.gauges.first().bothAdapters)
        assertEquals(gauges.gauges[1], reassigned.gauges[1])
        assertEquals(first.id, reassigned.context("g1", ProfileCollection(first.id, listOf(first)))!!.first)
    }
    @Test fun deletingInactiveVehiclePreservesSelectionAndLastVehicleCannotBeDeleted() {
        val collection = ProfileCollection(first.id, listOf(first, second))
        assertEquals(first.id, collection.withoutVehicle(second.id).activeId)
        assertThrows(IllegalArgumentException::class.java) { collection.withoutVehicle(second.id).withoutVehicle(first.id) }
        assertThrows(IllegalArgumentException::class.java) { collection.withoutVehicle("missing") }
        assertEquals(2, collection.profiles.size)
    }
}
