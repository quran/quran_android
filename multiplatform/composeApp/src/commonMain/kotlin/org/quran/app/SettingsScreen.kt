package org.quran.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import org.quran.app.designsystem.Action
import org.quran.app.designsystem.PaperCard
import org.quran.app.designsystem.QuranStrings
import org.quran.app.designsystem.ScreenTitle
import org.quran.app.designsystem.appString
import org.quran.app.designsystem.label
import org.quran.app.model.AppLanguage
import org.quran.app.model.StudyProgress
import org.quran.app.model.TranslationEdition
import org.quran.app.model.VerseId
import org.quran.app.translations.TranslationSettingsSection

@Composable
internal fun SettingsScreen(
    progress: StudyProgress,
    onProgressChange: (StudyProgress) -> Unit,
    onOpenVerse: (VerseId) -> Unit,
    translationEditions: List<TranslationEdition>,
    selectedTranslationId: String?,
    isTranslationCatalogLoading: Boolean,
    translationCatalogError: String?,
    onRefreshTranslationCatalog: () -> Unit,
    onTranslationSelected: (String?) -> Unit,
) {
    val language = progress.language
    val uriHandler = LocalUriHandler.current

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            ScreenTitle(
                appString(QuranStrings.settingsTitle),
                appString(QuranStrings.settingsSubtitle),
            )
        }
        item {
            PaperCard {
                Text(appString(QuranStrings.language), style = MaterialTheme.typography.titleLarge)
                Row {
                    TextButton({ onProgressChange(progress.copy(language = AppLanguage.ENGLISH)) }) { Text(appString(QuranStrings.english)) }
                    TextButton({ onProgressChange(progress.copy(language = AppLanguage.ARABIC)) }) { Text(appString(QuranStrings.arabic)) }
                }
                Text(appString(QuranStrings.childrenMode), style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(progress.childMode, { onProgressChange(progress.copy(childMode = it)) })
                    Text(appString(QuranStrings.childrenModeDetail))
                }
                Text(appString(QuranStrings.localProgress))
                Text(appString(QuranStrings.memorizedCount, progress.memorized.size))
            }
        }
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
        item { Text(appString(QuranStrings.savedVerses), style = MaterialTheme.typography.titleLarge) }
        if (progress.bookmarks.isEmpty()) {
            item { Text(label(language, "Save a verse while reading to find it here.", "احفظ آية أثناء القراءة لتجدها هنا.")) }
        }
        items(progress.bookmarks.sortedWith(compareBy({ it.surah }, { it.ayah }))) { verseId ->
            PaperCard {
                Text("${verseId.surah}:${verseId.ayah}")
                Action(appString(QuranStrings.openVerse), { onOpenVerse(verseId) })
                TextButton({ onProgressChange(progress.copy(bookmarks = progress.bookmarks - verseId)) }) {
                    Text(appString(QuranStrings.removeBookmark))
                }
            }
        }
        item { Text(appString(QuranStrings.memorizedVerses), style = MaterialTheme.typography.titleLarge) }
        if (progress.memorized.isEmpty()) {
            item { Text(label(language, "Mark a verse memorized after checking your recitation.", "حدد الآية محفوظة بعد مراجعة تلاوتك.")) }
        }
        items(progress.memorized.sortedWith(compareBy({ it.surah }, { it.ayah })), key = { "memorized-${it.surah}-${it.ayah}" }) { verseId ->
            PaperCard {
                Text("${verseId.surah}:${verseId.ayah}")
                Action(appString(QuranStrings.reviewVerse), { onOpenVerse(verseId) })
                TextButton({ onProgressChange(progress.copy(memorized = progress.memorized - verseId)) }) {
                    Text(appString(QuranStrings.needsPractice))
                }
            }
        }
        item {
            PaperCard {
                Text(appString(QuranStrings.textSource), style = MaterialTheme.typography.titleLarge)
                Text("Tanzil Quran Text (Uthmani 1.1) © 2007–2026 Tanzil Project. CC BY 3.0. Verbatim text; modification prohibited.")
                TextButton({ uriHandler.openUri("https://tanzil.net") }) {
                    Text(appString(QuranStrings.tanzilUpdates))
                }
                Text(appString(QuranStrings.futureAi))
            }
        }
    }
}
