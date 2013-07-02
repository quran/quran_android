package com.quran.labs.androidquran.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * Created by ahmedre on 7/2/13.
 */
public class QuranMaxImageView extends ImageView {
  private int mMaxHeight = -1;

  public QuranMaxImageView(Context context) {
    super(context);
  }

  public QuranMaxImageView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public QuranMaxImageView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }

  public int getMaxBitmapHeight(){
    return mMaxHeight;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    if (Build.VERSION.SDK_INT >= 14){
      mMaxHeight = canvas.getMaximumBitmapHeight();
    }
    super.onDraw(canvas);
  }
}
