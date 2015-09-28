package com.quran.labs.androidquran.util;

import com.quran.labs.androidquran.data.Constants;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;

import java.io.File;


public class QuranSettings {

  private static QuranSettings sInstance;
  private SharedPreferences mPrefs;
  private Context mAppContext;

  public static synchronized QuranSettings getInstance(@NonNull Context context) {
    if (sInstance == null) {
      sInstance = new QuranSettings(context.getApplicationContext());
    }
    return sInstance;
  }

  private QuranSettings(@NonNull Context appContext) {
    mPrefs = PreferenceManager.getDefaultSharedPreferences(appContext);
    mAppContext = appContext;
  }

  public boolean isArabicNames() {
    return mPrefs.getBoolean(Constants.PREF_USE_ARABIC_NAMES, false);
  }

  public boolean isLockOrientation() {
    return mPrefs.getBoolean(Constants.PREF_LOCK_ORIENTATION, false);
  }

  public boolean isLandscapeOrientation() {
    return mPrefs.getBoolean(Constants.PREF_LANDSCAPE_ORIENTATION, false);
  }

  public boolean shouldStream() {
    return mPrefs.getBoolean(Constants.PREF_PREFER_STREAMING, false);
  }

  public boolean isNightMode() {
    return mPrefs.getBoolean(Constants.PREF_NIGHT_MODE, false);
  }

  public boolean useNewBackground() {
    return mPrefs.getBoolean(Constants.PREF_USE_NEW_BACKGROUND, true);
  }

  public boolean highlightBookmarks() {
    return mPrefs.getBoolean(Constants.PREF_HIGHLIGHT_BOOKMARKS, true);
  }

  public int getNightModeTextBrightness() {
    return mPrefs.getInt(Constants.PREF_NIGHT_MODE_TEXT_BRIGHTNESS,
        Constants.DEFAULT_NIGHT_MODE_TEXT_BRIGHTNESS);
  }

  public boolean shouldOverlayPageInfo() {
    return mPrefs.getBoolean(Constants.PREF_OVERLAY_PAGE_INFO, true);
  }

  public boolean shouldDisplayMarkerPopup() {
    return mPrefs.getBoolean(Constants.PREF_DISPLAY_MARKER_POPUP, true);
  }

  public boolean shouldHighlightBookmarks() {
    return mPrefs.getBoolean(Constants.PREF_HIGHLIGHT_BOOKMARKS, true);
  }

  public boolean wantArabicInTranslationView() {
    return mPrefs.getBoolean(Constants.PREF_AYAH_BEFORE_TRANSLATION, true);
  }

  public int getPreferredDownloadAmount() {
    String str = mPrefs.getString(Constants.PREF_DOWNLOAD_AMOUNT,
        "" + AudioUtils.LookAheadAmount.PAGE);
    int val = AudioUtils.LookAheadAmount.PAGE;
    try {
      val = Integer.parseInt(str);
    } catch (Exception e) {
    }

    if (val > AudioUtils.LookAheadAmount.MAX ||
        val < AudioUtils.LookAheadAmount.MIN) {
      return AudioUtils.LookAheadAmount.PAGE;
    }
    return val;
  }

  public int getTranslationTextSize() {
    return mPrefs.getInt(Constants.PREF_TRANSLATION_TEXT_SIZE,
        Constants.DEFAULT_TEXT_SIZE);
  }

  public int getLastPage() {
    return mPrefs.getInt(Constants.PREF_LAST_PAGE, Constants.NO_PAGE_SAVED);
  }

  public void setLastPage(int page) {
    mPrefs.edit().putInt(Constants.PREF_LAST_PAGE, page).apply();
  }

  public boolean didPresentSdcardPermissionsRationale() {
    return mPrefs.getBoolean(Constants.PREF_DID_PRESENT_PERMISSIONS_RATIONALE, false);
  }

  public void setSdcardPermissionsRationalePresented() {
    mPrefs.edit().putBoolean(Constants.PREF_DID_PRESENT_PERMISSIONS_RATIONALE, true).apply();
  }

  public String getAppCustomLocation() {
    String location = mPrefs.getString(Constants.PREF_APP_LOCATION, null);
    if (location == null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        // on M, default to /sdcard/Android/data/com.quran.labs.androidquran
        File externalFilesDir = mAppContext.getExternalFilesDir(null);
        if (externalFilesDir != null) {
          location = externalFilesDir.getAbsolutePath();
        } else {
          // suggests that data is on a storage device that isn't mounted
          location = null;
        }
      } else {
        // pre-M, this just gives points to /sdcard
        location = Environment.getExternalStorageDirectory().getAbsolutePath();
      }
    }
    return location;
  }

  public void setAppCustomLocation(String newLocation) {
    mPrefs.edit().putString(Constants.PREF_APP_LOCATION, newLocation).apply();
  }

  public String getPreMAppCustomLocation() {
    return mPrefs.getString(Constants.PREF_APP_LOCATION,
        Environment.getExternalStorageDirectory().getAbsolutePath());
  }

  public boolean isAppLocationSet() {
    return mPrefs.getString(Constants.PREF_APP_LOCATION, null) != null;
  }

  public void setActiveTranslation(String translation) {
    mPrefs.edit().putString(Constants.PREF_ACTIVE_TRANSLATION, translation).apply();
  }
}
