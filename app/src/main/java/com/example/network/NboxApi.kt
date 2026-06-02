package com.example.network

import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

// This is a minimal API interface for nbox.me
interface NboxApi {
    @Multipart
    @POST("upload") // Replacing with typical upload endpoint. We don't know the exact endpoint for nbox.me.
    suspend fun uploadPhoto(
        @Part file: MultipartBody.Part
    ): UploadResponse
}

data class UploadResponse(
    val url: String?,
    val success: Boolean?
)
