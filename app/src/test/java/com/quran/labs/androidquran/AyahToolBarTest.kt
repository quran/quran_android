package com.quran.labs.androidquran

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.selection.SelectionIndicator
import com.quran.data.model.selection.SelectionRectangle
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.toolbar.R as ToolbarR
import com.quran.page.common.toolbar.AyahToolBar
import com.quran.page.common.toolbar.AyahToolBarPip
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class AyahToolBarTest {

  private fun createLaidOutToolBar(parentWidth: Int = 1080, parentHeight: Int = 1920): Pair<FrameLayout, AyahToolBar> {
    val baseContext = ApplicationProvider.getApplicationContext<Context>()
    val themedContext = ContextThemeWrapper(baseContext, androidx.appcompat.R.style.Theme_AppCompat_Light)
    val parent = FrameLayout(themedContext)
    val toolBar = AyahToolBar(themedContext)
    parent.addView(
      toolBar,
      FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      )
    )
    parent.measure(
      View.MeasureSpec.makeMeasureSpec(parentWidth, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(parentHeight, View.MeasureSpec.EXACTLY)
    )
    parent.layout(0, 0, parentWidth, parentHeight)
    return parent to toolBar
  }

  @Test
  fun `visible menu items populate contentDescription from item title`() {
    val (_, toolBar) = createLaidOutToolBar()
    val menuLayout = toolBar.getChildAt(0) as LinearLayout

    // cab_recite_from_here has android:visible="false" in ayah_menu.xml, leaving 4 visible items
    assertThat(menuLayout.childCount).isEqualTo(4)

    val expectedTitles = listOf(
      toolBar.context.getString(ToolbarR.string.bookmark_ayah),
      toolBar.context.getString(ToolbarR.string.share_ayah_text),
      toolBar.context.getString(ToolbarR.string.translation_ayah),
      toolBar.context.getString(ToolbarR.string.play_from_here)
    )

    val actualDescriptions = (0 until menuLayout.childCount).map { index ->
      val child = menuLayout.getChildAt(index)
      assertThat(child).isInstanceOf(ImageButton::class.java)
      child.contentDescription?.toString()
    }

    assertThat(actualDescriptions).containsExactlyElementsIn(expectedTitles).inOrder()
  }

  @Test
  fun `toolBarWidth counts only visible items when positioning toolbar`() {
    val (parent, toolBar) = createLaidOutToolBar(parentWidth = 1080, parentHeight = 1920)
    val itemWidth = toolBar.resources.getDimensionPixelSize(ToolbarR.dimen.toolbar_item_width)
    val menuLayout = toolBar.getChildAt(0) as LinearLayout

    // Center selection at x = 500 -> expected x = 500 - (visibleCount * itemWidth) / 2
    val selection = SelectionIndicator.SelectedItemPosition(
      firstItem = SelectionRectangle(left = 490f, top = 600f, right = 510f, bottom = 640f),
      lastItem = SelectionRectangle(left = 490f, top = 600f, right = 510f, bottom = 640f)
    )

    toolBar.onSelectionChanged(selection, reset = true)
    assertThat(menuLayout.childCount).isEqualTo(4)
    val expectedWidth4 = 4 * itemWidth
    assertThat(toolBar.translationX).isWithin(0.01f).of(500f - expectedWidth4 / 2f)

    // Hide one more visible item; toolBarWidth must shrink to 3 * itemWidth
    toolBar.setMenuItemVisibility(ToolbarR.id.cab_translate_ayah, false)
    toolBar.onSelectionChanged(selection, reset = false)
    assertThat(menuLayout.childCount).isEqualTo(3)
    val expectedWidth3 = 3 * itemWidth
    assertThat(toolBar.translationX).isWithin(0.01f).of(500f - expectedWidth3 / 2f)

    // Re-measure parent to confirm measured width matches 3 visible items
    parent.measure(
      View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
    )
    parent.layout(0, 0, 1080, 1920)
    assertThat(toolBar.measuredWidth).isEqualTo(expectedWidth3)
  }

  @Test
  fun `pipLeft clamps to non-negative and within toolbar bounds on layout`() {
    val (parent, toolBar) = createLaidOutToolBar(parentWidth = 1080, parentHeight = 1920)
    val pip = toolBar.getChildAt(1) as AyahToolBarPip

    // Produce a negative pipOffset via inverted rectangle (left > right -> midpoint - left < 0)
    val negativePipSelection = SelectionIndicator.SelectedItemPosition(
      firstItem = SelectionRectangle(left = 20f, top = 600f, right = 10f, bottom = 640f),
      lastItem = SelectionRectangle(left = 20f, top = 600f, right = 10f, bottom = 640f)
    )
    toolBar.onSelectionChanged(negativePipSelection, reset = true)
    parent.measure(
      View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
    )
    parent.layout(0, 0, 1080, 1920)

    assertThat(pip.left).isAtLeast(0)
    assertThat(pip.right).isAtMost(toolBar.measuredWidth)

    // Produce an oversized pipOffset near the right edge of parent
    val rightEdgeSelection = SelectionIndicator.SelectedItemPosition(
      firstItem = SelectionRectangle(left = 1070f, top = 600f, right = 1080f, bottom = 640f),
      lastItem = SelectionRectangle(left = 1070f, top = 600f, right = 1080f, bottom = 640f)
    )
    toolBar.onSelectionChanged(rightEdgeSelection, reset = false)
    parent.measure(
      View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
    )
    parent.layout(0, 0, 1080, 1920)

    assertThat(pip.left).isAtLeast(0)
    assertThat(pip.left).isAtMost((toolBar.measuredWidth - pip.measuredWidth).coerceAtLeast(0))
  }
}
