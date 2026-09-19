package com.example.raspberry_pi

import java.util.Locale

object SensorCsv {
    const val legacyHeader = "timestamp,device_name,device_address,rssi,uuid,raw_service_data"
    const val header = "$legacyHeader,temperature_c,humidity_percent,aqi,tvoc_ppb,eco2_ppm,sensor_timestamp_unix"

    fun row(
        timestamp: String,
        name: String?,
        address: String,
        rssi: Int,
        uuid: String,
        rawServiceData: String,
        packet: SensorPacket?
    ): String {
        val originalFields = listOf(
            timestamp, name ?: "unknown", address, rssi.toString(), uuid, rawServiceData
        ).joinToString(",") { csvCell(it) }
        return "$originalFields,${sensorFields(packet)}"
    }

    fun upgradeLegacyRow(row: String): String {
        val rawStart = row.lastIndexOf(",\"")
        require(rawStart >= 0 && row.endsWith('"')) { "기존 CSV 행의 형식을 읽을 수 없습니다." }
        val rawServiceData = row.substring(rawStart + 2, row.length - 1)
        return "$row,${sensorFields(parseRawServiceData(rawServiceData))}"
    }

    private fun parseRawServiceData(text: String): SensorPacket? {
        if (!text.startsWith('[') || !text.endsWith(']')) return null
        val bytes = text.substring(1, text.length - 1).split(',').map { part ->
            val value = part.trim().toIntOrNull() ?: return null
            if (value !in -128..127) return null
            value.toByte()
        }.toByteArray()
        return SensorPacket.parse(bytes)
    }

    private fun sensorFields(packet: SensorPacket?): String {
        if (packet == null) return ",,,,,"
        return listOf(
            String.format(Locale.US, "%.2f", packet.temperature),
            String.format(Locale.US, "%.2f", packet.humidity),
            packet.aqi.toString(),
            packet.tvoc.toString(),
            packet.eco2.toString(),
            packet.timestamp.toString()
        ).joinToString(",")
    }

    private fun csvCell(value: String): String {
        if (value.none { it == ',' || it == '"' || it == '\n' || it == '\r' }) return value
        return "\"${value.replace("\"", "\"\"")}\""
    }
}
