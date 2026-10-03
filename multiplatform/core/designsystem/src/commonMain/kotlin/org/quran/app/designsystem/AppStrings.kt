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
    val appName get() = Res.string.app_name
    val libraryTitle get() = Res.string.library_title
    val librarySubtitle get() = Res.string.library_subtitle
    val continueReading get() = Res.string.continue_reading
    val openLastRead get() = Res.string.open_last_read
    val searchSurah get() = Res.string.search_surah
    val surahTab get() = Res.string.surah_tab
    val juzTab get() = Res.string.juz_tab
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
    val juzBrowseComing get() = Res.string.juz_browse_coming
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
}
