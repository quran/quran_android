package org.quran.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import org.quran.app.designsystem.QuranText
import org.quran.app.designsystem.QuranTextVariant
import org.quran.app.designsystem.QuranTextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import org.quran.app.designsystem.Action
import org.quran.app.designsystem.PaperCard
import org.quran.app.designsystem.QuranStrings
import org.quran.app.designsystem.ScreenTitle
import org.quran.app.designsystem.appString
import org.quran.app.model.ReadingPreferences
import org.quran.app.model.StudyProgress
import org.quran.app.model.TranslationEdition
import org.quran.app.model.VerseId
import org.quran.app.translations.TranslationSettingsSection

@Composable
internal fun SettingsScreen(
    progress: StudyProgress,
    onProgressChange: (StudyProgress) -> Unit,
    onOpenVerse: (VerseId) -> Unit,
    onPracticeVerse: (VerseId) -> Unit,
    translationEditions: List<TranslationEdition>,
    selectedTranslationId: String?,
    isTranslationCatalogLoading: Boolean,
    translationCatalogError: String?,
    onRefreshTranslationCatalog: () -> Unit,
    onTranslationSelected: (String?) -> Unit,
    readingPreferences: ReadingPreferences,
    onReadingPreferencesChanged: (ReadingPreferences) -> Unit,
    onManageDownloads: () -> Unit,
) {
    val language = progress.language
    val uriHandler = LocalUriHandler.current

    LazyColumn(modifier = Modifier.testTag("settings_list"), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            ScreenTitle(
                appString(QuranStrings.settingsTitle),
                appString(QuranStrings.settingsSubtitle),
            )
        }
        item { SettingsPreferencesCard(progress, onProgressChange) }
        item { ReadingPreferencesCard(readingPreferences, progress.childMode, onReadingPreferencesChanged) }
        item { SettingsDownloadsCard(onManageDownloads) }
        item {
            TranslationSettingsSection(
                language = language,
                editions = translationEditions,
                selectedEditionId = selectedTranslationId,
                isLoading = isTranslationCatalogLoading,
                errorMessage = translationCatalogError,
                onRefresh = onRefreshTranslationCatalog,
                onSelect = onTranslationSelected,
            )
        }
        item { QuranText(appString(QuranStrings.savedVerses), variant = QuranTextVariant.Title) }
        if (progress.bookmarks.isEmpty()) {
            item { QuranText(appString(QuranStrings.savedVersesHelp)) }
        }
        items(progress.bookmarks.sortedWith(compareBy({ it.surah }, { it.ayah }))) { verseId ->
            PaperCard {
                QuranText("${verseId.surah}:${verseId.ayah}")
                Action(appString(QuranStrings.openVerse), { onOpenVerse(verseId) })
                QuranTextButton(appString(QuranStrings.removeBookmark), onClick = { onProgressChange(progress.copy(bookmarks = progress.bookmarks - verseId)) })
            }
        }
        item { QuranText(appString(QuranStrings.memorizedVerses), variant = QuranTextVariant.Title) }
        if (progress.memorized.isEmpty()) {
            item { QuranText(appString(QuranStrings.memorizedHelp)) }
        }
        items(progress.memorized.sortedWith(compareBy({ it.surah }, { it.ayah })), key = { "memorized-${it.surah}-${it.ayah}" }) { verseId ->
            SettingsMemorizedVerseCard(
                verseId = verseId,
                onRead = onOpenVerse,
                onReview = onPracticeVerse,
                onNeedsPractice = { reviewedVerse ->
                    onProgressChange(progress.copy(memorized = progress.memorized - reviewedVerse))
                },
            )
        }
        item {
            PaperCard {
                QuranText(appString(QuranStrings.textSource), variant = QuranTextVariant.Title)
                QuranText("Tanzil Quran Text (Uthmani 1.1) © 2007–2026 Tanzil Project. CC BY 3.0. Verbatim text; modification prohibited.")
                QuranTextButton(appString(QuranStrings.tanzilUpdates), onClick = { uriHandler.openUri("https://tanzil.net") })
                QuranText(appString(QuranStrings.futureAi))
            }
        }
    }
}
