package com.quran.labs.androidquran.ui.translation

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class TranslationViewInsetsTest {

  @Test
  fun `systemBars insets update horizontal padding and top-bottom spacer offsets non-cumulatively`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val translationView = TranslationView(context)
    val recyclerView = translationView.getChildAt(0) as RecyclerView

    val systemBarsInsets = WindowInsetsCompat.Builder()
      .setInsets(
        WindowInsetsCompat.Type.systemBars(),
        Insets.of(12, 36, 20, 48)
      )
      .build()

    ViewCompat.dispatchApplyWindowInsets(translationView, systemBarsInsets)
    ViewCompat.dispatchApplyWindowInsets(translationView, systemBarsInsets)

    assertThat(translationView.paddingLeft).isEqualTo(12)
    assertThat(translationView.paddingRight).isEqualTo(20)
    assertThat(recyclerView.itemDecorationCount).isEqualTo(1)

    val decoration = recyclerView.getItemDecorationAt(0) as SpacerDecoration
    val state = RecyclerView.State()
    val outRect = Rect()
    // Verify top offset is applied from systemBars() even with no displayCutout
    recyclerView.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
      override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int) =
        object : RecyclerView.ViewHolder(View(parent.context)) {}

      override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
      override fun getItemCount(): Int = 1
    }
    translationView.measure(
      View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY)
    )
    translationView.layout(0, 0, 400, 800)
    val laidOutChild = recyclerView.getChildAt(0)
    assertThat(laidOutChild).isNotNull()
    decoration.getItemOffsets(outRect, laidOutChild, recyclerView, state)
    assertThat(outRect.top).isEqualTo(36)
  }
}
