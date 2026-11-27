package com.quran.labs.androidquran.view

import android.app.Activity
import android.widget.FrameLayout
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.ui.helpers.HighlightTypes
import com.quran.page.common.data.AyahBounds
import com.quran.page.common.data.AyahCoordinates
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class HighlightingImageViewTest {

  @Test
  fun `detaching view cancels running audio transition animator`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    val container = FrameLayout(activity)
    activity.setContentView(container)

    val imageView = HighlightingImageView(activity)
    container.addView(imageView)

    imageView.setAyahData(
      AyahCoordinates(
        page = 1,
        ayahCoordinates = mapOf(
          "1:1" to listOf(AyahBounds(1, 0, 10, 10, 80, 30)),
          "1:2" to listOf(AyahBounds(2, 0, 10, 40, 80, 60))
        ),
        glyphCoordinates = null
      )
    )

    imageView.highlightAyah(1, 1, -1, HighlightTypes.AUDIO)
    imageView.highlightAyah(1, 2, -1, HighlightTypes.AUDIO)

    val animator = imageView.animator
    assertThat(animator).isNotNull()
    assertThat(animator!!.isStarted || animator.isRunning).isTrue()

    container.removeView(imageView)

    assertThat(animator.isRunning).isFalse()
    assertThat(animator.isStarted).isFalse()
    assertThat(imageView.animator).isNull()
  }
}
