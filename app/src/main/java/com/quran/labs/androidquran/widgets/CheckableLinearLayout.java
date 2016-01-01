package com.quran.labs.androidquran.widgets;

import com.quran.labs.androidquran.R;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
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
  private int mMinHeight;

  public CheckableLinearLayout(Context context) {
    super(context);
  }

  public CheckableLinearLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(context, attrs);
  }

  public CheckableLinearLayout(Context context,
      AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init(context, attrs);
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  public CheckableLinearLayout(Context context,
      AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    init(context, attrs);
  }

  private void init(Context context, AttributeSet attrs) {
    if (attrs != null) {
      TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.CheckableLinearLayout);
      mMinHeight = ta.getDimensionPixelSize(R.styleable.CheckableLinearLayout_minHeight, 0);
      ta.recycle();
    }
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

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    if (mMinHeight > 0 && getMeasuredHeight() < mMinHeight) {
      setMeasuredDimension(getMeasuredWidth(), mMinHeight);
    }
  }
}
