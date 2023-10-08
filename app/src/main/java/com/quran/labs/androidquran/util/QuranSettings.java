package com.quran.labs.androidquran.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.quran.common.upgrade.PreferencesUpgrade;
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

  private final Context appContext;
  private final SharedPreferences prefs;
  private final SharedPreferences perInstallationPrefs;

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

  public void registerPreferencesListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
    prefs.registerOnSharedPreferenceChangeListener(listener);
  }

  public void unregisterPreferencesListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
    prefs.unregisterOnSharedPreferenceChangeListener(listener);
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

  public boolean navigateWithVolumeKeys() {
    return prefs.getBoolean(Constants.PREF_USE_VOLUME_KEY_NAV, false);
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

  public int getNightModeBackgroundBrightness() {
    return prefs.getInt(Constants.PREF_NIGHT_MODE_BACKGROUND_BRIGHTNESS,
        Constants.DEFAULT_NIGHT_MODE_BACKGROUND_BRIGHTNESS);
  }

  public boolean shouldOverlayPageInfo() {
    return prefs.getBoolean(Constants.PREF_OVERLAY_PAGE_INFO, true);
  }

  public void setShouldOverlayPageInfo(boolean shouldOverlay) {
    prefs.edit().putBoolean(Constants.PREF_OVERLAY_PAGE_INFO, shouldOverlay).apply();
  }

  public boolean shouldDisplayMarkerPopup() {
    return prefs.getBoolean(Constants.PREF_DISPLAY_MARKER_POPUP, true);
  }

  public boolean shouldHighlightBookmarks() {
    return prefs.getBoolean(Constants.PREF_HIGHLIGHT_BOOKMARKS, true);
  }

  public boolean wantArabicInTranslationView() {
    return prefs.getBoolean(Constants.PREF_AYAH_BEFORE_TRANSLATION, true);
  }

  public boolean wantDyslexicFontInTranslationView() {
    return prefs.getBoolean(Constants.PREF_USE_DYSLEXIC_FONT, false);
  }

  public int getPreferredDownloadAmount() {
    String str = prefs.getString(Constants.PREF_DOWNLOAD_AMOUNT,
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

  // only available for Naskh, should return false by default for non-Naskh pages
  public boolean isSidelines() {
    return prefs.getBoolean(Constants.PREF_SHOW_SIDELINES, false);
  }

  public void setSidelines(boolean sidelines) {
    prefs.edit().putBoolean(Constants.PREF_SHOW_SIDELINES, sidelines).apply();
  }

  // only available for Naskh, should return false by default for non-Naskh pages
  public boolean isShowLineDividers() {
    return prefs.getBoolean(Constants.PREF_SHOW_LINE_DIVIDERS, false);
  }

  public void setShowLineDividers(boolean showLineDividers) {
    prefs.edit().putBoolean(Constants.PREF_SHOW_LINE_DIVIDERS, showLineDividers).apply();
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

  public boolean getShowDate() {
    return prefs.getBoolean(Constants.PREF_SHOW_DATE, false);
  }

  public void setShowDate(boolean isDateShown) {
    prefs.edit().putBoolean(Constants.PREF_SHOW_DATE, isDateShown).apply();
  }

  public boolean isQuranSplitWithTranslation() {
    return prefs.getBoolean(Constants.PREF_SPLIT_PAGE_AND_TRANSLATION, false);
  }

  public boolean isShowSuraTranslatedName() {
    return prefs.getBoolean(Constants.PREF_SURA_TRANSLATED_NAME,
        appContext.getResources().getBoolean(R.bool.show_sura_names_translation));
  }

  // probably should eventually move this to Application.onCreate..
  public void upgradePreferences(PreferencesUpgrade preferencesUpgrade) {
    int version = getVersion();
    if (version != BuildConfig.VERSION_CODE) {
      if (version == 0) {
        // try fetching from prefs instead of from per installation prefs
        version = prefs.getInt(Constants.PREF_VERSION, 0);
      }

      // no matter which version we're upgrading from, make sure the app location is set
      if (!isAppLocationSet()) {
        setAppCustomLocation(getAppCustomLocation());
      }

      // allow specific flavors of the app to handle their own upgrade logic.
      // this is important because different flavors have different version codes, so
      // common code here would likely be wrong for other flavors (unless it depends on
      // relative offsets to the version code instead of the actual version code).
      if (preferencesUpgrade.upgrade(appContext, version, BuildConfig.VERSION_CODE)) {
        // make sure that the version code now says that we're up to date.
        setVersion(BuildConfig.VERSION_CODE);
      }
    }
  }

  public int getCurrentAudioRevision() {
    return perInstallationPrefs.getInt(Constants.PREF_CURRENT_AUDIO_REVISION, 1);
  }

  public void setCurrentAudioRevision(int version) {
    perInstallationPrefs.edit()
        .putInt(Constants.PREF_CURRENT_AUDIO_REVISION, version).apply();
  }

  public boolean didPresentSdcardPermissionsDialog() {
    return perInstallationPrefs.getBoolean(Constants.PREF_DID_PRESENT_PERMISSIONS_DIALOG, false);
  }

  public void setSdcardPermissionsDialogPresented() {
    perInstallationPrefs.edit()
        .putBoolean(Constants.PREF_DID_PRESENT_PERMISSIONS_DIALOG, true).apply();
  }

  public String getAppCustomLocation() {
    return perInstallationPrefs.getString(Constants.PREF_APP_LOCATION, getDefaultLocation());
  }

  public String getDefaultLocation() {
    return appContext.getFilesDir().getAbsolutePath();
  }

  public void setAppCustomLocation(String newLocation) {
    perInstallationPrefs.edit().putString(Constants.PREF_APP_LOCATION, newLocation).apply();
  }

  public boolean isAppLocationSet() {
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
    perInstallationPrefs.edit().putBoolean(Constants.PREF_SHOULD_FETCH_PAGES, shouldFetchPages)
        .apply();
  }

  public void removeShouldFetchPages() {
    perInstallationPrefs.edit().remove(Constants.PREF_SHOULD_FETCH_PAGES).apply();
  }

  public void setDownloadedPages(long when, String path, String pageTypes) {
    perInstallationPrefs.edit().putBoolean(Constants.DEBUG_DID_DOWNLOAD_PAGES, true)
        .putString(Constants.DEBUG_PAGE_DOWNLOADED_PATH, path)
        .putString(Constants.DEBUG_PAGES_DOWNLOADED, pageTypes)
        .putLong(Constants.DEBUG_PAGES_DOWNLOADED_TIME, when)
        .apply();
  }

  public void removeDidDownloadPages() {
    perInstallationPrefs.edit().remove(Constants.DEBUG_DID_DOWNLOAD_PAGES)
        .remove(Constants.DEBUG_PAGE_DOWNLOADED_PATH)
        .remove(Constants.DEBUG_PAGES_DOWNLOADED_TIME)
        .remove(Constants.DEBUG_PAGES_DOWNLOADED)
        .apply();
  }

  public boolean didDownloadPages() {
    return perInstallationPrefs.getBoolean(Constants.DEBUG_DID_DOWNLOAD_PAGES, false);
  }

  public long getPreviouslyDownloadedTime() {
    return perInstallationPrefs.getLong(Constants.DEBUG_PAGES_DOWNLOADED_TIME, 0);
  }

  public String getPreviouslyDownloadedPath() {
    return perInstallationPrefs.getString(Constants.DEBUG_PAGE_DOWNLOADED_PATH, "");
  }

  public String getPreviouslyDownloadedPageTypes() {
    return perInstallationPrefs.getString(Constants.DEBUG_PAGES_DOWNLOADED, "");
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

  private void clearDefaultImagesDirectory() {
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

  public boolean didCheckPartialImages(String pageType) {
    final Set<String> checkedSets =
        perInstallationPrefs.getStringSet(Constants.PREF_CHECKED_PARTIAL_IMAGES,
            Collections.emptySet());
    return checkedSets != null && checkedSets.contains(pageType);
  }

  public void setCheckedPartialImages(String pageType) {
    final Set<String> checkedSets =
        perInstallationPrefs.getStringSet(Constants.PREF_CHECKED_PARTIAL_IMAGES,
            Collections.emptySet());
    final Set<String> setToSave = new HashSet<>(checkedSets);
    setToSave.add(pageType);
    perInstallationPrefs.edit()
        .putStringSet(Constants.PREF_CHECKED_PARTIAL_IMAGES, setToSave).apply();
  }
}
