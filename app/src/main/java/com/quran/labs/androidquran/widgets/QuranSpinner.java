package com.quran.labs.androidquran.widgets;

import android.content.Context;
import android.support.v7.widget.AppCompatSpinner;
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
        setMeasuredDimension(
            Math.min(calculatedWidth, MeasureSpec.getSize(widthMeasureSpec)),
            getMeasuredHeight());
        setDropDownWidth(calculatedWidth);
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
    width *= WIDTH_MULTIPLIER; // add some extra spacing
    return width;
  }
}
