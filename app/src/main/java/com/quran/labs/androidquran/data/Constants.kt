package com.quran.labs.androidquran.data

import com.quran.data.core.QuranConstants

object Constants {

  // data domain
  const val HOST = "https://quran.app/"

  // Numerics
  const val DEFAULT_NIGHT_MODE_TEXT_BRIGHTNESS = 255
  const val DEFAULT_NIGHT_MODE_BACKGROUND_BRIGHTNESS = 0
  const val DEFAULT_TEXT_SIZE = 15

  // 10 days in ms
  const val TRANSLATION_REFRESH_TIME = 60 * 60 * 24 * 10 * 1000

  // 1 hour in ms
  const val MIN_TRANSLATION_REFRESH_TIME = 60 * 60 * 1000

  // Pages
  const val PAGES_FIRST = QuranConstants.PAGES_FIRST
  const val SURA_FIRST = QuranConstants.FIRST_SURA
  const val SURA_LAST = QuranConstants.LAST_SURA
  const val SURAS_COUNT = QuranConstants.NUMBER_OF_SURAS
  const val JUZ2_COUNT = QuranConstants.JUZ2_COUNT
  const val AYA_MIN = QuranConstants.MIN_AYAH
  const val AYA_MAX = QuranConstants.MAX_AYAH
  const val NO_PAGE = -1
  const val MAX_RECENT_PAGES = 3

  // quranapp
  const val QURAN_APP_BASE = "http://quranapp.com/"
  const val QURAN_APP_ENDPOINT = "http://quranapp.com/note"

  // Notification Ids
  const val NOTIFICATION_ID_DOWNLOADING = 1
  const val NOTIFICATION_ID_DOWNLOADING_COMPLETE = 2
  const val NOTIFICATION_ID_DOWNLOADING_ERROR = 3
  const val NOTIFICATION_ID_AUDIO_PLAYBACK = 4
  const val NOTIFICATION_ID_AUDIO_UPDATE = 5

  // Notification channels
  const val AUDIO_CHANNEL = "quran_audio_playback"
  const val DOWNLOAD_CHANNEL = "quran_download_progress"

  // Unique work names
  const val AUDIO_UPDATE_UNIQUE_WORK = "audio_update_unique_work"

  // Settings Key (some of these have corresponding values in preference_keys.xml)
  const val PREF_APP_LOCATION = "appLocation"
  const val PREF_USE_ARABIC_NAMES = "useArabicNames"
  const val PREF_LAST_PAGE = "lastPage"
  const val PREF_LOCK_ORIENTATION = "lockOrientation"
  const val PREF_LANDSCAPE_ORIENTATION = "landscapeOrientation"
  const val PREF_TRANSLATION_TEXT_SIZE = "translationTextSize"
  const val PREF_ACTIVE_TRANSLATION = "activeTranslation"
  const val PREF_ACTIVE_TRANSLATIONS = "activeTranslations"
  const val PREF_NIGHT_MODE = "nightMode"
  const val PREF_NIGHT_MODE_TEXT_BRIGHTNESS = "nightModeTextBrightness"
  const val PREF_NIGHT_MODE_BACKGROUND_BRIGHTNESS = "nightModeBackgroundBrightness"
  const val PREF_DEFAULT_QARI = "defaultQari"
  const val PREF_SHOULD_FETCH_PAGES = "shouldFetchPages"
  const val PREF_OVERLAY_PAGE_INFO = "overlayPageInfo"
  const val PREF_DISPLAY_MARKER_POPUP = "displayMarkerPopup"
  const val PREF_HIGHLIGHT_BOOKMARKS = "highlightBookmarks"
  const val PREF_AYAH_BEFORE_TRANSLATION = "ayahBeforeTranslation"
  const val PREF_USE_DYSLEXIC_FONT = "useDyslexicFont"
  const val PREF_SPLIT_PAGE_AND_TRANSLATION = "splitPageAndTranslation"
  const val PREF_PREFER_STREAMING = "preferStreaming"
  const val PREF_DOWNLOAD_AMOUNT = "preferredDownloadAmount"
  const val PREF_LAST_UPDATED_TRANSLATIONS = "lastTranslationsUpdate"
  const val PREF_HAVE_UPDATED_TRANSLATIONS = "haveUpdatedTranslations"
  const val PREF_USE_NEW_BACKGROUND = "useNewBackground"
  const val PREF_USE_VOLUME_KEY_NAV = "volumeKeyNavigation"
  const val PREF_SORT_BOOKMARKS = "sortBookmarks"
  const val PREF_GROUP_BOOKMARKS_BY_TAG = "groupBookmarksByTag"
  const val PREF_SHOW_RECENTS = "showRecents"
  const val PREF_SHOW_DATE = "showDate"
  const val PREF_DUAL_PAGE_ENABLED = "useDualPageMode"
  const val PREF_VERSION = "version"
  const val PREF_DEFAULT_IMAGES_DIR = "defaultImagesDir"
  const val PREF_TRANSLATION_MANAGER = "translationManagerKey"
  const val PREF_AUDIO_MANAGER = "audioManagerKey"
  const val PREF_PAGE_TYPE = "pageTypeKey"
  const val PREF_IMPORT = "importKey"
  const val PREF_EXPORT = "exportKey"
  const val PREF_EXPORT_CSV = "exportKeyCSV"
  const val PREF_LOGS = "sendLogsKey"
  const val PREF_DID_PRESENT_PERMISSIONS_DIALOG = "didPresentStoragePermissionDialog"
  const val PREF_WAS_SHOWING_TRANSLATION = "wasShowingTranslation"
  const val PREF_ADVANCED_QURAN_SETTINGS = "quranAdvancedSettings"
  const val DEBUG_DID_DOWNLOAD_PAGES = "debugDidDownloadPages"
  const val DEBUG_PAGE_DOWNLOADED_PATH = "debugPageDownloadedPath"
  const val DEBUG_PAGES_DOWNLOADED_TIME = "debugPagesDownloadedTime"
  const val DEBUG_PAGES_DOWNLOADED = "debugPagesDownloaded"
  const val PREF_READING_CATEGORY = "readingCategoryKey"
  const val PREF_CHECKED_PARTIAL_IMAGES = "didCheckPartialImages"
  const val PREF_CURRENT_AUDIO_REVISION = "currentAudioRevision"
  const val PREF_SURA_TRANSLATED_NAME = "suraTranslatedName"
  const val PREF_SHOW_SIDELINES = "showSidelines"
  const val PREF_SHOW_LINE_DIVIDERS = "showLineDividers"
}
