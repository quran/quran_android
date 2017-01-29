package com.quran.labs.androidquran.data;

public class Constants {

  // data domain
  public static final String HOST = "http://android.quran.com/";

  // Numerics
  public static final int DEFAULT_NIGHT_MODE_TEXT_BRIGHTNESS = 255;
  public static final int DEFAULT_TEXT_SIZE = 15;

  // 10 days in ms
  public static final int TRANSLATION_REFRESH_TIME = 60 * 60 * 24 * 10 * 1000;

  // 1 hour in ms
  public static final int MIN_TRANSLATION_REFRESH_TIME = 60 * 60 * 1000;

  // Pages
  public static final int PAGES_FIRST = 1;
  public static final int PAGES_LAST = QuranConstants.NUMBER_OF_PAGES;
  public static final int PAGES_LAST_DUAL = PAGES_LAST / 2;
  public static final int SURA_FIRST = 1;
  public static final int SURA_LAST = 114;
  public static final int SURAS_COUNT = 114;
  public static final int JUZ2_COUNT = 30;
  public static final int AYA_MIN = 1;
  public static final int AYA_MAX = 286;
  public static final int NO_PAGE = -1;
  public static final int MAX_RECENT_PAGES = 3;

  // quranapp
  public static final String QURAN_APP_BASE = "http://quranapp.com/";
  public static final String QURAN_APP_ENDPOINT = "http://quranapp.com/note";

  // Settings Key (some of these have corresponding values in preference_keys.xml)
  public static final String PREF_APP_LOCATION = "appLocation";
  public static final String PREF_USE_ARABIC_NAMES = "useArabicNames";
  public static final String PREF_LAST_PAGE = "lastPage";
  public static final String PREF_LOCK_ORIENTATION = "lockOrientation";
  public static final String PREF_LANDSCAPE_ORIENTATION =
      "landscapeOrientation";
  public static final String PREF_TRANSLATION_TEXT_SIZE = "translationTextSize";
  public static final String PREF_ACTIVE_TRANSLATION = "activeTranslation";
  public static final String PREF_ACTIVE_TRANSLATIONS = "activeTranslations";
  public static final String PREF_NIGHT_MODE = "nightMode";
  public static final String PREF_NIGHT_MODE_TEXT_BRIGHTNESS = "nightModeTextBrightness";
  public static final String PREF_DEFAULT_QARI = "defaultQari";
  public static final String PREF_SHOULD_FETCH_PAGES = "shouldFetchPages";
  public static final String PREF_OVERLAY_PAGE_INFO = "overlayPageInfo";
  public static final String PREF_DISPLAY_MARKER_POPUP = "displayMarkerPopup";
  public static final String PREF_HIGHLIGHT_BOOKMARKS = "highlightBookmarks";
  public static final String PREF_AYAH_BEFORE_TRANSLATION =
      "ayahBeforeTranslation";
  public static final String PREF_PREFER_STREAMING = "preferStreaming";
  public static final String PREF_DOWNLOAD_AMOUNT = "preferredDownloadAmount";
  public static final String PREF_LAST_UPDATED_TRANSLATIONS =
      "lastTranslationsUpdate";
  public static final String PREF_HAVE_UPDATED_TRANSLATIONS =
      "haveUpdatedTranslations";
  public static final String PREF_USE_NEW_BACKGROUND = "useNewBackground";
  public static final String PREF_USE_VOLUME_KEY_NAV = "volumeKeyNavigation";
  public static final String PREF_SORT_BOOKMARKS = "sortBookmarks";
  public static final String PREF_GROUP_BOOKMARKS_BY_TAG = "groupBookmarksByTag";
  public static final String PREF_SHOW_RECENTS = "showRecents";
  public static final String PREF_DISPLAY_CATEGORY = "displayCategoryKey";
  public static final String PREF_TABLET_ENABLED = "useTabletMode";
  public static final String PREF_VERSION = "version";
  public static final String PREF_DEFAULT_IMAGES_DIR = "defaultImagesDir";
  public static final String PREF_TRANSLATION_MANAGER = "translationManagerKey";
  public static final String PREF_AUDIO_MANAGER = "audioManagerKey";
  public static final String PREF_IMPORT = "importKey";
  public static final String PREF_EXPORT = "exportKey";
  public static final String PREF_LOGS = "sendLogsKey";
  public static final String PREF_DID_PRESENT_PERMISSIONS_DIALOG =
      "didPresentStoragePermissionDialog";
  public static final String PREF_WAS_SHOWING_TRANSLATION = "wasShowingTranslation";
  public static final String PREF_QURAN_SETTINGS = "quranSettings";
}
