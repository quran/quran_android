package com.quran.labs.androidquran.widgets;

import android.content.Context;
import android.graphics.Rect;
import android.support.v7.widget.AppCompatAutoCompleteTextView;
import android.util.AttributeSet;
import android.widget.AdapterView;
import android.widget.ListAdapter;

/**
 * AutoCompleteTextView that forces show the suggestion when focused and forces choose when
 * unfocused.
 */
public class ForceCompleteTextView extends AppCompatAutoCompleteTextView {
  /** Thanks to those in http://stackoverflow.com/q/15544943/1197317 for inspiration */

  private boolean allowOnItemClickWithNull = false;

  public ForceCompleteTextView(Context context) {
    super(context);
  }

  public ForceCompleteTextView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public ForceCompleteTextView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  /**
   * Just like {@link #setOnItemClickListener(AdapterView.OnItemClickListener)}, but accepting null
   * also causes the listener to be called when suggestion is auto-chosen (force choose), e.g. when
   * user leaves without choosing.
   * @param acceptNull whether the listener accept null to be passed as first two arguments
   * @param l the listener
   */
  public void setOnItemClickListener(boolean acceptNull, AdapterView.OnItemClickListener l) {
    setOnItemClickListener(l);
    allowOnItemClickWithNull = acceptNull;
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

      AdapterView.OnItemClickListener listener = getOnItemClickListener();
      if (allowOnItemClickWithNull && listener != null) {
        listener.onItemClick(null, null, 0, 0);
      }
    }
  }

}
