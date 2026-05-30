package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ImageUtilsRobolectricTest {

  @Test
  fun `createTempFileUri returns jpg file and file-provider uri`() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    val (file, uri) = ImageUtils.createTempFileUri(context)

    assertTrue(file.exists())
    assertTrue(file.name.startsWith("JPEG_"))
    assertTrue(file.name.endsWith(".jpg"))
    assertEquals("${context.packageName}.provider", uri.authority)
    file.delete()
  }

  @Test
  fun `addWatermarkAndSave returns null when source image is unreadable`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val missingFile = File(context.externalCacheDir, "missing-image-${System.nanoTime()}.jpg")

    val result = ImageUtils.addWatermarkAndSave(context, missingFile, null)

    assertNull(result)
  }

  @Test
  fun `addWatermarkAndSave persists a decodable image without location`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val source = createSourceImage(context)

    val resultUri = ImageUtils.addWatermarkAndSave(context, source, null)

    assertNotNull(resultUri)
    val savedBitmap = resultUri?.let { uri ->
      context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input)
      }
    }
    assertNotNull(savedBitmap)
    resultUri?.let { context.contentResolver.delete(it, null, null) }
    source.delete()
  }

  @Test
  fun `addWatermarkAndSave persists a decodable image with location metadata`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val source = createSourceImage(context)
    val location = Location("test").apply {
      latitude = 37.7749
      longitude = -122.4194
      accuracy = 4.2f
    }

    val resultUri = ImageUtils.addWatermarkAndSave(context, source, location)

    assertNotNull(resultUri)
    val savedBitmap = resultUri?.let { uri ->
      context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input)
      }
    }
    assertNotNull(savedBitmap)
    resultUri?.let { context.contentResolver.delete(it, null, null) }
    source.delete()
  }

  private fun createSourceImage(context: Context): File {
    val source = File(context.externalCacheDir, "source-${System.nanoTime()}.jpg")
    val bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(android.graphics.Color.BLUE)
    FileOutputStream(source).use { out ->
      bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
    }
    bitmap.recycle()
    return source
  }
}
