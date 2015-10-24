package com.quran.labs.androidquran.ui.helpers;

import android.graphics.Paint;
import android.text.style.LineHeightSpan;

public class VerseLineHeightSpan implements LineHeightSpan {
  public static final float TRANSLATION_LINE_HEIGHT_MULTIPLY = 1.4f;
  public static final float TRANSLATION_LINE_HEIGHT_ADDITION = 1.4f;

  @Override
  public void chooseHeight(CharSequence text, int start, int end, int spanstartv, int v,
      Paint.FontMetricsInt fm) {
    int top = (int) ((fm.top - TRANSLATION_LINE_HEIGHT_ADDITION) /
        TRANSLATION_LINE_HEIGHT_MULTIPLY);
    int bottom = (int) ((fm.bottom - TRANSLATION_LINE_HEIGHT_ADDITION) /
        TRANSLATION_LINE_HEIGHT_MULTIPLY);

    // ascent and descent are what need to be modified, but also modifying top and bottom
    // just in case (since ascent/descent are recommended distances, whereas top/bottom are
    // maximum distances above/below the baseline).
    fm.top = fm.ascent = top;
    fm.bottom = fm.descent = bottom;
  }
}
