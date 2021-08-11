package com.quran.labs.androidquran.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

public class LeftToRightLinearLayout extends LinearLayout {
  public LeftToRightLinearLayout(Context context) {
    this(context, null);
  }

  public LeftToRightLinearLayout(Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public LeftToRightLinearLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    setLayoutDirection(LAYOUT_DIRECTION_LTR);
  }

  public LeftToRightLinearLayout(Context context, AttributeSet attrs,
                                 int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    setLayoutDirection(LAYOUT_DIRECTION_LTR);
  }
}
