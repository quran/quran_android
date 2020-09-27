package com.quran.labs.androidquran.view;

import android.content.Context;
import android.os.Build;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import android.util.AttributeSet;
import android.widget.LinearLayout;

public class LeftToRightLinearLayout extends LinearLayout {
  public LeftToRightLinearLayout(Context context) {
    this(context, null);
  }

  public LeftToRightLinearLayout(Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public LeftToRightLinearLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      setLayoutDirection(LAYOUT_DIRECTION_LTR);
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  public LeftToRightLinearLayout(Context context, AttributeSet attrs,
                                 int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      setLayoutDirection(LAYOUT_DIRECTION_LTR);
    }
  }
}
