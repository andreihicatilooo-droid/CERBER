package com.example.network

import retrofit2.http.GET
import retrofit2.http.Query
import com.squareup.moshi.JsonClass

interface PlacesApi {
    @GET("place/nearbysearch/json")
    suspend fun getNearbyPlaces(
        @Query("location") location: String,
        @Query("radius") radius: Int,
        @Query("type") type: String,
        @Query("key") apiKey: String
    ): PlacesResponse
}

@JsonClass(generateAdapter = true)
data class PlacesResponse(
    val results: List<PlaceResult>
)

@JsonClass(generateAdapter = true)
data class PlaceResult(
    val geometry: PlaceGeometry,
    val name: String,
    val place_id: String
)

@JsonClass(generateAdapter = true)
data class PlaceGeometry(
    val location: PlaceLocation
)

@JsonClass(generateAdapter = true)
data class PlaceLocation(
    val lat: Double,
    val lng: Double
)
