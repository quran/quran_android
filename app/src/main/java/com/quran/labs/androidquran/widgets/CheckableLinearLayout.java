package com.quran.labs.androidquran.widgets;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.Checkable;
import android.widget.LinearLayout;

/**
 * the intention behind this class is to provide backward compatibility for
 * the contextual action bar mode (as used by tags and bookmarks). if we
 * were api 11+, we would just call setActivated on a standard LinearLayout
 * and wouldn't need this.
 *
 * however, since we want to support older api versions (for now), we do
 * this workaround instead.
 *
 * reference: http://stackoverflow.com/questions/11293399
 */
public class CheckableLinearLayout extends LinearLayout implements Checkable {
  private static final int[] CHECKED_STATE_SET = {
      android.R.attr.state_checked,
  };

  private boolean mIsChecked;

  public CheckableLinearLayout(Context context) {
    super(context);
  }

  public CheckableLinearLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  public CheckableLinearLayout(Context context,
      AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  public CheckableLinearLayout(Context context,
      AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
  }

  @Override
  protected int[] onCreateDrawableState(int extraSpace) {
    final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
    if (isChecked()) {
      mergeDrawableStates(drawableState, CHECKED_STATE_SET);
    }
    return drawableState;
  }

  @Override
  public void setChecked(boolean checked) {
    mIsChecked = checked;
    refreshDrawableState();
  }

  @Override
  public boolean isChecked() {
    return mIsChecked;
  }

  @Override
  public void toggle() {
    mIsChecked = !mIsChecked;
    refreshDrawableState();
  }
}
