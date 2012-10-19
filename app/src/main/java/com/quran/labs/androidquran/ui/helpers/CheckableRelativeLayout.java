package com.quran.labs.androidquran.ui.helpers;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Checkable;
import android.widget.RelativeLayout;

public class CheckableRelativeLayout extends RelativeLayout implements Checkable {

   private boolean mIsChecked = false;

   public CheckableRelativeLayout(Context context) {
      super(context);
   }

   public CheckableRelativeLayout(Context context, AttributeSet attrs) {
      super(context, attrs);
   }

   public CheckableRelativeLayout(Context context, AttributeSet attrs, int defStyle) {
      super(context, attrs, defStyle);
   }

   @Override
   public void setChecked(boolean checked) {
      mIsChecked = checked;
   }

   @Override
   public boolean isChecked() {
      return mIsChecked;
   }

   @Override
   public void toggle() {
      mIsChecked = !mIsChecked;
   }
}
