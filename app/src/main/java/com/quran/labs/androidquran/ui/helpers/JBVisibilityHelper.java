package com.quran.labs.androidquran.ui.helpers;

import android.view.View;
import com.quran.labs.androidquran.ui.PagerActivity;

public class JBVisibilityHelper {

   public static void setVisibilityChangeListener(
           final PagerActivity activity, View view){
      view.setOnSystemUiVisibilityChangeListener(
              new View.OnSystemUiVisibilityChangeListener() {
         @Override
         public void onSystemUiVisibilityChange(int visibility) {
            if ((visibility & View.SYSTEM_UI_FLAG_LOW_PROFILE) == 0){
               activity.toggleActionBarVisibility(true);
            }
            else { activity.toggleActionBarVisibility(false); }
         }
      });
   }

   public static void clearVisibilityChangeListener(View view){
      if (view != null){
         view.setOnSystemUiVisibilityChangeListener(null);
      }
   }
}
