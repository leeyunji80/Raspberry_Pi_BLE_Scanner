package com.example.raspberry_pi

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun verifiedRawRequiresAllTwentyOneBytesAndUsesUnsignedHex() {
        val fullPacket = sampleBytes + byteArrayOf(
            0xD1.toByte(), 0xA0.toByte(), 0x02, 0x03, 0xA9.toByte(), 0xD6.toByte(), 0x5C, 0xFF.toByte()
        )

        assertEquals(
            "470b501102bc00a602bb1faa6ad1a00203a9d65cff",
            SensorPacket.verifiedRawHex(fullPacket)
        )
        assertEquals(SensorPacket.VERIFIED_PACKET_LENGTH * 2, SensorPacket.verifiedRawHex(fullPacket)!!.length)
        assertTrue(SensorPacket.verifiedRawHex(fullPacket)!!.matches(Regex("[0-9a-f]{42}")))
        assertNull(SensorPacket.verifiedRawHex(fullPacket.copyOf(20)))
        assertNull(SensorPacket.verifiedRawHex(fullPacket + 0x00))
    }

    @Test
    fun apiRequestContainsTheExactRawHexAndRequiredFieldNames() {
        val raw = "470b501102bc00a602bb1faa6ad1a00203a9d65cff"
        val request = SensorRequest(
            team = "9",
            sensor = "environment_sensor",
            mac = "D8:3A:DD:C1:89:2E",
            temp = 28.87f,
            humidity = 44.32f,
            AQI = 2,
            TVOC = 188,
            eCO2 = 678,
            timestamp = 1789534139L,
            lat = 36.629011,
            lon = 127.457092,
            sender = "android-id",
            raw = raw
        )

        val json = Gson().toJson(request)
        assertTrue(json.contains("\"key\":\"opensrc2026\""))
        assertTrue(json.contains("\"AQI\":2"))
        assertTrue(json.contains("\"TVOC\":188"))
        assertTrue(json.contains("\"eCO2\":678"))
        assertTrue(json.contains("\"raw\":\"$raw\""))
    }

    @Test
    fun serverVerificationFieldsAreParsedForSuccessAndFailure() {
        val success = Gson().fromJson(
            """{"result":"Success","message":"ok","verified":true}""",
            SensorResponse::class.java
        )
        val failure = Gson().fromJson(
            """{"result":"Fail","verified":false,"status":"bad_tag","detail":"태그 검증 실패"}""",
            SensorResponse::class.java
        )

        assertEquals(true, success.verified)
        assertEquals("bad_tag", failure.status)
        assertEquals("태그 검증 실패", failure.detail)
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
