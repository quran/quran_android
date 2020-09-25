package com.quran.labs.androidquran.view;

import android.content.Context;
import android.graphics.Rect;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatAutoCompleteTextView;
import android.util.AttributeSet;
import android.widget.AdapterView;

/**
 * AutoCompleteTextView that forces to use value from one of the values in adapter (choices).
 */
public class ForceCompleteTextView extends AppCompatAutoCompleteTextView {
  /* Thanks to those in http://stackoverflow.com/q/15544943/1197317 for inspiration */

  private @Nullable OnForceCompleteListener onForceCompleteListener;

  public ForceCompleteTextView(Context context) {
    super(context);
    init();
  }

  public ForceCompleteTextView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public ForceCompleteTextView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init() {
    super.setOnItemClickListener((parent, view, position, id) -> onForceComplete(position, id));
  }

  protected void onForceComplete(int position, long rowId) {
    if (onForceCompleteListener != null) {
      onForceCompleteListener.onForceComplete(this, position, rowId);
    }
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
      onForceComplete(AdapterView.INVALID_POSITION, AdapterView.INVALID_ROW_ID);
    }
  }

  /**
   * Sets the listener that will be called to do force completion ({@link #onForceComplete(int,
   * long)}).
   */
  public void setOnForceCompleteListener(@Nullable OnForceCompleteListener l) {
    this.onForceCompleteListener = l;
    post(() -> {
      if (!isFocused()) {
        onForceComplete(AdapterView.INVALID_POSITION, AdapterView.INVALID_ROW_ID);
      }
    });
  }

  /**
   * Do not call this method, use {@link #setOnForceCompleteListener(OnForceCompleteListener)}
   * instead.
   *
   * @throws UnsupportedOperationException if called
   */
  @Override
  public void setOnItemClickListener(AdapterView.OnItemClickListener l) {
    throw new UnsupportedOperationException("Call setOnForceCompleteListener instead");
  }

  public interface OnForceCompleteListener {
    /**
     * @param position position the user selects or a negative value if nothing is selected
     * @param rowId    corresponding item's ID of the position
     */
    void onForceComplete(ForceCompleteTextView view, int position, long rowId);
  }
}
