package com.example.raspberry_pi

import com.google.gson.GsonBuilder
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory

object RetrofitClient {
    private const val BASE_URL = "http://203.255.81.72:10021/"
    val gson = GsonBuilder().setLenient().create()

    val api: SensorApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        retrofit.create(SensorApiService::class.java)
    }
}
