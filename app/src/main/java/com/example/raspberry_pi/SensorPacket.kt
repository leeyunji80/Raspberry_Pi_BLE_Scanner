package com.example.raspberry_pi

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

data class SensorPacket(
    val temperature: Float,
    val humidity: Float,
    val aqi: Int,
    val tvoc: Int,
    val eco2: Int,
    val timestamp: Long
) {
    companion object {
        const val VALUE_LENGTH = 13
        const val TAG_LENGTH = 8
        const val VERIFIED_PACKET_LENGTH = VALUE_LENGTH + TAG_LENGTH

        fun parse(data: ByteArray?): SensorPacket? {
            if (data == null || data.size < VALUE_LENGTH) return null
            val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val temperature = buf.short / 100.0f
            val humidity = (buf.short.toInt() and 0xFFFF) / 100.0f
            val aqi = buf.get().toInt() and 0xFF
            val tvoc = buf.short.toInt() and 0xFFFF
            val eco2 = buf.short.toInt() and 0xFFFF
            val timestamp = buf.int.toLong() and 0xFFFFFFFFL
            return SensorPacket(temperature, humidity, aqi, tvoc, eco2, timestamp)
        }

        fun verifiedRawHex(data: ByteArray?): String? {
            if (data == null || data.size != VERIFIED_PACKET_LENGTH) return null
            return data.joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xFF)
            }
        }
    }

    override fun toString(): String {
        return String.format(
            Locale.KOREA,
            "온도: %.2f°C\n습도: %.2f%%\nAQI: %d\nTVOC: %d ppb\neCO₂: %d ppm",
            temperature, humidity, aqi, tvoc, eco2
        )
    }
}
