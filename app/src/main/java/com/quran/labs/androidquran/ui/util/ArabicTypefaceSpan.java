package com.quran.labs.androidquran.ui.util;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Parcel;
import android.text.TextPaint;
import android.text.style.TypefaceSpan;
import com.quran.labs.androidquran.util.ArabicStyle;

/**
 * Created by ahmedre on 6/14/13.
 */
public class ArabicTypefaceSpan extends TypefaceSpan {
   private Context mContext;
   private boolean mIsBold = true;

   public ArabicTypefaceSpan(Context context, boolean bold) {
      super("monospace");
      mContext = context;
      mIsBold = bold;
   }

   public ArabicTypefaceSpan(Context context, Parcel src) {
      super(src);
      mContext = context;
   }

   @Override
   public void updateDrawState(TextPaint ds) {
      apply(ds, mContext, mIsBold);
   }

   @Override
   public void updateMeasureState(TextPaint paint) {
      apply(paint, mContext, mIsBold);
   }

   private static void apply(Paint paint, Context context, boolean isBold) {
      Typeface tf = ArabicStyle.getTypeface(context);
      paint.setFakeBoldText(isBold);
      paint.setTypeface(tf);
   }
}
