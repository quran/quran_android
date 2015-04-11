package com.quran.labs.androidquran.widgets;

import com.quran.labs.androidquran.R;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

public class FitSystemRelativeLayout extends RelativeLayout {
  private MarginLayoutParams mToolBarViewParams;
  private MarginLayoutParams mAudioBarViewParams;

  public FitSystemRelativeLayout(@NonNull Context context) {
    super(context);
  }

  public FitSystemRelativeLayout(@NonNull Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public FitSystemRelativeLayout(@NonNull Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  public FitSystemRelativeLayout(@NonNull Context context, AttributeSet attrs, int defStyleAttr,
      int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
  }

  @Override
  protected boolean fitSystemWindows(@NonNull Rect insets) {
    if (mToolBarViewParams == null || mAudioBarViewParams == null) {
      mToolBarViewParams = (MarginLayoutParams) findViewById(R.id.toolbar).getLayoutParams();
      mAudioBarViewParams = (MarginLayoutParams) findViewById(R.id.audio_area).getLayoutParams();
    }

    mToolBarViewParams.setMargins(insets.left, insets.top, insets.right, insets.bottom);
    mAudioBarViewParams.setMargins(insets.left, insets.top, insets.right, insets.bottom);
    return true;
  }
}
