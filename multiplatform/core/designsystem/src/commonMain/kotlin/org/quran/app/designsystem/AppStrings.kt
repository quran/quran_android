package org.quran.app.designsystem

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import qurankmp.core.designsystem.generated.resources.*

/**
 * Shared, type-safe UI strings. Values live in composeResources/values/strings.xml
 * and composeResources/values-ar/strings.xml so Android and iOS share one catalogue.
 */
@Composable
fun appString(resource: StringResource): String = stringResource(resource)

@Composable
fun appString(resource: StringResource, vararg arguments: Any): String = stringResource(resource, *arguments)

object QuranStrings {
    val readingPreferences get() = Res.string.reading_preferences
    val readingPreferencesHelp get() = Res.string.reading_preferences_help
    val arabicTextSize get() = Res.string.arabic_text_size
    val translationTextSize get() = Res.string.translation_text_size
    val textSizeDefault get() = Res.string.text_size_default
    val textSizeLarge get() = Res.string.text_size_large
    val childrenLargeText get() = Res.string.children_large_text

    val practiceRange get() = Res.string.practice_range
    val practiceRangeHelp get() = Res.string.practice_range_help
    val practiceRangeAddress get() = Res.string.practice_range_address
    val decreaseRange get() = Res.string.decrease_range
    val increaseRange get() = Res.string.increase_range
    val practiceRangeTitle get() = Res.string.practice_range_title
    val downloadRange get() = Res.string.download_range
    val useCachedRange get() = Res.string.use_cached_range
    val audioDownloadProgress get() = Res.string.audio_download_progress
    val rangeAudioHelp get() = Res.string.range_audio_help
    val repeatUntilHelp get() = Res.string.repeat_until_help
    val importSingleAyahHelp get() = Res.string.import_single_ayah_help
    val practiceCurrentPosition get() = Res.string.practice_current_position

    val reciterAudio get() = Res.string.reciter_audio
    val downloadAyah get() = Res.string.download_ayah
    val useCachedRecitation get() = Res.string.use_cached_recitation
    val audioDownloading get() = Res.string.audio_downloading
    val audioDownloadFailed get() = Res.string.audio_download_failed
    val audioCacheHelp get() = Res.string.audio_cache_help
    val practiceAyah get() = Res.string.practice_ayah
    val audioSourceCredit get() = Res.string.audio_source_credit

    val translationCached get() = Res.string.translation_cached
    val translationOlderCached get() = Res.string.translation_older_cached
    val decreaseRepetitions get() = Res.string.decrease_repetitions
    val increaseRepetitions get() = Res.string.increase_repetitions
    val translationCatalogUnavailable get() = Res.string.translation_catalog_unavailable
    val translationLoadFailed get() = Res.string.translation_load_failed
    val appName get() = Res.string.app_name
    val libraryTitle get() = Res.string.library_title
    val librarySubtitle get() = Res.string.library_subtitle
    val continueReading get() = Res.string.continue_reading
    val openLastRead get() = Res.string.open_last_read
    val searchSurah get() = Res.string.search_surah
    val surahTab get() = Res.string.surah_tab
    val juzTab get() = Res.string.juz_tab
    val juzTitle get() = Res.string.juz_title
    val juzStartsAt get() = Res.string.juz_starts_at
    val readJuz get() = Res.string.read_juz
    val bookmarksTab get() = Res.string.bookmarks_tab
    val read get() = Res.string.read
    val verseCount get() = Res.string.verse_count
    val availableOffline get() = Res.string.available_offline
    val readerOfflineText get() = Res.string.reader_offline_text
    val currentReadingPosition get() = Res.string.current_reading_position
    val saveBookmark get() = Res.string.save_bookmark
    val removeBookmark get() = Res.string.remove_bookmark
    val saved get() = Res.string.saved
    val practice get() = Res.string.practice
    val study get() = Res.string.study
    val moreActions get() = Res.string.more_actions
    val markAsRead get() = Res.string.mark_as_read
    val back get() = Res.string.back
    val qibla get() = Res.string.qibla
    val library get() = Res.string.library
    val settings get() = Res.string.settings
    val noBookmarks get() = Res.string.no_bookmarks
    val bookmarkHelp get() = Res.string.bookmark_help
    val settingsTitle get() = Res.string.settings_title
    val settingsSubtitle get() = Res.string.settings_subtitle
    val language get() = Res.string.language
    val english get() = Res.string.english
    val arabic get() = Res.string.arabic
    val childrenMode get() = Res.string.children_mode
    val childrenModeDetail get() = Res.string.children_mode_detail
    val localProgress get() = Res.string.local_progress
    val memorizedCount get() = Res.string.memorized_count
    val savedVerses get() = Res.string.saved_verses
    val openVerse get() = Res.string.open_verse
    val memorizedVerses get() = Res.string.memorized_verses
    val reviewVerse get() = Res.string.review_verse
    val needsPractice get() = Res.string.needs_practice
    val textSource get() = Res.string.text_source
    val tanzilUpdates get() = Res.string.tanzil_updates
    val futureAi get() = Res.string.future_ai
    val memorizeOneAyah get() = Res.string.memorize_one_ayah
    val reciteFromMemory get() = Res.string.recite_from_memory
    val revealAyah get() = Res.string.reveal_ayah
    val hideAyah get() = Res.string.hide_ayah
    val repeatUntilMemorized get() = Res.string.repeat_until_memorized
    val iMemorizedThis get() = Res.string.i_memorized_this
    val sessionComplete get() = Res.string.session_complete
    val startAgain get() = Res.string.start_again
    val translationMeanings get() = Res.string.translation_meanings
    val arabicOnly get() = Res.string.arabic_only
    val chooseEdition get() = Res.string.choose_edition
    val translationListLoading get() = Res.string.translation_list_loading
    val refreshList get() = Res.string.refresh_list
    val translationInterpretation get() = Res.string.translation_interpretation
    val chooseTranslation get() = Res.string.choose_translation
    val languageOrTranslator get() = Res.string.language_or_translator
    val close get() = Res.string.close
    val sourceAndLicense get() = Res.string.source_and_license
    val translationLoading get() = Res.string.translation_loading
    val retry get() = Res.string.retry
    val chooseTranslationHelp get() = Res.string.choose_translation_help
    val recordingPlaybackError get() = Res.string.recording_playback_error
    val repeatedVerse get() = Res.string.repeated_verse
    val ownRecitation get() = Res.string.own_recitation
    val importRecordingHelp get() = Res.string.import_recording_help
    val importRecording get() = Res.string.import_recording
    val recordingImportError get() = Res.string.recording_import_error
    val faceQibla get() = Res.string.face_qibla
    val trueNorthBearing get() = Res.string.true_north_bearing
    val manualCoordinatesHelp get() = Res.string.manual_coordinates_help
    val latitude get() = Res.string.latitude
    val longitude get() = Res.string.longitude
    val calculateDirection get() = Res.string.calculate_direction
    val undefinedDirection get() = Res.string.undefined_direction
    val invalidCoordinates get() = Res.string.invalid_coordinates
    val liveCompass get() = Res.string.live_compass
    val compassCalibrationHelp get() = Res.string.compass_calibration_help
    val confirmDirectionHelp get() = Res.string.confirm_direction_help
    val studyReadStep get() = Res.string.study_read_step
    val studyReciteStep get() = Res.string.study_recite_step
    val studyPracticeStep get() = Res.string.study_practice_step
    val guidedStudy get() = Res.string.guided_study
    val aiLearningAssistant get() = Res.string.ai_learning_assistant
    val savedVersesHelp get() = Res.string.saved_verses_help
    val memorizedHelp get() = Res.string.memorized_help
    val hideNotes get() = Res.string.hide_notes
    val showNotes get() = Res.string.show_notes
    val selfRecitation get() = Res.string.self_recitation
    val repetitions get() = Res.string.repetitions
    val pause get() = Res.string.pause
    val playAndRepeat get() = Res.string.play_and_repeat
    val bearingResult get() = Res.string.bearing_result
    val stopCompass get() = Res.string.stop_compass
    val startCompass get() = Res.string.start_compass
    val turnLeft get() = Res.string.turn_left
    val turnRight get() = Res.string.turn_right
    val headingUnavailable get() = Res.string.heading_unavailable
    val headingWaiting get() = Res.string.heading_waiting
    val restartGuidance get() = Res.string.restart_guidance
    val nextStep get() = Res.string.next_step
    val childrenGuidanceHelp get() = Res.string.children_guidance_help
    val aiDeferredHelp get() = Res.string.ai_deferred_help
    val translationEditionTitle get() = Res.string.translation_edition_title
    val translationEditionMetadata get() = Res.string.translation_edition_metadata
    val translationSourceMetadata get() = Res.string.translation_source_metadata
    val downloadsTitle get() = Res.string.downloads_title
    val downloadsSubtitle get() = Res.string.downloads_subtitle
    val downloadsSettingsHelp get() = Res.string.downloads_settings_help
    val manageDownloads get() = Res.string.manage_downloads
    val storageUsage get() = Res.string.storage_usage
    val storageUsageValue get() = Res.string.storage_usage_value
    val noDownloads get() = Res.string.no_downloads
    val refreshDownloads get() = Res.string.refresh_downloads
    val removeDownload get() = Res.string.remove_download
    val removingDownload get() = Res.string.removing_download
    val downloadVerseAddress get() = Res.string.download_verse_address
    val downloadItemSize get() = Res.string.download_item_size
    val downloadRemoved get() = Res.string.download_removed
    val downloadAlreadyGone get() = Res.string.download_already_gone
    val downloadRemovalFailed get() = Res.string.download_removal_failed
    val removeDownloadDescription get() = Res.string.remove_download_description
    val downloadInUse get() = Res.string.download_in_use
    val downloadManagementFailed get() = Res.string.download_management_failed
    val reciterAlafasy get() = Res.string.reciter_alafasy
    val reciterAlafasyArabic get() = Res.string.reciter_alafasy_arabic
    val reciterHusary get() = Res.string.reciter_husary
    val reciterHusaryArabic get() = Res.string.reciter_husary_arabic
    val reciterSudais get() = Res.string.reciter_sudais
    val reciterSudaisArabic get() = Res.string.reciter_sudais_arabic
    val readerAudioTitle get() = Res.string.reader_audio_title
    val readerAudioChooseVerse get() = Res.string.reader_audio_choose_verse
    val readerAudioVerse get() = Res.string.reader_audio_verse
    val readerAudioReciter get() = Res.string.reader_audio_reciter
    val readerAudioPreparing get() = Res.string.reader_audio_preparing
    val readerAudioPlaying get() = Res.string.reader_audio_playing
    val readerAudioReady get() = Res.string.reader_audio_ready
    val readerAudioFailed get() = Res.string.reader_audio_failed
    val readerAudioListen get() = Res.string.reader_audio_listen
    val readerAudioRetry get() = Res.string.reader_audio_retry
    val readerAudioPlay get() = Res.string.reader_audio_play
    val readerAudioPause get() = Res.string.reader_audio_pause
    val readerAudioStop get() = Res.string.reader_audio_stop
}
