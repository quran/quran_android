package com.quran.labs.androidquran.widgets;

import android.content.Context;
import android.graphics.Rect;
import android.support.v7.widget.AppCompatAutoCompleteTextView;
import android.util.AttributeSet;
import android.widget.ListAdapter;

/**
 * AutoCompleteTextView that forces show the suggestion when focused and forces choose when
 * unfocused
 */
public class ForceCompleteTextView extends AppCompatAutoCompleteTextView {
  /** Thanks to those in http://stackoverflow.com/q/15544943/1197317 for inspiration */

  public ForceCompleteTextView(Context context) {
    super(context);
  }

  public ForceCompleteTextView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public ForceCompleteTextView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  public boolean enoughToFilter() {
    // Break the limit of minimum 1
    return true;
  }

  @Override
  protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
    super.onFocusChanged(focused, direction, previouslyFocusedRect);
    if (focused) {
      performFiltering(getText(), 0);
      showDropDown();
    } else {
      ListAdapter adapter = getAdapter();
      Object value = adapter.isEmpty() ? null : adapter.getItem(0);
      setText(value == null ? null : value.toString());
    }
  }

}
