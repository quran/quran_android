package com.quran.labs.androidquran.ui;

import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;

public abstract class QuranActionBarActivity extends AppCompatActivity {

  /**
   * work around an LG bug on 4.1.2.
   * see http://stackoverflow.com/questions/26833242
   * and also https://code.google.com/p/android/issues/detail?id=78154
   */
  private static final boolean sBuggyMenuVersion =
      Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN &&
          "LGE".equalsIgnoreCase(Build.BRAND);

  public boolean onKeyDown(int keyCode, KeyEvent event) {
    return keyCode == KeyEvent.KEYCODE_MENU && sBuggyMenuVersion || super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onKeyUp(int keyCode, @NonNull KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_MENU && sBuggyMenuVersion) {
      openOptionsMenu();
      return true;
    }
    return super.onKeyUp(keyCode, event);
  }
}
