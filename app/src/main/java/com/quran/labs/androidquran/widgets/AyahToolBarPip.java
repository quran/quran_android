package com.quran.labs.androidquran.widgets;

import com.quran.labs.androidquran.R;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;

import static com.quran.labs.androidquran.widgets.AyahToolBar.PipPosition;

public class AyahToolBarPip extends ImageView {
  private PipPosition mPosition;

  public AyahToolBarPip(Context context) {
    super(context);
    init();
  }

  public AyahToolBarPip(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public AyahToolBarPip(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init();
  }

  private void init() {
    mPosition = PipPosition.DOWN;
    setImageResource(R.drawable.toolbar_pip_down);
  }

  public void ensurePosition(PipPosition position) {
    if (position != mPosition) {
      final int res = position == PipPosition.UP ? R.drawable.toolbar_pip_up :
          R.drawable.toolbar_pip_down;
      setImageResource(res);
      mPosition = position;
    }
  }
}
