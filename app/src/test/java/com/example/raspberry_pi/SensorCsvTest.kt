package com.example.raspberry_pi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SensorCsvTest {
    private val sampleBytes = byteArrayOf(
        71, 11, 80, 17, 2, -68, 0, -90, 2, -69, 31, -86, 106
    )

    @Test
    fun samplePayloadUsesTheExpectedFieldBoundaries() {
        val packet = SensorPacket.parse(sampleBytes)!!
        assertEquals(28.87f, packet.temperature, 0.001f)
        assertEquals(44.32f, packet.humidity, 0.001f)
        assertEquals(2, packet.aqi)
        assertEquals(188, packet.tvoc)
        assertEquals(678, packet.eco2)
        assertEquals(1789534139L, packet.timestamp)
        assertNull(SensorPacket.parse(sampleBytes.copyOf(12)))
    }

    @Test
    fun csvRowAddsParsedColumnsAndPreservesRawBytes() {
        val raw = sampleBytes.joinToString(prefix = "[", postfix = "]")
        val row = SensorCsv.row(
            "2026-09-16 13:49:06", "sensor", "D8:3A:DD:C1:89:2E", -71,
            "0000181a-0000-1000-8000-00805f9b34fb", raw, SensorPacket.parse(sampleBytes)
        )
        assertEquals(
            "2026-09-16 13:49:06,sensor,D8:3A:DD:C1:89:2E,-71," +
                "0000181a-0000-1000-8000-00805f9b34fb,\"$raw\"," +
                "28.87,44.32,2,188,678,1789534139",
            row
        )
    }

    @Test
    fun legacyRowGainsTheSameColumnsWithoutChangingOriginalValues() {
        val oldRow = "2026-09-16 13:49:06,sensor,D8:3A:DD:C1:89:2E,-71," +
            "0000181a-0000-1000-8000-00805f9b34fb," +
            "\"[71, 11, 80, 17, 2, -68, 0, -90, 2, -69, 31, -86, 106]\""
        assertEquals(
            "$oldRow,28.87,44.32,2,188,678,1789534139",
            SensorCsv.upgradeLegacyRow(oldRow)
        )
    }
}
