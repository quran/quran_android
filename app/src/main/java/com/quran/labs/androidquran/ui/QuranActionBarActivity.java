package com.quran.labs.androidquran.ui;

import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBarActivity;
import android.view.KeyEvent;

public abstract class QuranActionBarActivity extends ActionBarActivity {
  private static boolean sBuggyMenu = false;

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (!sBuggyMenu) {
      return super.onKeyDown(keyCode, event);
    } else {
      // work around an LG crash - see onKeyUp for more info
      return keyCode == KeyEvent.KEYCODE_MENU &&
          Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN
          || super.onKeyDown(keyCode, event);
    }
  }

  @Override
  public boolean onKeyUp(int keyCode, @NonNull KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_MENU &&
        Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN) {
      // work around LG crash on Jellybean. some discussion here:
      // https://code.google.com/p/android/issues/detail?id=78154

      // on devices with hardware keys tested, if we return true
      // from onKeyDown, openOptionsMenu works, but seemingly does
      // nothing if we let super run its course. so we catch the
      // crash the first time and do nothing, then always open
      // the menu ourselves from then on.
      if (sBuggyMenu) {
        openOptionsMenu();
        return true;
      }

      try {
        return super.onKeyUp(keyCode, event);
      } catch (NullPointerException npe) {
        // the first time this happens, we won't show a menu, but we'll log
        // that we have a buggy menu. next time, we'll just return true on
        // down and open the options menu here.
        sBuggyMenu = true;
        return true;
      }
    }
    return super.onKeyUp(keyCode, event);
  }
}
