package org.quran.app

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import org.quran.app.designsystem.ScreenWrapper
import org.quran.app.designsystem.QuranStrings
import org.quran.app.designsystem.appString
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import org.quran.app.data.StoredReadingPreferencesStore
import org.quran.app.data.RecitationDataModule
import org.quran.app.data.StoredReciterSelectionStore
import org.quran.app.data.BundledQuranRepository
import org.quran.app.data.StoredProgressRepository
import org.quran.app.data.StoredTranslationSelectionStore
import org.quran.app.data.platformCompassProvider
import org.quran.app.data.TranslationDataModule
import org.quran.app.designsystem.QuranTheme
import org.quran.app.domain.AudioPlayer
import org.quran.app.domain.SettingsStore
import org.quran.app.memorization.MemorizationScreen
import org.quran.app.model.StudyProgress
import org.quran.app.model.TranslationEdition
import org.quran.app.qibla.QiblaScreen
import org.quran.app.reader.LibraryScreen
import org.quran.app.tutor.TutorScreen

@Composable
fun QuranApp(
    settings: SettingsStore,
    audioPlayer: AudioPlayer,
    importAudio: ((String) -> Unit) -> Unit,
) {
    val recitationModule = remember { RecitationDataModule() }
    val reciterSelection = remember(settings) { StoredReciterSelectionStore(settings) }
    var selectedReciterId by remember(settings) { mutableStateOf(reciterSelection.selectedReciterId()) }
    DisposableEffect(recitationModule) { onDispose { recitationModule.close() } }
    val readingPreferencesStore = remember(settings) { StoredReadingPreferencesStore(settings) }
    var readingPreferences by remember(settings) { mutableStateOf(readingPreferencesStore.read()) }
    val quran = remember { BundledQuranRepository() }
    val progressRepository = remember(settings) { StoredProgressRepository(settings) }
    val translationSelection = remember(settings) { StoredTranslationSelectionStore(settings) }
    val translationModule = remember(settings) { TranslationDataModule(settings) }
    val translationRepository = translationModule.repository
    DisposableEffect(translationModule) { onDispose { translationModule.close() } }

    var progress by remember { mutableStateOf(progressRepository.read()) }
    var selectedTranslationId by remember { mutableStateOf(translationSelection.selectedEditionId()) }
    var translationEditions by remember { mutableStateOf(emptyList<TranslationEdition>()) }
    var isTranslationCatalogLoading by remember { mutableStateOf(false) }
    var translationCatalogFailed by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val navigationStack = rememberNavBackStack(navigationConfig, Library)
    val compass = remember { platformCompassProvider() }

    fun saveProgress(updated: StudyProgress) {
        progressRepository.save(updated)
        progress = updated
    }

    fun refreshTranslationCatalog() {
        coroutineScope.launch {
            isTranslationCatalogLoading = true
            translationCatalogFailed = false
            try {
                translationEditions = translationRepository.editions()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                translationCatalogFailed = true
            } finally {
                isTranslationCatalogLoading = false
            }
        }
    }

    LaunchedEffect(translationRepository) { refreshTranslationCatalog() }

    QuranTheme {
        CompositionLocalProvider(
            LocalLayoutDirection provides if (progress.language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            AppLocale provides progress.language,
        ) {
            ScreenWrapper(
                title = appString(QuranStrings.appName),
                onBack = if (navigationStack.size > 1) ({ navigationStack.removeAt(navigationStack.lastIndex); Unit }) else null,
                bottomBar = {
                    AppBottomBar(navigationStack.last()) { destination ->
                        navigationStack.clear()
                        navigationStack.add(destination)
                    }
                },
            ) { insets ->
                Box(Modifier.padding(insets).padding(horizontal = 20.dp).fillMaxSize()) {
                    NavDisplay(
                        backStack = navigationStack,
                        onBack = { if (navigationStack.size > 1) navigationStack.removeAt(navigationStack.lastIndex) },
                        entryProvider = entryProvider {
                            entry<Library> {
                                LibraryScreen(chapters = quran.chapters(), progress = progress, juzs = quran.juzs()) { surah, ayah ->
                                    navigationStack.add(Reader(surah, ayah))
                                }
                            }
                            entry<Reader> { route ->
                                ReaderEntry(
                                    route = route,
                                    readingPreferences = readingPreferences,
                                    quran = quran,
                                    progress = progress,
                                    selectedTranslationId = selectedTranslationId,
                                    translationEditions = translationEditions,
                                    translationRepository = translationRepository,
                                    onRead = { saveProgress(progress.copy(lastRead = it)) },
                                    onBookmark = { verse ->
                                        val bookmarks = if (verse in progress.bookmarks) {
                                            progress.bookmarks - verse
                                        } else {
                                            progress.bookmarks + verse
                                        }
                                        saveProgress(progress.copy(bookmarks = bookmarks))
                                    },
                                    onPractice = { navigationStack.add(Practice(it.surah, it.ayah)) },
                                    onStudy = { navigationStack.add(Study(it.surah, it.ayah)) },
                                )
                            }
                            entry<Practice> { route ->
                                MemorizationScreen(
                                    verse = quran.verses(route.surah)[route.ayah - 1],
                                    chapterVerses = quran.verses(route.surah),
                                    progress = progress,
                                    onMemorized = { saveProgress(progress.copy(memorized = progress.memorized + it)) },
                                    audioPlayer = audioPlayer,
                                    onImport = importAudio,
                                    recitationRepository = recitationModule.repository,
                                    recitationStorage = recitationModule.storage,
                                    selectedReciterId = selectedReciterId,
                                    onReciterSelected = { id -> reciterSelection.select(id); selectedReciterId = id },
                                )
                            }
                            entry<Study> { route ->
                                TutorScreen(quran.verses(route.surah)[route.ayah - 1], progress)
                            }
                            entry<Downloads> {
                                DownloadsEntry(recitationModule.storage, progress.language)
                            }
                            entry<Qibla> { QiblaScreen(progress.language, compass) }
                            entry<Settings> {
                                SettingsScreen(
                                    progress = progress,
                                    onProgressChange = ::saveProgress,
                                    readingPreferences = readingPreferences,
                                    onReadingPreferencesChanged = { updated -> readingPreferencesStore.save(updated); readingPreferences = updated },
                                    onOpenVerse = { navigationStack.add(Reader(it.surah, it.ayah)) },
                                    translationEditions = translationEditions,
                                    selectedTranslationId = selectedTranslationId,
                                    isTranslationCatalogLoading = isTranslationCatalogLoading,
                                    translationCatalogError = if (translationCatalogFailed) appString(QuranStrings.translationCatalogUnavailable) else null,
                                    onManageDownloads = { navigationStack.add(Downloads) },
                                    onRefreshTranslationCatalog = ::refreshTranslationCatalog,
                                    onTranslationSelected = { editionId ->
                                        translationSelection.selectEdition(editionId)
                                        selectedTranslationId = editionId
                                    },
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
