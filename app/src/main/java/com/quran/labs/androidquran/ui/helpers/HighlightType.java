package com.quran.labs.androidquran.ui.helpers;

import android.content.Context;
import android.graphics.Paint;

import com.quran.labs.androidquran.R;

public class HighlightType implements Comparable<HighlightType> {

  public static final HighlightType SELECTION = new HighlightType(1, false, R.color.selection_highlight);
  public static final HighlightType AUDIO =     new HighlightType(2, false, R.color.audio_highlight);
  public static final HighlightType NOTE =      new HighlightType(3, true,  R.color.note_highlight);
  public static final HighlightType BOOKMARK =  new HighlightType(4, true,  R.color.bookmark_highlight);

  private Long mId;
  private boolean mMultipleHighlightsAllowed;
  private int mColorId;
  private Paint mPaint;

  private HighlightType(long id, boolean multipleHighlightsAllowed, int colorId) {
    mId = id;
    mMultipleHighlightsAllowed = multipleHighlightsAllowed;
    mColorId = colorId;
  }

  public boolean isMultipleHighlightsAllowed() {
    return mMultipleHighlightsAllowed;
  }

  public Paint getPaint(Context context) {
    if (mPaint == null) {
      int color = context.getResources().getColor(mColorId);
      mPaint = new Paint();
      mPaint.setColor(color);
    }
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
