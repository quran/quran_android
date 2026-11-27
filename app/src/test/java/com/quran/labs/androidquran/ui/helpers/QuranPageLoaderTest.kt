package com.quran.labs.androidquran.ui.helpers

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.source.DisplaySize
import com.quran.data.source.PageProvider
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.fakes.FakePageProvider
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranScreenInfo
import okhttp3.OkHttpClient
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class QuranPageLoaderTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Before
  fun setUp() {
    File(context.filesDir, "quran_android").deleteRecursively()
  }

  @Test
  fun `loadPages emits responses for all requested pages`() {
    val pageProvider = object : PageProvider by FakePageProvider() {
      override fun getImagesDirectoryName(): String = "images"
    }
    val display = context.getSystemService(DisplayManager::class.java)
      .getDisplay(Display.DEFAULT_DISPLAY)!!
    val screenInfo = QuranScreenInfo(
      context, display, pageProvider.getPageSizeCalculator(DisplaySize(0, 0))
    )
    val quranFileUtils = QuranFileUtils(context, pageProvider, screenInfo)
    val width = "_1024"
    val imagesDir = quranFileUtils.getQuranImagesDirectory(width)
    imagesDir.mkdirs()

    for (page in listOf(1, 2)) {
      val bmp = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
      FileOutputStream(File(imagesDir, QuranFileUtils.getPageFileName(page))).use { out ->
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
      }
      bmp.recycle()
    }

    val loader = QuranPageLoader(
      activityContext = context,
      appContext = context,
      okHttpClient = OkHttpClient.Builder().build(),
      imageWidth = width,
      quranScreenInfo = screenInfo,
      quranFileUtils = quranFileUtils,
    )

    val observer = loader.loadPages(arrayOf(1, 2)).test()
    assertThat(observer.await(5, TimeUnit.SECONDS)).isTrue()
    observer.assertNoErrors()
    observer.assertValueCount(2)

    val pages = observer.values().map { it.pageNumber }
    assertThat(pages).containsExactly(1, 2)
    observer.values().forEach { response ->
      assertThat(response.isSuccessful).isTrue()
      assertThat(response.bitmap).isNotNull()
      response.bitmap?.recycle()
    }
  }

  @Test
  @Config(qualifiers = "w960dp-h600dp-xhdpi")
  fun `resolveTargetPageWidth tracks Activity window width and halves in dual-page layout`() {
    Robolectric.buildActivity(Activity::class.java).use { controller ->
      val activity = controller.create().start().resume().get()
      val windowWidth = QuranPageLoader.resolveWindowWidthPx(activity)
      assertThat(windowWidth).isEqualTo(1920)

      assertThat(QuranPageLoader.resolveTargetPageWidth(activity, isDualPageLayout = false))
        .isEqualTo(1920)
      assertThat(QuranPageLoader.resolveTargetPageWidth(activity, isDualPageLayout = true))
        .isEqualTo(960)
    }
  }

  @Test
  @Config(qualifiers = "w540dp-h900dp-xhdpi")
  fun `getImageFromSD respects explicit maxTargetWidth override`() {
    val pageProvider = object : PageProvider by FakePageProvider() {
      override fun getImagesDirectoryName(): String = "images"
    }
    val display = context.getSystemService(DisplayManager::class.java)
      .getDisplay(Display.DEFAULT_DISPLAY)!!
    val screenInfo = QuranScreenInfo(
      context, display, pageProvider.getPageSizeCalculator(DisplaySize(0, 0))
    )
    val quranFileUtils = QuranFileUtils(context, pageProvider, screenInfo)
    val width = "_1260"
    val imagesDir = quranFileUtils.getQuranImagesDirectory(width)
    imagesDir.mkdirs()

    val bmp = Bitmap.createBitmap(200, 320, Bitmap.Config.ARGB_8888)
    val pageName = QuranFileUtils.getPageFileName(1)
    FileOutputStream(File(imagesDir, pageName)).use { out ->
      bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    bmp.recycle()

    val response = quranFileUtils.getImageFromSD(width, pageName, maxTargetWidth = 100)
    assertThat(response.isSuccessful).isTrue()
    assertThat(response.sourceWidth).isEqualTo(200)
    assertThat(response.sourceHeight).isEqualTo(320)
    assertThat(response.bitmap).isNotNull()
    response.bitmap?.recycle()
  }
}
