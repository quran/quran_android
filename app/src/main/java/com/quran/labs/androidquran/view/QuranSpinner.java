package com.quran.labs.androidquran.view;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import androidx.appcompat.widget.AppCompatSpinner;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SpinnerAdapter;

/**
 * An {@link AppCompatSpinner} that uses the last items in an adapter and a multiplier to
 * determine the width of the Spinner and its dropdown.
 *
 * AppCompatSpinner uses the measurement of the first 15 items to determine the width.
 */
public class QuranSpinner extends AppCompatSpinner {
  private static final int MAX_ITEMS_MEASURED = 15;
  private static final float WIDTH_MULTIPLIER = 1.1f;
  private static final Rect PADDING_RECT = new Rect();

  private SpinnerAdapter adapter;

  public QuranSpinner(Context context) {
    super(context);
  }

  public QuranSpinner(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public QuranSpinner(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  public void setAdapter(SpinnerAdapter adapter) {
    super.setAdapter(adapter);
    this.adapter = adapter;
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.AT_MOST) {
      int calculatedWidth = calculateWidth();
      int measuredWidth = getMeasuredWidth();
      if (calculatedWidth > measuredWidth) {
        int width = Math.min(calculatedWidth, MeasureSpec.getSize(widthMeasureSpec));
        setMeasuredDimension(width, getMeasuredHeight());
        setDropDownWidth(calculatedWidth);

        // hack to fix an odd bug with Farsi - see quran/quran_android#849
        // because we get incorrect width for Farsi, set the actual spinner width to the overall
        // desired width. this causes all subsequent children added to use the width of the
        // spinner to measure themselves instead of "wrap_content". Leaving this hack here for
        // non-Farsi languages as well, since it has a nice sub-benefit of causing the Spinner
        // to not change width when the selected item is changed to a longer/shorter item.
        getLayoutParams().width = width;
      } else {
        setDropDownWidth(measuredWidth);
      }
    }
  }

  private int calculateWidth() {
    if (adapter == null) {
      return 0;
    }

    int width = 0;
    View itemView = null;
    int itemType = 0;
    final int widthMeasureSpec =
        MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.UNSPECIFIED);
    final int heightMeasureSpec =
        MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.UNSPECIFIED);

    // Make sure the number of items we'll measure is capped. If it's a huge data set
    // with wildly varying sizes, oh well.
    final int end = adapter.getCount();
    int start = Math.max(end - MAX_ITEMS_MEASURED, 0);
    for (int i = start; i < end; i++) {
      final int positionType = adapter.getItemViewType(i);
      if (positionType != itemType) {
        itemType = positionType;
        itemView = null;
      }
      itemView = adapter.getView(i, itemView, this);
      if (itemView.getLayoutParams() == null) {
        itemView.setLayoutParams(new LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT));
      }
      itemView.measure(widthMeasureSpec, heightMeasureSpec);
      width = Math.max(width, itemView.getMeasuredWidth());
    }

    // make sure to take the background padding into account
    Drawable drawable = getBackground();
    if (drawable != null) {
      drawable.getPadding(PADDING_RECT);
      width += PADDING_RECT.left + PADDING_RECT.right;
    }
    width = (int) ( width * WIDTH_MULTIPLIER); // add some extra spacing
    return width;
  }
}
