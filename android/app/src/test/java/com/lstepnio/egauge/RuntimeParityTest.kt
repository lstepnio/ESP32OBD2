package com.lstepnio.egauge

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeParityTest {
    @Test fun sharedRuntimeFixturesMatchTheContract() {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream("runtime-identity.json"))
        val fixtures = JSONArray(stream.bufferedReader().use { it.readText() })
        assertTrue(fixtures.length() > 0)
        for (index in 0 until fixtures.length()) {
            val fixture = fixtures.getJSONObject(index)
            val id = fixture.getString("id")
            val bytes = fixture.getString("hex").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            if (!fixture.getBoolean("accepted")) {
                assertThrows(id, IllegalArgumentException::class.java) {
                    GaugeProtocolCodec.runtimeIdentity(bytes)
                }
                continue
            }
            val identity = GaugeProtocolCodec.runtimeIdentity(bytes)
            assertEquals(id, fixture.getLong("storedRevision"), identity.storedRevision)
            assertEquals(id, fixture.getString("confirmation"), confirmRuntime(
                fixture.getLong("expectedRevision"), fixture.getString("expectedSha256"), identity,
            ).name)
        }
    }
}
