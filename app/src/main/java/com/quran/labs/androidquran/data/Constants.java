package com.quran.labs.androidquran.data;

import com.quran.data.core.QuranConstants;

public class Constants {

  // data domain
  public static final String HOST = "https://android.quran.com/";

  // Numerics
  public static final int DEFAULT_NIGHT_MODE_TEXT_BRIGHTNESS = 255;
  public static final int DEFAULT_TEXT_SIZE = 15;

  // 10 days in ms
  public static final int TRANSLATION_REFRESH_TIME = 60 * 60 * 24 * 10 * 1000;

  // 1 hour in ms
  public static final int MIN_TRANSLATION_REFRESH_TIME = 60 * 60 * 1000;

  // Pages
  public static final int PAGES_FIRST = QuranConstants.PAGES_FIRST;
  public static final int SURA_FIRST = QuranConstants.FIRST_SURA;
  public static final int SURA_LAST = QuranConstants.LAST_SURA;
  public static final int SURAS_COUNT = QuranConstants.NUMBER_OF_SURAS;
  public static final int JUZ2_COUNT = QuranConstants.JUZ2_COUNT;
  public static final int AYA_MIN = QuranConstants.MIN_AYAH;
  public static final int AYA_MAX = QuranConstants.MAX_AYAH;
  public static final int NO_PAGE = -1;
  public static final int MAX_RECENT_PAGES = 3;

  // quranapp
  public static final String QURAN_APP_BASE = "http://quranapp.com/";
  public static final String QURAN_APP_ENDPOINT = "http://quranapp.com/note";

  // Notification Ids
  public static final int NOTIFICATION_ID_DOWNLOADING = 1;
  public static final int NOTIFICATION_ID_DOWNLOADING_COMPLETE = 2;
  public static final int NOTIFICATION_ID_DOWNLOADING_ERROR = 3;
  public static final int NOTIFICATION_ID_AUDIO_PLAYBACK = 4;
  public static final int NOTIFICATION_ID_AUDIO_UPDATE = 5;

  // Notification channels
  public static final String AUDIO_CHANNEL = "quran_audio_playback";
  public static final String DOWNLOAD_CHANNEL = "quran_download_progress";

  // Unique work names
  public static final String AUDIO_UPDATE_UNIQUE_WORK = "audio_update_unique_work";

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
  public static final String PREF_SPLIT_PAGE_AND_TRANSLATION =
      "splitPageAndTranslation";
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
  public static final String PREF_SHOW_DATE = "showDate";
  public static final String PREF_DUAL_PAGE_ENABLED = "useDualPageMode";
  public static final String PREF_VERSION = "version";
  public static final String PREF_DEFAULT_IMAGES_DIR = "defaultImagesDir";
  public static final String PREF_TRANSLATION_MANAGER = "translationManagerKey";
  public static final String PREF_AUDIO_MANAGER = "audioManagerKey";
  public static final String PREF_PAGE_TYPE = "pageTypeKey";
  public static final String PREF_IMPORT = "importKey";
  public static final String PREF_EXPORT = "exportKey";
  public static final String PREF_LOGS = "sendLogsKey";
  public static final String PREF_DID_PRESENT_PERMISSIONS_DIALOG =
      "didPresentStoragePermissionDialog";
  public static final String PREF_WAS_SHOWING_TRANSLATION = "wasShowingTranslation";
  public static final String PREF_ADVANCED_QURAN_SETTINGS = "quranAdvancedSettings";
  public static final String DEBUG_DID_DOWNLOAD_PAGES = "debugDidDownloadPages";
  public static final String DEBUG_PAGE_DOWNLOADED_PATH = "debugPageDownloadedPath";
  public static final String DEBUG_PAGES_DOWNLOADED_TIME = "debugPagesDownloadedTime";
  public static final String DEBUG_PAGES_DOWNLOADED = "debugPagesDownloaded";
  public static final String PREF_READING_CATEGORY = "readingCategoryKey";
  public static final String PREF_CHECKED_PARTIAL_IMAGES = "didCheckPartialImages";
  public static final String PREF_CURRENT_AUDIO_REVISION = "currentAudioRevision";
  public static final String PREF_SURA_TRANSLATED_NAME = "suraTranslatedName";
}
