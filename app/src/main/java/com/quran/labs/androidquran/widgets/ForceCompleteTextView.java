package com.quran.labs.androidquran.widgets;

import android.content.Context;
import android.graphics.Rect;
import android.support.v7.widget.AppCompatAutoCompleteTextView;
import android.util.AttributeSet;
import android.widget.AdapterView;
import android.widget.ListAdapter;

/**
 * AutoCompleteTextView that forces show the suggestion when focused and forces choose the
 * suggestion when unfocused. Force choose is done by calling the listener set by {@link
 * #setOnItemClickListener(AdapterView.OnItemClickListener)} with null for the first two arguments,
 * if there is at least one suggestion appears then the position would be 0 and the text will be the
 * first item appears, otherwise the position would be -1 and the text will be empty, the rest is
 * left to the listener.
 */
public class ForceCompleteTextView extends AppCompatAutoCompleteTextView {
  /* Thanks to those in http://stackoverflow.com/q/15544943/1197317 for inspiration */

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
    } else {
      ListAdapter adapter = getAdapter();
      AdapterView.OnItemClickListener listener = getOnItemClickListener();
      if (adapter.isEmpty()) {
        setText(null);
        if (listener != null)
          listener.onItemClick(null, null, -1, -1);
      } else {
        Object value = adapter.getItem(0);
        setText(value == null ? null : value.toString());
        if (listener != null)
          listener.onItemClick(null, null, 0, 0);
      }
    }
  }

}
