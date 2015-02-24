package com.quran.labs.androidquran.widgets;

import com.quran.labs.androidquran.R;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.InputDevice;
import android.view.MotionEvent;

public class QuranRecyclerView extends RecyclerView {
  private Context mContext;
  private float mVerticalScrollFactor;

  public QuranRecyclerView(Context context) {
    super(context);
    mContext = context;
  }

  public QuranRecyclerView(Context context, AttributeSet attrs) {
    super(context, attrs);
    mContext = context;
  }

  public QuranRecyclerView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    mContext = context;
  }

  private float getVerticalScrollFactor() {
    if (mVerticalScrollFactor == 0) {
      TypedValue outValue = new TypedValue();
      if (!mContext.getTheme().resolveAttribute(
          R.attr.listPreferredItemHeight, outValue, true)) {
        throw new IllegalStateException(
            "Expected theme to define listPreferredItemHeight.");
      }
      mVerticalScrollFactor = outValue.getDimension(
          mContext.getResources().getDisplayMetrics());
    }
    return mVerticalScrollFactor;
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
  @Override
  public boolean onGenericMotionEvent(MotionEvent event) {
    if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0) {
      if (event.getAction() == MotionEvent.ACTION_SCROLL &&
          getScrollState() == SCROLL_STATE_IDLE) {
        final float vscroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
        if (vscroll != 0) {
          final int delta = -1 * (int) (vscroll * getVerticalScrollFactor());
          if (ViewCompat.canScrollVertically(this, delta > 0 ? 1 : -1)) {
            scrollBy(0, delta);
            return true;
          }
        }
      }
    }
    return super.onGenericMotionEvent(event);
  }
}
