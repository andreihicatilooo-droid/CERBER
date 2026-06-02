package com.example.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy

class UploaderNetworkError(message: String, cause: Throwable? = null) : Exception(message, cause)
class UploaderParseError(message: String, cause: Throwable? = null) : Exception(message, cause)

data class NboxUploadResult(
    val filename: String,
    val directUrl: String?,
    val deleteUrl: String?,
    val thumbUrl: String?
)

class NboxUploaderService(
    private val proxyUrl: String? = null,
    private val customHeaders: Map<String, String> = emptyMap()
) {
    private val client: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
        if (proxyUrl != null) {
            val parts = proxyUrl.split(":")
            if (parts.size == 2) {
                builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(parts[0], parts[1].toInt())))
            }
        }
        builder.build()
    }

    suspend fun uploadFilesBatched(files: List<File>): List<NboxUploadResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<NboxUploadResult>()
        val chunked = files.chunked(20)

        for (batch in chunked) {
            val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            for (file in batch) {
                // В Kotlin нет простой встроенной библиотеки mimetypes как в Python,
                // поэтому используем простой подход или URLConnection.guessContentTypeFromName
                val mimeType = java.net.URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"
                val reqFile = file.asRequestBody(mimeType.toMediaTypeOrNull())
                builder.addFormDataPart("files[]", file.name, reqFile)
            }

            val requestBody = builder.build()
            val requestBuilder = Request.Builder()
                .url("https://nbox.me/put")
                .post(requestBody)

            customHeaders.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            val request = requestBuilder.build()

            try {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw UploaderNetworkError("HTTP error: ${response.code}")
                }

                val html = response.body?.string() ?: throw UploaderParseError("Empty response")
                val parsedResults = parseHtml(html, batch.map { it.name })
                results.addAll(parsedResults)
            } catch (e: Exception) {
                if (e !is UploaderNetworkError && e !is UploaderParseError) {
                    throw UploaderNetworkError("Network request failed", e)
                }
                throw e
            }
        }

        return@withContext results
    }

    private fun parseHtml(html: String, filenames: List<String>): List<NboxUploadResult> {
        // Простой парсинг HTML с помощью регулярных выражений без внешних библиотек
        val iRegex = """(https://nbox\.me/i/[a-zA-Z0-9_\-.]+)""".toRegex()
        val delRegex = """(https://nbox\.me/delete/[a-zA-Z0-9_\-]+)""".toRegex()
        val thumbRegex = """(https://nbox\.me/t/[a-zA-Z0-9_\-.]+)""".toRegex()

        val iMatches = iRegex.findAll(html).map { it.value }.toList()
        val delMatches = delRegex.findAll(html).map { it.value }.toList()
        val thumbMatches = thumbRegex.findAll(html).map { it.value }.toList()

        val results = mutableListOf<NboxUploadResult>()
        for (i in filenames.indices) {
            results.add(
                NboxUploadResult(
                    filename = filenames[i],
                    directUrl = iMatches.getOrNull(i),
                    deleteUrl = delMatches.getOrNull(i),
                    thumbUrl = thumbMatches.getOrNull(i)
                )
            )
        }
        return results
    }
}
