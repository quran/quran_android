package com.quran.labs.androidquran.widgets;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;

import com.quran.labs.androidquran.R;

public class FitSystemRelativeLayout extends RelativeLayout {
  private static final boolean IS_PRE_KITKAT = Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT;

  private int lastTopInset;
  private View toolBarParent;
  private MarginLayoutParams toolBarViewParams;
  private MarginLayoutParams audioBarViewParams;

  public FitSystemRelativeLayout(@NonNull Context context) {
    this(context, null);
  }

  public FitSystemRelativeLayout(@NonNull Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public FitSystemRelativeLayout(@NonNull Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      // needed to fix positioning of the ayah toolbar
      setLayoutDirection(LAYOUT_DIRECTION_LTR);
    }
  }

  @Override
  protected boolean fitSystemWindows(@NonNull Rect insets) {
    if (toolBarViewParams == null || audioBarViewParams == null) {
      View toolbar = findViewById(R.id.toolbar);
      toolBarParent = (View) toolbar.getParent();
      toolBarViewParams = (MarginLayoutParams) toolbar.getLayoutParams();
      audioBarViewParams = (MarginLayoutParams) findViewById(R.id.audio_area).getLayoutParams();
    }

    toolBarViewParams.setMargins(insets.left, insets.top, insets.right, 0);
    audioBarViewParams.setMargins(insets.left, 0, insets.right, insets.bottom);

    /*
      this is needed to fix a bug where the Toolbar is half cut off before Kitkat (especially when
      playing audio).

      the reason for this is that we always animate the Toolbar's parent to either 0 or to
      the negative value of its height, and on pre-Kitkat, the parent's height is incorrect (not
      reflecting the updated top margin on the toolbar itself), unless we explicitly
      requestLayout. the reason for this is that on Kitkat and above, the insets don't change when
      the toolbar is shown or hidden whereas pre-Kitkat, the inset's top value is 0 when the
      toolbar is gone and some value when the toolbar is visible.

      the audio bar solves this same problem by animating to its height plus its bottomMargin,
      (partially because the audio bar does not have a parent wrapper that is being animated, and,
      as a result, by definition, will never have its height reflect any updated margins). it is
      possible for the toolbar to solve the problem in the same way as well (by using the
      topMargin in addition to the height of the parent).
     */
    if (IS_PRE_KITKAT && lastTopInset != insets.top) {
      toolBarParent.requestLayout();
      lastTopInset = insets.top;
    }
    return true;
  }
}
