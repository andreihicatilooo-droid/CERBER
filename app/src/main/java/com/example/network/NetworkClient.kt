package com.example.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object NetworkClient {
    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        .build()

    private val placesRetrofit = Retrofit.Builder()
        .baseUrl("https://maps.googleapis.com/maps/api/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    val placesApi: PlacesApi = placesRetrofit.create(PlacesApi::class.java)
}
