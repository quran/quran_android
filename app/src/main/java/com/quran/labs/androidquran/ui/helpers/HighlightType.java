package com.quran.labs.androidquran.ui.helpers;

import android.graphics.Color;
import android.graphics.Paint;

public enum HighlightType {

  // Declaration order determines highlighting priority (first is highest)
  SELECTION (true,  Color.argb(64, 70,  148, 166)), //#404694A6 (blue)
  AUDIO     (true,  Color.argb(64, 70,  166, 70)),  //#4046A646 (green)
  NOTE      (true,  Color.argb(64, 235, 235, 33)),  //#40EBEB21 (yellow)
  BOOKMARK  (false, Color.argb(64, 164, 164, 164)); //#40A4A4A4 (gray)

  private boolean mUnique;
  private Paint mPaint;

  private HighlightType(boolean unique, int color) {
    mUnique = unique;
    mPaint = new Paint();
    mPaint.setColor(color);
  }

  public boolean isUnique() {
    return mUnique;
  }

  public Paint getPaint() {
    return mPaint;
  }

  public long getId() {
    return this.ordinal();
  }
}
