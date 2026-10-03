package org.quran.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.*
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.quran.app.data.BundledQuranRepository
import org.quran.app.data.StoredProgressRepository
import org.quran.app.data.StoredTranslationSelectionStore
import org.quran.app.data.platformCompassProvider
import org.quran.app.data.createTranslationRepository
import org.quran.app.designsystem.Ivory
import org.quran.app.designsystem.QuranStrings
import org.quran.app.designsystem.QuranTheme
import org.quran.app.designsystem.appString
import org.quran.app.domain.AudioPlayer
import org.quran.app.domain.SettingsStore
import org.quran.app.domain.TranslationRepository
import org.quran.app.memorization.MemorizationScreen
import org.quran.app.model.AppLanguage
import org.quran.app.model.StudyProgress
import org.quran.app.model.TranslationEdition
import org.quran.app.model.VerseId
import org.quran.app.model.VerseTranslation
import org.quran.app.qibla.QiblaScreen
import org.quran.app.reader.LibraryScreen
import org.quran.app.reader.ReaderScreen
import org.quran.app.translations.TranslationEditionHeader
import org.quran.app.translations.TranslationSettingsSection
import org.quran.app.translations.TranslationVerseText
import org.quran.app.tutor.TutorScreen

@Serializable
data object Library : NavKey

@Serializable
data class Reader(val surah: Int, val ayah: Int = 1) : NavKey

@Serializable
data class Practice(val surah: Int, val ayah: Int) : NavKey

@Serializable
data class Study(val surah: Int, val ayah: Int) : NavKey

@Serializable
data object Qibla : NavKey

@Serializable
data object Settings : NavKey

private val navigationConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Library::class, Library.serializer())
            subclass(Reader::class, Reader.serializer())
            subclass(Practice::class, Practice.serializer())
            subclass(Study::class, Study.serializer())
            subclass(Qibla::class, Qibla.serializer())
            subclass(Settings::class, Settings.serializer())
        }
    }
}

@Composable
fun QuranApp(
    settings: SettingsStore,
    audioPlayer: AudioPlayer,
    importAudio: ((String) -> Unit) -> Unit,
) {
    val quran = remember { BundledQuranRepository() }
    val progressRepository = remember(settings) { StoredProgressRepository(settings) }
    val translationSelection = remember(settings) { StoredTranslationSelectionStore(settings) }
    val translationRepository = remember(settings) {
        createTranslationRepository(settings)
    }

    var progress by remember { mutableStateOf(progressRepository.read()) }
    var selectedTranslationId by remember { mutableStateOf(translationSelection.selectedEditionId()) }
    var translationEditions by remember { mutableStateOf(emptyList<TranslationEdition>()) }
    var isTranslationCatalogLoading by remember { mutableStateOf(false) }
    var translationCatalogError by remember { mutableStateOf<String?>(null) }
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
            translationCatalogError = null
            try {
                translationEditions = translationRepository.editions()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                translationCatalogError = "Translation list is unavailable. Try again when online."
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
            Scaffold(
                containerColor = Ivory,
                topBar = {
                    AppTopBar(navigationStack.size, progress.language) {
                        if (navigationStack.size > 1) navigationStack.removeAt(navigationStack.lastIndex)
                    }
                },
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
                                LibraryScreen(quran.chapters(), progress) { surah, ayah ->
                                    navigationStack.add(Reader(surah, ayah))
                                }
                            }
                            entry<Reader> { route ->
                                ReaderEntry(
                                    route = route,
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
                                    progress = progress,
                                    onMemorized = { saveProgress(progress.copy(memorized = progress.memorized + it)) },
                                    audioPlayer = audioPlayer,
                                    onImport = importAudio,
                                )
                            }
                            entry<Study> { route ->
                                TutorScreen(quran.verses(route.surah)[route.ayah - 1], progress)
                            }
                            entry<Qibla> { QiblaScreen(progress.language, compass) }
                            entry<Settings> {
                                SettingsScreen(
                                    progress = progress,
                                    onProgressChange = ::saveProgress,
                                    onOpenVerse = { navigationStack.add(Reader(it.surah, it.ayah)) },
                                    translationEditions = translationEditions,
                                    selectedTranslationId = selectedTranslationId,
                                    isTranslationCatalogLoading = isTranslationCatalogLoading,
                                    translationCatalogError = translationCatalogError,
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

@Composable
private fun ReaderEntry(
    route: Reader,
    quran: BundledQuranRepository,
    progress: StudyProgress,
    selectedTranslationId: String?,
    translationEditions: List<TranslationEdition>,
    translationRepository: TranslationRepository,
    onRead: (VerseId) -> Unit,
    onBookmark: (VerseId) -> Unit,
    onPractice: (VerseId) -> Unit,
    onStudy: (VerseId) -> Unit,
) {
    var reloadToken by remember(route, selectedTranslationId) { mutableIntStateOf(0) }
    var translatedContent by remember(route, selectedTranslationId) {
        mutableStateOf(ReaderTranslationState())
    }

    LaunchedEffect(route.surah, selectedTranslationId, reloadToken) {
        if (selectedTranslationId == null) {
            translatedContent = ReaderTranslationState()
            return@LaunchedEffect
        }

        translatedContent = ReaderTranslationState(isLoading = true)
        try {
            val edition = translationEditions.firstOrNull { it.id == selectedTranslationId }
                ?: translationRepository.editions().firstOrNull { it.id == selectedTranslationId }
                ?: error("The selected translation is no longer available.")
            val verses = translationRepository.verses(edition, route.surah)
            translatedContent = ReaderTranslationState(
                edition = edition,
                verses = verses.associateBy { it.verseId },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            translatedContent = ReaderTranslationState(
                errorMessage = "Translation could not be loaded. Check your connection and try again.",
            )
        }
    }

    ReaderScreen(
        chapter = quran.chapters()[route.surah - 1],
        verses = quran.verses(route.surah),
        initialAyah = route.ayah,
        progress = progress,
        translationSummary = {
            TranslationEditionHeader(
                language = progress.language,
                edition = translatedContent.edition,
                isLoading = translatedContent.isLoading,
                errorMessage = translatedContent.errorMessage,
                onRetry = { reloadToken++ },
            )
        },
        translationForVerse = { verseId ->
            TranslationVerseText(
                language = progress.language,
                edition = translatedContent.edition,
                translation = translatedContent.verses[verseId],
            )
        },
        onRead = onRead,
        onBookmark = onBookmark,
        onPractice = onPractice,
        onStudy = onStudy,
    )
}

private data class ReaderTranslationState(
    val edition: TranslationEdition? = null,
    val verses: Map<VerseId, VerseTranslation> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

@Composable
private fun AppTopBar(stackSize: Int, language: AppLanguage, onBack: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = onBack) {
            Text(if (stackSize > 1) "‹ ${appString(QuranStrings.back)}" else appString(QuranStrings.appName))
        }
        Text(appString(QuranStrings.librarySubtitle), Modifier.padding(top = 14.dp))
    }
}

@Composable
private fun AppBottomBar(
    currentDestination: NavKey,
    onNavigate: (NavKey) -> Unit,
) {
    NavigationBar(containerColor = Ivory) {
        listOf(
            Library to appString(QuranStrings.library),
            Qibla to appString(QuranStrings.qibla),
            Settings to appString(QuranStrings.settings),
        ).forEach { (destination, title) ->
            NavigationBarItem(
                selected = currentDestination == destination,
                onClick = { onNavigate(destination) },
                icon = { Text(if (destination == Library) "۞" else if (destination == Qibla) "↗" else "◉") },
                label = { Text(title) },
            )
        }
    }
}
