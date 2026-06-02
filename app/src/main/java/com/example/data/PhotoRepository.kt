package com.example.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import com.example.network.NetworkClient

class PhotoRepository(
    private val dao: PhotoDao,
    private val context: Context
) {
    val allPhotos: Flow<List<PhotoEntity>> = dao.getAllPhotos()

    suspend fun insert(photo: PhotoEntity) = dao.insertPhoto(photo)
    suspend fun update(photo: PhotoEntity) = dao.updatePhoto(photo)
    suspend fun getById(id: Int) = dao.getPhotoById(id)
    suspend fun delete(id: Int) = dao.deletePhotoById(id)

    suspend fun uploadToNbox(photo: PhotoEntity): Result<String> {
        return try {
            val uri = Uri.parse(photo.uriString)
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw Exception("Could not open file")
            
            val tempFile = File(context.cacheDir, "upload_temp.jpg")
            val outputStream = FileOutputStream(tempFile)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()

            val requestFile = tempFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", tempFile.name, requestFile)

            val response = NetworkClient.nboxApi.uploadPhoto(body)
            tempFile.delete()

            if (response.url != null) {
                Result.success(response.url)
            } else {
                Result.failure(Exception("Upload failed, no URL returned"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
