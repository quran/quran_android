package com.quran.labs.androidquran.view

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.widget.AdapterView
import androidx.appcompat.widget.AppCompatAutoCompleteTextView

/**
 * AutoCompleteTextView that forces to use value from one of the values in adapter (choices).
 */
class ForceCompleteTextView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = androidx.appcompat.R.attr.autoCompleteTextViewStyle
) : AppCompatAutoCompleteTextView(context, attrs, defStyleAttr) {
  /* Thanks to those in http://stackoverflow.com/q/15544943/1197317 for inspiration */

  private var onForceCompleteListener: OnForceCompleteListener? = null

  init {
    super.setOnItemClickListener { _, _, position, id -> onForceComplete(position, id) }
  }

  protected fun onForceComplete(position: Int, rowId: Long) {
    onForceCompleteListener?.onForceComplete(this, position, rowId)
  }

  override fun enoughToFilter(): Boolean {
    // Break the limit of minimum 1
    return true
  }

  override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
    super.onFocusChanged(focused, direction, previouslyFocusedRect)
    if (focused) {
      performFiltering(text, 0)
    } else {
      onForceComplete(AdapterView.INVALID_POSITION, AdapterView.INVALID_ROW_ID)
    }
  }

  /**
   * Sets the listener that will be called to do force completion ([onForceComplete]).
   */
  fun setOnForceCompleteListener(l: OnForceCompleteListener?) {
    this.onForceCompleteListener = l
    post {
      if (!isFocused) {
        onForceComplete(AdapterView.INVALID_POSITION, AdapterView.INVALID_ROW_ID)
      }
    }
  }

  /**
   * Do not call this method, use [setOnForceCompleteListener] instead.
   *
   * @throws UnsupportedOperationException if called
   */
  override fun setOnItemClickListener(l: AdapterView.OnItemClickListener?) {
    throw UnsupportedOperationException("Call setOnForceCompleteListener instead")
  }

  fun interface OnForceCompleteListener {
    /**
     * @param position position the user selects or a negative value if nothing is selected
     * @param rowId corresponding item's ID of the position
     */
    fun onForceComplete(view: ForceCompleteTextView, position: Int, rowId: Long)
  }
}
