package com.quran.labs.androidquran.view

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.widget.SpinnerAdapter
import androidx.appcompat.widget.AppCompatSpinner
import kotlin.math.max
import kotlin.math.min

/**
 * An [AppCompatSpinner] that uses the last items in an adapter and a multiplier to
 * determine the width of the Spinner and its dropdown.
 *
 * AppCompatSpinner uses the measurement of the first 15 items to determine the width.
 */
class QuranSpinner @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = androidx.appcompat.R.attr.spinnerStyle
) : AppCompatSpinner(context, attrs, defStyleAttr) {

  private var spinnerAdapter: SpinnerAdapter? = null

  override fun setAdapter(adapter: SpinnerAdapter?) {
    super.setAdapter(adapter)
    this.spinnerAdapter = adapter
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.AT_MOST) {
      val calculatedWidth = calculateWidth()
      val measuredWidth = measuredWidth
      if (calculatedWidth > measuredWidth) {
        val width = min(calculatedWidth, MeasureSpec.getSize(widthMeasureSpec))
        setMeasuredDimension(width, measuredHeight)
        dropDownWidth = calculatedWidth

        // hack to fix an odd bug with Farsi - see quran/quran_android#849
        // because we get incorrect width for Farsi, set the actual spinner width to the overall
        // desired width. this causes all subsequent children added to use the width of the
        // spinner to measure themselves instead of "wrap_content". Leaving this hack here for
        // non-Farsi languages as well, since it has a nice sub-benefit of causing the Spinner
        // to not change width when the selected item is changed to a longer/shorter item.
        layoutParams?.width = width
      } else {
        dropDownWidth = measuredWidth
      }
    }
  }

  private fun calculateWidth(): Int {
    val currentAdapter = spinnerAdapter ?: return 0

    var width = 0
    var itemView: View? = null
    var itemType = 0
    val widthMeasureSpec = MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.UNSPECIFIED)
    val heightMeasureSpec = MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.UNSPECIFIED)

    // Make sure the number of items we'll measure is capped. If it's a huge data set
    // with wildly varying sizes, oh well.
    val end = currentAdapter.count
    val start = max(end - MAX_ITEMS_MEASURED, 0)
    for (i in start until end) {
      val positionType = currentAdapter.getItemViewType(i)
      if (positionType != itemType) {
        itemType = positionType
        itemView = null
      }
      itemView = currentAdapter.getView(i, itemView, this)
      if (itemView.layoutParams == null) {
        itemView.layoutParams = LayoutParams(
          LayoutParams.WRAP_CONTENT,
          LayoutParams.WRAP_CONTENT
        )
      }
      itemView.measure(widthMeasureSpec, heightMeasureSpec)
      width = max(width, itemView.measuredWidth)
    }

    // make sure to take the background padding into account
    background?.let { drawable ->
      drawable.getPadding(PADDING_RECT)
      width += PADDING_RECT.left + PADDING_RECT.right
    }
    width = (width * WIDTH_MULTIPLIER).toInt() // add some extra spacing
    return width
  }

  companion object {
    private const val MAX_ITEMS_MEASURED = 15
    private const val WIDTH_MULTIPLIER = 1.1f
    private val PADDING_RECT = Rect()
  }
}
