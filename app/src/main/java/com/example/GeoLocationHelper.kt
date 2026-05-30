package com.example

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GeoLocationHelper(context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun getLocationFlow(): Flow<Location?> = callbackFlow {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
            .setMinUpdateIntervalMillis(1000)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                trySend(result.lastLocation)
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        ).addOnFailureListener {
            trySend(null)
        }

        awaitClose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
}

object ImageUtils {
    fun createTempFileUri(context: Context): Pair<File, Uri> {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val imageFileName = "JPEG_" + timeStamp + "_"
        val storageDir = context.externalCacheDir
        val imageFile = File.createTempFile(
            imageFileName,
            ".jpg",
            storageDir
        )
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            imageFile
        )
        return Pair(imageFile, uri)
    }

    fun addWatermarkAndSave(context: Context, imageFile: File, location: Location?): Uri? {
        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath) ?: return null

        val resultBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)
        
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = bitmap.width * 0.03f
            setShadowLayer(5f, 0f, 0f, Color.BLACK)
        }
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#44000000")
        }

        val timeString = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val latString = location?.let { "Lat: ${String.format("%.4f", it.latitude)}" } ?: "Lat: N/A"
        val lngString = location?.let { "Lng: ${String.format("%.4f", it.longitude)}" } ?: "Lng: N/A"
        val accString = location?.let { "Accuracy: ${String.format("%.1f", it.accuracy)}m" } ?: "Accuracy: N/A"

        val lines = listOf(timeString, latString, lngString, accString)
        
        val left = bitmap.width * 0.05f
        var top = bitmap.height * 0.95f - (paint.textSize * lines.size)
        
        // Draw background
        var maxWidth = 0f
        for (line in lines) {
            val width = paint.measureText(line)
            if (width > maxWidth) {
                maxWidth = width
            }
        }
        val padding = 20f
        canvas.drawRect(
            left - padding, 
            top - paint.textSize - padding, 
            left + maxWidth + padding, 
            top + (paint.textSize * (lines.size - 1)) + padding, 
            bgPaint
        )

        for (line in lines) {
            canvas.drawText(line, left, top, paint)
            top += paint.textSize + 10f
        }

        return saveToMediaStore(context, resultBitmap)
    }

    private fun saveToMediaStore(context: Context, bitmap: Bitmap): Uri? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val name = "GeoCam_$timeStamp.jpg"
        
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/GeoCam")
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val uri = context.contentResolver.insert(collection, values) ?: return null
        
        try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }
            return uri
        } catch (e: Exception) {
            context.contentResolver.delete(uri, null, null)
            return null
        }
    }
}
