package com.quran.labs.androidquran.view;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;

import com.quran.labs.androidquran.R;
import androidx.annotation.NonNull;

public class FitSystemRelativeLayout extends RelativeLayout {

  private View toolBarParent;
  private View audioBarView;
  private MarginLayoutParams audioBarViewParams;

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

  @Override
  protected boolean fitSystemWindows(@NonNull Rect insets) {
    if (toolBarParent == null || audioBarView == null || audioBarViewParams == null) {
      View toolbar = findViewById(R.id.toolbar);
      toolBarParent = (View) toolbar.getParent();
      audioBarView = findViewById(R.id.audio_area);
      audioBarViewParams = (MarginLayoutParams) audioBarView.getLayoutParams();
    }

    toolBarParent.setPadding(insets.left, insets.top, insets.right, 0);
    audioBarView.setPadding(insets.left, 0, insets.right, 0);
    audioBarViewParams.setMargins(0, 0, 0, insets.bottom);

    return false;
  }
}
