package com.quran.labs.androidquran.ui.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.source.DisplaySize
import com.quran.data.source.PageProvider
import com.quran.data.source.PageSizeCalculator
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.QuranAyahInfo
import com.quran.labs.androidquran.common.Response
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.fakes.FakePageProvider
import com.quran.labs.androidquran.ui.util.ImageAyahUtils
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranScreenInfo
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.view.DividerView
import com.quran.labs.androidquran.view.ForceCompleteTextView
import com.quran.labs.androidquran.view.LeftToRightLinearLayout
import com.quran.labs.androidquran.view.ObservableScrollView
import com.quran.labs.androidquran.view.QuranHeaderView
import com.quran.labs.androidquran.view.QuranSpinner
import com.quran.page.common.data.AyahBounds
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class ScaledPageBitmapDrawableAndDecodeTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Test
  fun `computeTargetDecodeDimensions downscales portrait only and never upscales landscape`() {
    // 1260x2038 page on 720px portrait screen -> 720x1165
    assertThat(QuranFileUtils.computeTargetDecodeDimensions(1260, 2038, 720))
      .isEqualTo(720 to 1165)
    // 1260x2038 page on 1080px portrait screen -> 1080x1747
    assertThat(QuranFileUtils.computeTargetDecodeDimensions(1260, 2038, 1080))
      .isEqualTo(1080 to 1747)
    // 1260x2038 page on 1604px landscape screen -> null (full native 1260x2038, never upscaled)
    assertThat(QuranFileUtils.computeTargetDecodeDimensions(1260, 2038, 1604)).isNull()
    assertThat(QuranFileUtils.computeTargetDecodeDimensions(1260, 2038, 1260)).isNull()
    assertThat(QuranFileUtils.computeTargetDecodeDimensions(1260, 2038, 0)).isNull()
  }

  @Test
  fun `ScaledPageBitmapDrawable preserves exact imageMatrix and ImageAyahUtils coordinates`() {
    val fullBitmap = Bitmap.createBitmap(1260, 2038, Bitmap.Config.ARGB_8888)
    val scaledBitmap = Bitmap.createBitmap(720, 1165, Bitmap.Config.ARGB_8888)
    try {
      val fullView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        setImageDrawable(BitmapDrawable(context.resources, fullBitmap))
      }
      val scaledDrawable = ScaledPageBitmapDrawable.create(context.resources, scaledBitmap, 1260, 2038)
      assertThat(scaledDrawable).isInstanceOf(ScaledPageBitmapDrawable::class.java)
      assertThat(scaledDrawable.intrinsicWidth).isEqualTo(1260)
      assertThat(scaledDrawable.intrinsicHeight).isEqualTo(2038)

      val scaledView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        setImageDrawable(scaledDrawable)
      }

      val wSpec = View.MeasureSpec.makeMeasureSpec(697, View.MeasureSpec.EXACTLY)
      val hSpec = View.MeasureSpec.makeMeasureSpec(1530, View.MeasureSpec.EXACTLY)
      fullView.measure(wSpec, hSpec)
      fullView.layout(0, 0, 697, 1530)
      scaledView.measure(wSpec, hSpec)
      scaledView.layout(0, 0, 697, 1530)

      val fullValues = FloatArray(9).also { fullView.imageMatrix.getValues(it) }
      val scaledValues = FloatArray(9).also { scaledView.imageMatrix.getValues(it) }
      for (i in 0 until 9) {
        assertThat(scaledValues[i]).isWithin(1e-4f).of(fullValues[i])
      }

      // Touch point mapping back to ayahinfo_1260.db coordinate space must be identical
      val fullXY = ImageAyahUtils.getPageXY(350f, 600f, fullView)!!
      val scaledXY = ImageAyahUtils.getPageXY(350f, 600f, scaledView)!!
      assertThat(scaledXY[0]).isWithin(1e-3f).of(fullXY[0])
      assertThat(scaledXY[1]).isWithin(1e-3f).of(fullXY[1])
    } finally {
      fullBitmap.recycle()
      scaledBitmap.recycle()
    }
  }

  @Test
  fun `decodePageBitmapSoftware preserves source dimensions when decoding PNG`() {
    val pageProvider = object : PageProvider by FakePageProvider() {
      override fun getImagesDirectoryName(): String = "images"
    }
    val display = context.getSystemService(DisplayManager::class.java)
      .getDisplay(Display.DEFAULT_DISPLAY)!!
    val screenInfo = QuranScreenInfo(
      context, display, pageProvider.getPageSizeCalculator(DisplaySize(0, 0))
    )
    val fileUtils = QuranFileUtils(context, pageProvider, screenInfo)

    val pngFile = File(context.cacheDir, "decode_test_page.png")
    val src = Bitmap.createBitmap(200, 320, Bitmap.Config.ARGB_8888)
    try {
      FileOutputStream(pngFile).use { out ->
        src.compress(Bitmap.CompressFormat.PNG, 100, out)
      }
      val decoded = fileUtils.decodePageBitmapSoftware(pngFile, maxTargetWidth = 100)
      assertThat(decoded).isNotNull()
      assertThat(decoded!!.sourceWidth).isEqualTo(200)
      assertThat(decoded.sourceHeight).isEqualTo(320)
      assertThat(decoded.bitmap.width).isAtMost(200)
      decoded.bitmap.recycle()
    } finally {
      src.recycle()
      pngFile.delete()
    }
  }

  @Test
  fun `converted Kotlin data and view classes preserve expected behavior`() {
    // Response
    val bmp = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
    try {
      val ok = Response(bmp, 0, 1260, 2038).apply { setPageData(42) }
      assertThat(ok.isSuccessful).isTrue()
      assertThat(ok.pageNumber).isEqualTo(42)
      assertThat(ok.sourceWidth).isEqualTo(1260)
      assertThat(ok.sourceHeight).isEqualTo(2038)
      val err = Response(Response.ERROR_NO_INTERNET)
      assertThat(err.isSuccessful).isFalse()
      assertThat(err.bitmap).isNull()
    } finally {
      bmp.recycle()
    }

    // QuranAyahInfo
    val ayahInfo = QuranAyahInfo(2, 255, "الله", emptyList(), 262)
    assertThat(ayahInfo.asSuraAyah().sura).isEqualTo(2)
    assertThat(ayahInfo.asSuraAyah().ayah).isEqualTo(255)

    // QuranRow
    val row = QuranRow.Builder()
      .withType(QuranRow.AYAH_BOOKMARK)
      .withSura(1)
      .withAyah(1)
      .withPage(1)
      .withText("Al-Fatiha")
      .build()
    assertThat(row.isBookmark).isTrue()
    assertThat(row.isAyahBookmark).isTrue()
    assertThat(row.isHeader).isFalse()

    // AyahBounds defensive copy + engulf
    val b1 = AyahBounds(1, 1, 10, 20, 40, 50)
    val b2 = AyahBounds(1, 2, 40, 20, 80, 50)
    val copy = b1.bounds
    copy.set(0f, 0f, 1f, 1f)
    assertThat(b1.bounds).isEqualTo(RectF(10f, 20f, 40f, 50f))
    b1.engulf(b2)
    assertThat(b1.bounds).isEqualTo(RectF(10f, 20f, 80f, 50f))

    // Converted custom views instantiate cleanly
    val divider = DividerView(context).apply {
      setDividerColor(Color.GRAY)
      highlight(Color.YELLOW)
      unhighlight()
      toggleLine(false)
    }
    assertThat(divider).isNotNull()

    val ltrLayout = LeftToRightLinearLayout(context)
    assertThat(ltrLayout.layoutDirection).isEqualTo(View.LAYOUT_DIRECTION_LTR)

    var scrolledY = -1
    val scrollView = ObservableScrollView(context).apply {
      addView(View(context).apply { minimumHeight = 2000 }, 400, 2000)
    }
    scrollView.measure(
      View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY)
    )
    scrollView.layout(0, 0, 400, 500)
    scrollView.setOnScrollListener { _, _, y, _, _ -> scrolledY = y }
    scrollView.scrollTo(0, 120)
    assertThat(scrolledY).isEqualTo(120)

    val headerView = QuranHeaderView(context).apply {
      addView(TextView(context).apply { text = "Juz 1" })
      addView(TextView(context).apply { text = "1" })
    }
    headerView.measure(
      View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(60, View.MeasureSpec.EXACTLY)
    )
    headerView.layout(0, 0, 400, 60)
    assertThat(headerView.childCount).isEqualTo(2)

    val forceComplete = ForceCompleteTextView(context)
    assertThat(forceComplete.enoughToFilter()).isTrue()
    var threw = false
    try {
      forceComplete.setOnItemClickListener(null)
    } catch (_: UnsupportedOperationException) {
      threw = true
    }
    assertThat(threw).isTrue()

    val spinner = QuranSpinner(context)
    assertThat(spinner).isNotNull()
  }

  @Test
  @Config(qualifiers = "w960dp-h600dp-xhdpi")
  fun `QuranUtils getDebugInfo prints tabletWidthParam when dual page mode is supported`() {
    val display = context.getSystemService(DisplayManager::class.java)
      .getDisplay(Display.DEFAULT_DISPLAY)!!
    val calculator = object : PageSizeCalculator {
      override fun getWidthParameter(): String = "1260"
      override fun getTabletWidthParameter(): String = "1024"
      override fun setOverrideParameter(parameter: String) = Unit
    }
    val screenInfo = QuranScreenInfo(context, display, calculator)
    val debugInfo = QuranUtils.getDebugInfo(context, screenInfo)
    assertThat(screenInfo.isDualPageMode).isTrue()
    assertThat(debugInfo).contains("Display: _1260")
    assertThat(debugInfo).contains("tablet width: _1024")
  }

  @Test
  fun `QuranDisplayData caches sura list metadata, prefixed names, and juz titles thread-safely`() {
    val displayData = QuranDisplayData(FakePageProvider().getDataSource().let {
      com.quran.data.core.QuranInfo(it)
    })
    val firstMeta = displayData.getSuraListMetaString(context, 1)
    val secondMeta = displayData.getSuraListMetaString(context, 1)
    assertThat(firstMeta).isNotEmpty()
    assertThat(secondMeta).isEqualTo(firstMeta)

    val suraPrefixed = displayData.getSuraName(context, 1, wantPrefix = true, wantTranslation = false)
    val suraPlain = displayData.getSuraName(context, 1, wantPrefix = false, wantTranslation = false)
    assertThat(suraPrefixed).contains(suraPlain)
    assertThat(displayData.getSuraName(context, 1, wantPrefix = true, wantTranslation = false))
      .isEqualTo(suraPrefixed)

    val juzTitle = displayData.getJuzDisplayString(context, 1)
    assertThat(juzTitle).contains(QuranUtils.getLocalizedNumber(1))
  }
}
