package com.quran.labs.androidquran.extra.feature.linebyline.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import com.google.common.truth.Truth.assertThat
import com.quran.data.source.PageContentType
import com.quran.labs.androidquran.extra.feature.linebyline.model.EmptyPageInfo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.NumberFormat
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class QuranLineByLineCanvasViewAdversarialTest {

  @Test
  fun `QuranLineByLineCanvasView survives zero dimensions, null or recycled bitmap, rebindPage, and detach`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val container = FrameLayout(activity)
    activity.setContentView(container)

    val view = QuranLineByLineCanvasView(
      context = activity,
      pageNumber = 1,
      pageContentType = PageContentType.Line(ratio = 0.1f, lineHeight = 120, allowOverlapOfLines = false),
      isScrollable = false,
      ayahNumberFormatter = NumberFormat.getIntegerInstance(Locale.US),
      onClick = {},
      onPagePositioned = { _, _, _, _ -> },
      onSelectionStart = { _, _ -> },
      onSelectionModified = { _, _ -> },
      onSelectionEnd = {}
    )
    container.addView(view)

    // Zero-size measure/draw must not divide by zero or throw
    view.measure(
      View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.EXACTLY)
    )
    view.layout(0, 0, 0, 0)
    val dummyCanvas = Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
    view.draw(dummyCanvas)

    // Standard portrait measure + recycled bitmap guard
    view.measure(
      View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY)
    )
    view.layout(0, 0, 720, 1280)
    val pageBmp = Bitmap.createBitmap(360, 640, Bitmap.Config.ARGB_8888)
    view.setPageInfo(EmptyPageInfo.copy(page = 1, compositedPageBitmap = pageBmp))
    view.draw(Canvas(Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)))

    pageBmp.recycle()
    // Drawing with recycled bitmap still attached must not throw
    view.draw(Canvas(Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)))

    view.clearCompositedBitmap()
    view.rebindPage(604)
    view.clearPageInfo()
    view.draw(Canvas(Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)))

    container.removeView(view)
    assertThat(container.childCount).isEqualTo(0)
  }
}
