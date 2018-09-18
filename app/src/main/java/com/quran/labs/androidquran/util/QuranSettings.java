package com.quran.labs.androidquran.util;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import com.quran.labs.androidquran.BuildConfig;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.service.QuranDownloadService;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;


public class QuranSettings {
  private static final String PREFS_FILE = "com.quran.labs.androidquran.per_installation";

  private static QuranSettings instance;

  private Context appContext;
  private SharedPreferences prefs;
  private SharedPreferences perInstallationPrefs;

  public static synchronized QuranSettings getInstance(@NonNull Context context) {
    if (instance == null) {
      instance = new QuranSettings(context.getApplicationContext());
    }
    return instance;
  }

  @VisibleForTesting
  public static void setInstance(QuranSettings settings) {
    instance = settings;
  }

  private QuranSettings(@NonNull Context appContext) {
    this.appContext = appContext;
    prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
    perInstallationPrefs = appContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
  }

  public boolean isArabicNames() {
    return prefs.getBoolean(Constants.PREF_USE_ARABIC_NAMES, false);
  }

  public boolean isLockOrientation() {
    return prefs.getBoolean(Constants.PREF_LOCK_ORIENTATION, false);
  }

  public boolean isLandscapeOrientation() {
    return prefs.getBoolean(Constants.PREF_LANDSCAPE_ORIENTATION, false);
  }

  public boolean shouldStream() {
    return prefs.getBoolean(Constants.PREF_PREFER_STREAMING, false);
  }

  public boolean isNightMode() {
    return prefs.getBoolean(Constants.PREF_NIGHT_MODE, false);
  }

  public boolean useNewBackground() {
    return prefs.getBoolean(Constants.PREF_USE_NEW_BACKGROUND, true);
  }

  public boolean highlightBookmarks() {
    return prefs.getBoolean(Constants.PREF_HIGHLIGHT_BOOKMARKS, true);
  }

  public int getNightModeTextBrightness() {
    return prefs.getInt(Constants.PREF_NIGHT_MODE_TEXT_BRIGHTNESS,
        Constants.DEFAULT_NIGHT_MODE_TEXT_BRIGHTNESS);
  }

  public boolean shouldOverlayPageInfo() {
    return prefs.getBoolean(Constants.PREF_OVERLAY_PAGE_INFO, true);
  }

  public boolean shouldDisplayMarkerPopup() {
    return prefs.getBoolean(Constants.PREF_DISPLAY_MARKER_POPUP, true);
  }

  public boolean isImmersiveInPortrait() {
    return prefs.getBoolean(Constants.PREF_IMMERSIVE_IN_PORTRAIT,
        appContext.getResources().getBoolean(R.bool.immersive_in_portrait));
  }

  public boolean shouldHighlightBookmarks() {
    return prefs.getBoolean(Constants.PREF_HIGHLIGHT_BOOKMARKS, true);
  }

  public boolean wantArabicInTranslationView() {
    return prefs.getBoolean(Constants.PREF_AYAH_BEFORE_TRANSLATION, true);
  }

  public int getPreferredDownloadAmount() {
    String str = prefs.getString(Constants.PREF_DOWNLOAD_AMOUNT,
        "" + AudioUtils.LookAheadAmount.INSTANCE.getPAGE());
    int val = AudioUtils.LookAheadAmount.INSTANCE.getPAGE();
    try {
      val = Integer.parseInt(str);
    } catch (Exception e) {
      // no op
    }

    if (val > AudioUtils.LookAheadAmount.INSTANCE.getMAX() ||
        val < AudioUtils.LookAheadAmount.INSTANCE.getMIN()) {
      return AudioUtils.LookAheadAmount.INSTANCE.getPAGE();
    }
    return val;
  }

  public int getTranslationTextSize() {
    return prefs.getInt(Constants.PREF_TRANSLATION_TEXT_SIZE,
        Constants.DEFAULT_TEXT_SIZE);
  }

  public int getLastPage() {
    return prefs.getInt(Constants.PREF_LAST_PAGE, Constants.NO_PAGE);
  }

  public int getBookmarksSortOrder() {
    return prefs.getInt(Constants.PREF_SORT_BOOKMARKS, 0);
  }

  public void setBookmarksSortOrder(int sortOrder) {
    prefs.edit().putInt(Constants.PREF_SORT_BOOKMARKS, sortOrder).apply();
  }

  public boolean getBookmarksGroupedByTags() {
    return prefs.getBoolean(Constants.PREF_GROUP_BOOKMARKS_BY_TAG, false);
  }

  public void setBookmarksGroupedByTags(boolean groupedByTags) {
    prefs.edit().putBoolean(Constants.PREF_GROUP_BOOKMARKS_BY_TAG, groupedByTags).apply();
  }

  public String getPageType() {
    return prefs.getString(Constants.PREF_PAGE_TYPE, null);
  }

  public void setPageType(String pageType) {
    prefs.edit().putString(Constants.PREF_PAGE_TYPE, pageType).apply();
    clearDefaultImagesDirectory();
  }

  public boolean getShowRecents() {
    return prefs.getBoolean(Constants.PREF_SHOW_RECENTS, true);
  }

  public void setShowRecents(boolean minimizeRecents) {
    prefs.edit().putBoolean(Constants.PREF_SHOW_RECENTS, minimizeRecents).apply();
  }

  // probably should eventually move this to Application.onCreate..
  public void upgradePreferences() {
    int version = getVersion();
    if (version != BuildConfig.VERSION_CODE) {
      if (version == 0) {
        version = prefs.getInt(Constants.PREF_VERSION, 0);
      }

      if (version != 0) {
        if (version < 2672) {
          // migrate preferences
          setAppCustomLocation(prefs.getString(Constants.PREF_APP_LOCATION, null));

          if (prefs.contains(Constants.PREF_SHOULD_FETCH_PAGES)) {
            setShouldFetchPages(prefs.getBoolean(Constants.PREF_SHOULD_FETCH_PAGES, false));
          }

          if (prefs.contains(QuranDownloadService.PREF_LAST_DOWNLOAD_ERROR)) {
            setLastDownloadError(
                prefs.getString(QuranDownloadService.PREF_LAST_DOWNLOAD_ITEM, null),
                prefs.getInt(QuranDownloadService.PREF_LAST_DOWNLOAD_ERROR, 0));
          }

          prefs.edit()
              .remove(Constants.PREF_VERSION)
              .remove(Constants.PREF_APP_LOCATION)
              .remove(Constants.PREF_SHOULD_FETCH_PAGES)
              .remove(QuranDownloadService.PREF_LAST_DOWNLOAD_ERROR)
              .remove(QuranDownloadService.PREF_LAST_DOWNLOAD_ITEM)
              .remove(Constants.PREF_ACTIVE_TRANSLATION)
                  // these aren't migrated since they can be derived pretty easily
              .remove("didPresentPermissionsRationale") // was renamed, removing old one
              .remove(Constants.PREF_DEFAULT_IMAGES_DIR)
              .remove(Constants.PREF_HAVE_UPDATED_TRANSLATIONS)
              .remove(Constants.PREF_LAST_UPDATED_TRANSLATIONS)
              .apply();
        } else if (version < 2674) {
          // explicitly an else - if we migrated via the above, we're okay. otherwise, we are in
          // a bad state due to not crashing in 2.6.7-p2 (thus getting its incorrect behavior),
          // and thus crashing on 2.6.7-p3 and above (where the bug was fixed). this works around
          // this issue.
          try {
            getLastDownloadItemWithError();
            getLastDownloadErrorCode();
          } catch (Exception e) {
            clearLastDownloadError();
          }
        } else if (version == 2800) {
          // upgrading from 2800, need to remove notification channels
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            deleteOldNotificationChannels();
          }
        }
      }

      // no matter which version we're upgrading from, make sure the app location is set
      if (!isAppLocationSet()) {
        setAppCustomLocation(getAppCustomLocation());
      }

      // make sure that the version code now says that we're up to date.
      setVersion(BuildConfig.VERSION_CODE);
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  private void deleteOldNotificationChannels() {
    NotificationManager notificationManager =
        (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);
    if (notificationManager != null) {
      notificationManager.deleteNotificationChannel("quran_audio");
      notificationManager.deleteNotificationChannel("quran_download");
    }
  }

  public boolean didPresentSdcardPermissionsDialog() {
    return perInstallationPrefs.getBoolean(Constants.PREF_DID_PRESENT_PERMISSIONS_DIALOG, false);
  }

  public void setSdcardPermissionsDialogPresented() {
    perInstallationPrefs.edit()
        .putBoolean(Constants.PREF_DID_PRESENT_PERMISSIONS_DIALOG, true).apply();
  }

  public String getAppCustomLocation() {
    return perInstallationPrefs.getString(Constants.PREF_APP_LOCATION,
        Environment.getExternalStorageDirectory().getAbsolutePath());
  }

  public void setAppCustomLocation(String newLocation) {
    perInstallationPrefs.edit().putString(Constants.PREF_APP_LOCATION, newLocation).apply();
  }

  private boolean isAppLocationSet() {
    return perInstallationPrefs.getString(Constants.PREF_APP_LOCATION, null) != null;
  }

  public void setActiveTranslations(Set<String> activeTranslations) {
    perInstallationPrefs.edit()
        .putStringSet(Constants.PREF_ACTIVE_TRANSLATIONS, activeTranslations).apply();
  }

  public Set<String> getActiveTranslations() {
    if (!perInstallationPrefs.contains(Constants.PREF_ACTIVE_TRANSLATIONS)) {
      String translation = perInstallationPrefs.getString(Constants.PREF_ACTIVE_TRANSLATION, null);
      Set<String> active = new HashSet<>();
      if (translation != null) {
        active.add(translation);
      }
      return active;
    } else {
      return perInstallationPrefs.getStringSet(
          Constants.PREF_ACTIVE_TRANSLATIONS, Collections.emptySet());
    }
  }

  public int getVersion() {
    return perInstallationPrefs.getInt(Constants.PREF_VERSION, 0);
  }

  public void setVersion(int version) {
    perInstallationPrefs.edit().putInt(Constants.PREF_VERSION, version).apply();
  }

  public boolean shouldFetchPages() {
    return perInstallationPrefs.getBoolean(Constants.PREF_SHOULD_FETCH_PAGES, false);
  }

  public void setShouldFetchPages(boolean shouldFetchPages) {
    perInstallationPrefs.edit().putBoolean(Constants.PREF_SHOULD_FETCH_PAGES, shouldFetchPages).apply();
  }

  public void removeShouldFetchPages() {
    perInstallationPrefs.edit().remove(Constants.PREF_SHOULD_FETCH_PAGES).apply();
  }

  public void setDownloadedPages(boolean didDownload) {
    perInstallationPrefs.edit().putBoolean(Constants.PREF_DID_DOWNLOAD_PAGES, didDownload).apply();
  }

  public boolean didDownloadPages() {
    return perInstallationPrefs.getBoolean(Constants.PREF_DID_DOWNLOAD_PAGES, false);
  }

  public boolean haveUpdatedTranslations() {
    return perInstallationPrefs.getBoolean(Constants.PREF_HAVE_UPDATED_TRANSLATIONS, false);
  }

  public void setHaveUpdatedTranslations(boolean haveUpdatedTranslations) {
    perInstallationPrefs.edit().putBoolean(Constants.PREF_HAVE_UPDATED_TRANSLATIONS,
        haveUpdatedTranslations).apply();
  }

  public long getLastUpdatedTranslationDate() {
    return perInstallationPrefs.getLong(Constants.PREF_LAST_UPDATED_TRANSLATIONS,
        System.currentTimeMillis());
  }

  public void setLastUpdatedTranslationDate(long date) {
    perInstallationPrefs.edit().putLong(Constants.PREF_LAST_UPDATED_TRANSLATIONS, date).apply();
  }

  public String getLastDownloadItemWithError() {
    return perInstallationPrefs.getString(QuranDownloadService.PREF_LAST_DOWNLOAD_ITEM, "");
  }

  public int getLastDownloadErrorCode() {
    return perInstallationPrefs.getInt(QuranDownloadService.PREF_LAST_DOWNLOAD_ERROR, 0);
  }

  public void setLastDownloadError(String lastDownloadItem, int lastDownloadError) {
    perInstallationPrefs.edit()
        .putInt(QuranDownloadService.PREF_LAST_DOWNLOAD_ERROR, lastDownloadError)
        .putString(QuranDownloadService.PREF_LAST_DOWNLOAD_ITEM, lastDownloadItem)
        .apply();
  }

  public void clearLastDownloadError() {
    perInstallationPrefs.edit()
        .remove(QuranDownloadService.PREF_LAST_DOWNLOAD_ERROR)
        .remove(QuranDownloadService.PREF_LAST_DOWNLOAD_ITEM)
        .apply();
  }

  public void clearDefaultImagesDirectory() {
    perInstallationPrefs.edit().remove(Constants.PREF_DEFAULT_IMAGES_DIR).apply();
  }

  public boolean haveDefaultImagesDirectory() {
    return perInstallationPrefs.contains(Constants.PREF_DEFAULT_IMAGES_DIR);
  }

  public void setDefaultImagesDirectory(String directory) {
    perInstallationPrefs.edit().putString(Constants.PREF_DEFAULT_IMAGES_DIR, directory).apply();
  }

  String getDefaultImagesDirectory() {
    return perInstallationPrefs.getString(Constants.PREF_DEFAULT_IMAGES_DIR, "");
  }

  public void setWasShowingTranslation(boolean wasShowingTranslation) {
    perInstallationPrefs.edit().putBoolean(Constants.PREF_WAS_SHOWING_TRANSLATION,
        wasShowingTranslation).apply();
  }

  public boolean getWasShowingTranslation() {
    return perInstallationPrefs.getBoolean(Constants.PREF_WAS_SHOWING_TRANSLATION, false);
  }
}
