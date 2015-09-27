package com.quran.labs.androidquran.ui.helpers;

import com.quran.labs.androidquran.ui.util.TypefaceManager;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

public class UthmaniSpan extends MetricAffectingSpan {
  private Typeface mTypeface;

  public UthmaniSpan(Context context) {
    mTypeface = TypefaceManager.getUthmaniTypeface(context);
  }

  @Override
  public void updateDrawState(TextPaint ds) {
    ds.setTypeface(mTypeface);
  }

  @Override
  public void updateMeasureState(TextPaint paint) {
    paint.setTypeface(mTypeface);
  }
}
