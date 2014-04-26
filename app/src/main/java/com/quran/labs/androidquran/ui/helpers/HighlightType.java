package com.quran.labs.androidquran.ui.helpers;

import android.graphics.Color;
import android.graphics.Paint;

public class HighlightType implements Comparable<HighlightType> {

  private static final int BLUE =   Color.argb(64, 70,  148, 166);  //#404694A6
  private static final int GREEN =  Color.argb(64, 70,  166, 70);   //#4046A646
  private static final int YELLOW = Color.argb(64, 235, 235, 33);   //#40EBEB21
  private static final int GRAY =   Color.argb(64, 164, 164, 164);  //#40A4A4A4

  public static final HighlightType SELECTION = new HighlightType(1, true,  BLUE);
  public static final HighlightType AUDIO =     new HighlightType(2, true,  GREEN);
  public static final HighlightType NOTE =      new HighlightType(3, true,  YELLOW);
  public static final HighlightType BOOKMARK =  new HighlightType(4, false, GRAY);

  private Long mId;
  private boolean mUnique;
  private Paint mPaint;

  private HighlightType(long id, boolean unique, int color) {
    mId = id;
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

  @Override
  public int compareTo(HighlightType another) {
    return mId.compareTo(another.mId);
  }

  @Override
  public boolean equals(Object o) {
    return this == o || o != null && o.getClass() == HighlightType.class &&
        mId.equals(((HighlightType) o).mId);
  }

  @Override
  public int hashCode() {
    return mId.hashCode();
  }

}
