package com.quran.labs.androidquran.widgets;

import android.content.Context;
import android.graphics.Rect;
import android.support.v7.widget.AppCompatAutoCompleteTextView;
import android.util.AttributeSet;
import android.widget.AdapterView;

/**
 * AutoCompleteTextView that forces to use value from one of the values in adapter (choices).
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
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();

    // TODO create relevant listener name, such as onSelectChoice
    AdapterView.OnItemClickListener listener = getOnItemClickListener();
    if (listener != null)
      listener.onItemClick(null, null, -1, -1);
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
      // TODO create relevant listener name, such as onSelectChoice
      AdapterView.OnItemClickListener listener = getOnItemClickListener();
      if (listener != null)
        listener.onItemClick(null, null, -1, -1);
    }
  }

  /**
   * Sets the listener that will be notified when the user clicks an item in the drop down list,
   * user leaves without clicking, or when this view is attached to window. The two latter cases you
   * use to force the completion, the listener will be called with position argument set to -1 and
   * view argument set to null.
   *
   * @param l the item click listener
   */
  @Override
  public void setOnItemClickListener(AdapterView.OnItemClickListener l) {
    super.setOnItemClickListener(l);
  }
}
