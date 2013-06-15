package com.quran.labs.androidquran.util;

import net.hockeyapp.android.CrashManagerListener;

/**
 * Created by ahmedre on 6/15/13.
 */
public class QuranCrashListener extends CrashManagerListener {
   private static QuranCrashListener sInstance;

   public static QuranCrashListener getInstance(){
      if (sInstance == null){
         sInstance = new QuranCrashListener();
      }
      return sInstance;
   }

   @Override
   public Boolean onCrashesFound() {
      // automatically send crashes
      return true;
   }
}
