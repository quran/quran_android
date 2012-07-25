package com.quran.labs.androidquran.ui.helpers;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;
import android.widget.RelativeLayout;

public class CheckableRelativeLayout extends RelativeLayout implements Checkable {

   private boolean isChecked;
   private List<Checkable> checkableViews;
   
   public CheckableRelativeLayout(Context context, AttributeSet attrs, int defStyle) {
      super(context, attrs, defStyle);
      this.isChecked = false;
      this.checkableViews = new ArrayList<Checkable>();
   }

   public CheckableRelativeLayout(Context context, AttributeSet attrs) {
      super(context, attrs);
      this.isChecked = false;
      this.checkableViews = new ArrayList<Checkable>();
   }

   public CheckableRelativeLayout(Context context, int checkableId) {
      super(context);
      this.isChecked = false;
      this.checkableViews = new ArrayList<Checkable>();
   }

   @Override
   public void setChecked(boolean checked) {
      this.isChecked = checked;
      for (Checkable c : checkableViews) {
         c.setChecked(checked);
      }
   }

   @Override
   public boolean isChecked() {
      return isChecked;
   }

   @Override
   public void toggle() {
      this.isChecked = !this.isChecked;
      for (Checkable c : checkableViews) {
         c.toggle();
      }
   }
   
   @Override
   protected void onFinishInflate() {
      super.onFinishInflate();

      final int childCount = this.getChildCount();
      for (int i = 0; i < childCount; ++i) {
         findCheckableChildren(this.getChildAt(i));
      }
   }

   private void findCheckableChildren(View v) {
      if (v instanceof Checkable) {
         this.checkableViews.add((Checkable) v);
      }

      if (v instanceof ViewGroup) {
         final ViewGroup vg = (ViewGroup) v;
         final int childCount = vg.getChildCount();
         for (int i = 0; i < childCount; ++i) {
            findCheckableChildren(vg.getChildAt(i));
         }
      }
   }
}
