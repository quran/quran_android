package com.quran.labs.androidquran.util;

import com.quran.labs.androidquran.BuildConfig;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.service.QuranDownloadService;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;


public class QuranSettings {
  private static final String PREFS_FILE = "com.quran.labs.androidquran.per_installation";

  private static QuranSettings sInstance;
  private SharedPreferences mPrefs;
  private SharedPreferences mPerInstallationPrefs;

  public static synchronized QuranSettings getInstance(@NonNull Context context) {
    if (sInstance == null) {
      sInstance = new QuranSettings(context.getApplicationContext());
    }
    return sInstance;
  }

  @VisibleForTesting
  public static void setInstance(QuranSettings settings) {
    sInstance = settings;
  }

  private QuranSettings(@NonNull Context appContext) {
    mPrefs = PreferenceManager.getDefaultSharedPreferences(appContext);
    mPerInstallationPrefs = appContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
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
      // no op
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

  public int getBookmarksSortOrder() {
    return mPrefs.getInt(Constants.PREF_SORT_BOOKMARKS, 0);
  }

  public void setBookmarksSortOrder(int sortOrder) {
    mPrefs.edit().putInt(Constants.PREF_SORT_BOOKMARKS, sortOrder).apply();
  }

  public boolean getBookmarksGroupedByTags() {
    return mPrefs.getBoolean(Constants.PREF_GROUP_BOOKMARKS_BY_TAG, true);
  }

  public void setBookmarksGroupedByTags(boolean groupedByTags) {
    mPrefs.edit().putBoolean(Constants.PREF_GROUP_BOOKMARKS_BY_TAG, groupedByTags).apply();
  }

  // probably should eventually move this to Application.onCreate..
  public void upgradePreferences() {
    int version = getVersion();
    if (version != BuildConfig.VERSION_CODE) {
      if (version == 0) {
        version = mPrefs.getInt(Constants.PREF_VERSION, 0);
      }

      if (version != 0) {
        if (version < 2672) {
          // migrate preferences
          setAppCustomLocation(mPrefs.getString(Constants.PREF_APP_LOCATION, null));

          if (mPrefs.contains(Constants.PREF_SHOULD_FETCH_PAGES)) {
            setShouldFetchPages(mPrefs.getBoolean(Constants.PREF_SHOULD_FETCH_PAGES, false));
          }

          if (mPrefs.contains(QuranDownloadService.PREF_LAST_DOWNLOAD_ERROR)) {
            setLastDownloadError(
                mPrefs.getString(QuranDownloadService.PREF_LAST_DOWNLOAD_ITEM, null),
                mPrefs.getInt(QuranDownloadService.PREF_LAST_DOWNLOAD_ERROR, 0));
          }

          if (mPrefs.contains(Constants.PREF_ACTIVE_TRANSLATION)) {
            setActiveTranslation(mPrefs.getString(Constants.PREF_ACTIVE_TRANSLATION, null));
          }

          mPrefs.edit()
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

  public boolean didPresentSdcardPermissionsDialog() {
    return mPerInstallationPrefs.getBoolean(Constants.PREF_DID_PRESENT_PERMISSIONS_DIALOG, false);
  }

  public void setSdcardPermissionsDialogPresented() {
    mPerInstallationPrefs.edit()
        .putBoolean(Constants.PREF_DID_PRESENT_PERMISSIONS_DIALOG, true).apply();
  }

  public String getAppCustomLocation() {
    return mPerInstallationPrefs.getString(Constants.PREF_APP_LOCATION,
        Environment.getExternalStorageDirectory().getAbsolutePath());
  }

  public void setAppCustomLocation(String newLocation) {
    mPerInstallationPrefs.edit().putString(Constants.PREF_APP_LOCATION, newLocation).apply();
  }

  public boolean isAppLocationSet() {
    return mPerInstallationPrefs.getString(Constants.PREF_APP_LOCATION, null) != null;
  }

  public String getActiveTranslation() {
    return mPerInstallationPrefs.getString(Constants.PREF_ACTIVE_TRANSLATION, "");
  }

  public void setActiveTranslation(String translation) {
    mPerInstallationPrefs.edit().putString(Constants.PREF_ACTIVE_TRANSLATION, translation).apply();
  }

  public void removeActiveTranslation() {
    mPerInstallationPrefs.edit().remove(Constants.PREF_ACTIVE_TRANSLATION).apply();
  }

  public int getVersion() {
    return mPerInstallationPrefs.getInt(Constants.PREF_VERSION, 0);
  }

  public void setVersion(int version) {
    mPerInstallationPrefs.edit().putInt(Constants.PREF_VERSION, version).apply();
  }

  public boolean shouldFetchPages() {
    return mPerInstallationPrefs.getBoolean(Constants.PREF_SHOULD_FETCH_PAGES, false);
  }

  public void setShouldFetchPages(boolean shouldFetchPages) {
    mPerInstallationPrefs.edit().putBoolean(Constants.PREF_SHOULD_FETCH_PAGES, shouldFetchPages).apply();
  }

  public void removeShouldFetchPages() {
    mPerInstallationPrefs.edit().remove(Constants.PREF_SHOULD_FETCH_PAGES).apply();
  }

  public boolean haveUpdatedTranslations() {
    return mPerInstallationPrefs.getBoolean(Constants.PREF_HAVE_UPDATED_TRANSLATIONS, false);
  }

  public void setHaveUpdatedTranslations(boolean haveUpdatedTranslations) {
    mPerInstallationPrefs.edit().putBoolean(Constants.PREF_HAVE_UPDATED_TRANSLATIONS,
        haveUpdatedTranslations).apply();
  }

  public long getLastUpdatedTranslationDate() {
    return mPerInstallationPrefs.getLong(Constants.PREF_LAST_UPDATED_TRANSLATIONS,
        System.currentTimeMillis());
  }

  public void setLastUpdatedTranslationDate(long date) {
    mPerInstallationPrefs.edit().putLong(Constants.PREF_LAST_UPDATED_TRANSLATIONS, date).apply();
  }

  public String getLastDownloadItemWithError() {
    return mPerInstallationPrefs.getString(QuranDownloadService.PREF_LAST_DOWNLOAD_ITEM, "");
  }

  public int getLastDownloadErrorCode() {
    return mPerInstallationPrefs.getInt(QuranDownloadService.PREF_LAST_DOWNLOAD_ERROR, 0);
  }

  public void setLastDownloadError(String lastDownloadItem, int lastDownloadError) {
    mPerInstallationPrefs.edit()
        .putInt(QuranDownloadService.PREF_LAST_DOWNLOAD_ERROR, lastDownloadError)
        .putString(QuranDownloadService.PREF_LAST_DOWNLOAD_ITEM, lastDownloadItem)
        .apply();
  }

  public void clearLastDownloadError() {
    mPerInstallationPrefs.edit()
        .remove(QuranDownloadService.PREF_LAST_DOWNLOAD_ERROR)
        .remove(QuranDownloadService.PREF_LAST_DOWNLOAD_ITEM)
        .apply();
  }

  public boolean haveDefaultImagesDirectory() {
    return mPerInstallationPrefs.contains(Constants.PREF_DEFAULT_IMAGES_DIR);
  }

  public void setDefaultImagesDirectory(String directory) {
    mPerInstallationPrefs.edit().putString(Constants.PREF_DEFAULT_IMAGES_DIR, directory).apply();
  }

  public String getDefaultImagesDirectory() {
    return mPerInstallationPrefs.getString(Constants.PREF_DEFAULT_IMAGES_DIR, "");
  }
}
