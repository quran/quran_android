package com.quran.labs.androidquran.util;

import android.content.Context;
import net.hockeyapp.android.CrashManagerListener;

/**
 * Created by ahmedre on 6/15/13.
 */
public class QuranCrashListener extends CrashManagerListener {
  private static QuranCrashListener sInstance;
  private static Context sContext;

  public static QuranCrashListener getInstance(Context context) {
    if (sInstance == null) {
      sInstance = new QuranCrashListener();
      sContext = context.getApplicationContext();
    }
    return sInstance;
  }

  @Override
  public Boolean onCrashesFound() {
    // automatically send crashes
    return true;
  }

  @Override
  public String getDescription() {
    return QuranUtils.getDebugInfo(sContext);
  }
}
