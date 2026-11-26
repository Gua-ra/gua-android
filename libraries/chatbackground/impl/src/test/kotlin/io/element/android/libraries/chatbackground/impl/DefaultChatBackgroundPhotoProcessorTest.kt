/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbackground.impl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import io.element.android.tests.testutils.robolectric.RobolectricTest
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DefaultChatBackgroundPhotoProcessorTest : RobolectricTest() {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun `a large photo is turned upright, capped at 2048 px and loses all its metadata`() = runTest {
        val source = writeJpeg(width = 4000, height = 3000) { file ->
            ExifInterface(file).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                setLatLong(-23.5505, -46.6333)
                setAttribute(ExifInterface.TAG_MAKE, "Phone maker")
                setAttribute(ExifInterface.TAG_MODEL, "Phone model")
                setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:11:03 10:00:00")
                saveAttributes()
            }
        }

        val upload = createProcessor().process(Uri.fromFile(source)).getOrThrow()

        assertThat(upload.mimetype).isEqualTo("image/jpeg")
        assertThat(upload.width to upload.height).isEqualTo(1536 to 2048)
        assertThat(upload.data.take(2)).containsExactly(0xFF.toByte(), 0xD8.toByte()).inOrder()
        assertThat(String(upload.data, Charsets.ISO_8859_1)).doesNotContain("Exif\u0000\u0000")
        val exif = ExifInterface(upload.data.inputStream())
        assertThat(exif.latLong).isNull()
        assertThat(exif.getAttribute(ExifInterface.TAG_MAKE)).isNull()
        assertThat(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)).isNull()
        val decoded = BitmapFactory.decodeByteArray(upload.data, 0, upload.data.size)
        assertThat(decoded.width to decoded.height).isEqualTo(1536 to 2048)
        assertThat(upload.blurhash).isNotEmpty()
    }

    @Test
    fun `a small photo is not upscaled and transparency becomes black`() = runTest {
        val bitmap = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.TRANSPARENT) }
        val source = File(context.cacheDir, "transparent.png").apply { outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }

        val upload = createProcessor().process(Uri.fromFile(source)).getOrThrow()

        assertThat(upload.width to upload.height).isEqualTo(300 to 200)
        val pixel = BitmapFactory.decodeByteArray(upload.data, 0, upload.data.size).getPixel(150, 100)
        assertThat(Color.red(pixel)).isLessThan(8)
        assertThat(Color.green(pixel)).isLessThan(8)
        assertThat(Color.blue(pixel)).isLessThan(8)
    }

    @Test
    fun `a photo still over 1_5 MB is encoded again at 1600 px`() = runTest {
        val source = writeJpeg(width = 2400, height = 2400, noise = true)

        val upload = createProcessor().process(Uri.fromFile(source)).getOrThrow()

        assertThat(upload.width to upload.height).isEqualTo(1600 to 1600)
    }

    @Test
    fun `a file that is not an image fails`() = runTest {
        val source = File(context.cacheDir, "not-an-image.jpg").apply { writeText("hello") }

        assertThat(createProcessor().process(Uri.fromFile(source)).isFailure).isTrue()
    }

    private fun TestScope.createProcessor() = DefaultChatBackgroundPhotoProcessor(context, testCoroutineDispatchers())

    private fun writeJpeg(width: Int, height: Int, noise: Boolean = false, editExif: (File) -> Unit = {}): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (noise) {
            val random = Random(42)
            val pixels = IntArray(width * height) { random.nextInt() or 0xFF000000.toInt() }
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        } else {
            bitmap.eraseColor(Color.rgb(30, 160, 90))
        }
        val file = File(context.cacheDir, "source-${width}x$height.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        editExif(file)
        return file
    }
}
