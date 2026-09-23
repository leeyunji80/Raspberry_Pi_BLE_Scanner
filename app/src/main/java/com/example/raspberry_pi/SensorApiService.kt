package com.example.raspberry_pi

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

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
    val sender: String
)


data class SensorResponse(
    val result: String?,
    val message: String?,
    val received_data: ReceivedData?
)

data class ReceivedData(
    val team: String?,
    val sensor: String?
)

interface SensorApiService {
    @POST("sensor/opensrc/test/")
    fun sendSensorData(@Body request: SensorRequest): Call<SensorResponse>
}
