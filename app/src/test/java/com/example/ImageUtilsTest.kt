package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ImageUtilsTest {

  @Test
  fun createTempFileUri_createsCacheBackedFileAndContentUri() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    val (file, uri) = ImageUtils.createTempFileUri(context)

    assertTrue(file.exists())
    assertTrue(file.parentFile?.absolutePath == context.externalCacheDir?.absolutePath)
    assertTrue(uri.toString().contains("${context.packageName}.provider"))
  }

  @Test
  fun addWatermarkAndSave_returnsNull_whenImageCannotBeDecoded() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val invalidImage = File(context.cacheDir, "not-an-image.jpg").apply {
      writeText("not an image")
    }

    val result = ImageUtils.addWatermarkAndSave(context, invalidImage, location = null)

    assertTrue(result == null)
  }

  @Test
  fun addWatermarkAndSave_savesImage_whenLocationIsNull() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val imageFile = createJpegFixture(context)

    val result = ImageUtils.addWatermarkAndSave(context, imageFile, location = null)

    assertNotNull(result)
    assertTrue(result.toString().startsWith("content://"))
  }

  @Test
  fun addWatermarkAndSave_savesImage_whenLocationIsPresent() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val imageFile = createJpegFixture(context)
    val location =
      Location("gps").apply {
        latitude = 51.5074
        longitude = -0.1278
        accuracy = 3.5f
      }

    val result = ImageUtils.addWatermarkAndSave(context, imageFile, location)

    assertNotNull(result)
    assertTrue(result.toString().startsWith("content://"))
  }

  private fun createJpegFixture(context: Context): File {
    val fixture = File(context.cacheDir, "fixture-${System.nanoTime()}.jpg")
    val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.RED)
    FileOutputStream(fixture).use { output ->
      bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output)
    }
    return fixture
  }
}
