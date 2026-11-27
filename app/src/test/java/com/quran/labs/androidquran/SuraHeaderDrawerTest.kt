package com.quran.labs.androidquran

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import com.quran.page.common.data.PageCoordinates
import com.quran.page.common.data.SuraHeaderLocation
import com.quran.page.common.draw.ImageDrawHelper
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class SuraHeaderDrawerTest {

  private class RecordingCanvas : Canvas() {
    val drawnDstRects = mutableListOf<RectF>()

    override fun drawBitmap(bitmap: Bitmap, src: Rect?, dst: RectF, paint: Paint?) {
      drawnDstRects.add(RectF(dst))
    }
  }

  private fun createDrawer(): ImageDrawHelper {
    val clazz = Class.forName("com.quran.labs.androidquran.common.drawing.helper.SuraHeaderDrawer")
    val ctor = clazz.getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
    return ctor.newInstance(R.drawable.ic_ab_search, Color.BLACK) as ImageDrawHelper
  }

  @Test
  fun `draw safely no-ops when ImageView drawable is null`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val imageView = ImageView(context).apply {
      layout(0, 0, 500, 800)
    }
    assertThat(imageView.drawable).isNull()

    val coordinates = PageCoordinates(
      page = 1,
      pageBounds = RectF(0f, 0f, 500f, 800f),
      suraHeaders = listOf(SuraHeaderLocation(sura = 1, x = 0, y = 200, width = 400, height = 40)),
      ayahMarkers = emptyList()
    )
    val canvas = RecordingCanvas()
    val drawer = createDrawer()

    drawer.draw(coordinates, canvas, imageView)

    assertThat(canvas.drawnDstRects).isEmpty()
  }

  @Test
  fun `draw shifts bounds symmetrically by paddingLeft instead of stretching with paddingRight`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val pageBitmap = Bitmap.createBitmap(1000, 1500, Bitmap.Config.ARGB_8888)
    val paddingLeft = 30
    val paddingTop = 20
    val paddingRight = 140
    val paddingBottom = 10

    val imageView = ImageView(context).apply {
      setImageDrawable(BitmapDrawable(context.resources, pageBitmap))
      imageMatrix = Matrix()
      setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
      layout(0, 0, 1170, 1530)
    }

    val coordinates = PageCoordinates(
      page = 2,
      pageBounds = RectF(0f, 0f, 1000f, 1500f),
      suraHeaders = listOf(SuraHeaderLocation(sura = 2, x = 0, y = 300, width = 950, height = 100)),
      ayahMarkers = emptyList()
    )
    val canvas = RecordingCanvas()
    val drawer = createDrawer()

    drawer.draw(coordinates, canvas, imageView)

    assertThat(canvas.drawnDstRects).hasSize(1)
    val drawn = canvas.drawnDstRects.single()
    val intrinsicWidth = 1000f
    val expectedUnpaddedLeft = intrinsicWidth * 0.025f
    val expectedUnpaddedRight = intrinsicWidth - expectedUnpaddedLeft
    val expectedWidth = expectedUnpaddedRight - expectedUnpaddedLeft

    assertThat(drawn.left).isWithin(0.01f).of(expectedUnpaddedLeft + paddingLeft)
    assertThat(drawn.right).isWithin(0.01f).of(expectedUnpaddedRight + paddingLeft)
    assertThat(drawn.width()).isWithin(0.01f).of(expectedWidth)
    assertThat(drawn.top).isWithin(0.01f).of(300f - (intrinsicWidth * 0.1f) / 2f + paddingTop)
  }
}
