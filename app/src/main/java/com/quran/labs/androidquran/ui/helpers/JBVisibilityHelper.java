package com.quran.labs.androidquran.ui.helpers;

import android.view.View;
import com.quran.labs.androidquran.ui.PagerActivity;

public class JBVisibilityHelper {
   private static int sLastVisibility =
             View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
           | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;

   public static void setVisibilityChangeListener(
           final PagerActivity activity, View view){
      view.setOnSystemUiVisibilityChangeListener(
              new View.OnSystemUiVisibilityChangeListener() {
         @Override
         public void onSystemUiVisibilityChange(int visibility) {
            int diff = sLastVisibility ^ visibility;
            sLastVisibility = diff;
            if ((diff & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) != 0 &&
                (visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0){
                activity.toggleActionBarVisibility(true);
            }
         }
      });
   }

   public static void clearVisibilityChangeListener(View view){
      if (view != null){
         view.setOnSystemUiVisibilityChangeListener(null);
      }
   }
}
