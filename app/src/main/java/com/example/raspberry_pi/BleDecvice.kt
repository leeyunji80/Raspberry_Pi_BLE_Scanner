package com.example.raspberry_pi

import android.bluetooth.le.ScanRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BleDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
    val uuid: String?,
    val scanRecord: ScanRecord?,
    val sensorPacket: SensorPacket?
) {
    fun toDisplayString(): String {
        val nameText = name ?: "(이름 없음)"
        val uuidText = uuid ?: "알 수 없음"
        val rawText = scanRecord?.toString() ?: "ScanRecord 없음"
        val sensorText = sensorPacket?.let { packet ->
            val measuredAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)
                .format(Date(packet.timestamp * 1000))
            "${packet}\n측정 시각: $measuredAt"
        } ?: "센서 데이터 없음 또는 형식 오류"
        return "$nameText\nMAC: $address\nUUID: $uuidText\nRSSI: $rssi dBm\n$rawText\n$sensorText"
    }
}
