package com.example.raspberry_pi

import com.google.gson.JsonObject
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

enum class TransmissionMode(val displayName: String) {
    NORMAL("정상"),
    RAW_TAMPERED("RAW 변조"),
    VALUE_MISMATCH("값 불일치"),
    BAD_RAW("RAW 형식 오류");

    fun next(): TransmissionMode {
        val modes = entries
        return modes[(ordinal + 1) % modes.size]
    }
}

data class TransmissionValues(
    val raw: String,
    val temperature: Float
)

fun applyTransmissionMode(
    mode: TransmissionMode,
    raw: String,
    temperature: Float
): TransmissionValues {
    return when (mode) {
        TransmissionMode.NORMAL -> TransmissionValues(raw, temperature)
        TransmissionMode.RAW_TAMPERED -> {
            val changedFirstNibble = if (raw.first() == '0') '1' else '0'
            TransmissionValues(changedFirstNibble + raw.drop(1), temperature)
        }
        TransmissionMode.VALUE_MISMATCH -> TransmissionValues(raw, temperature + 10.0f)
        TransmissionMode.BAD_RAW -> TransmissionValues(raw.dropLast(2), temperature)
    }
}

data class SensorRequest(
    val key: String = "opensrc2026",
    val team: String,
    val sensor: String,
    val mac: String,
    val temp: Float,
    val humidity: Float,
    val AQI: Int,
    val TVOC: Int,
    val eCO2: Int,
    val timestamp: Long,
    val lat: Double,
    val lon: Double,
    val sender: String,
    val raw: String
)


data class SensorResponse(
    val result: String?,
    val message: String?,
    val received_data: ReceivedData?,
    val verified: Boolean?,
    val status: String?,
    val detail: String?,
    val expected: JsonObject?
)

data class ReceivedData(
    val team: String?,
    val sensor: String?
)

interface SensorApiService {
    @POST("sensor/opensrc/test/")
    fun sendSensorData(@Body request: SensorRequest): Call<SensorResponse>
}
