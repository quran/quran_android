package com.quran.labs.androidquran.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;

public class FitSystemRelativeLayout extends RelativeLayout {

  public FitSystemRelativeLayout(@NonNull Context context) {
    this(context, null);
  }

  public FitSystemRelativeLayout(@NonNull Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public FitSystemRelativeLayout(@NonNull Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    // needed to fix positioning of the ayah toolbar
    setLayoutDirection(LAYOUT_DIRECTION_LTR);
  }
}
