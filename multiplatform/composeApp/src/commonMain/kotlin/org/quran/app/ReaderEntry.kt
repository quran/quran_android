package org.quran.app

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import org.quran.app.designsystem.QuranStrings
import org.quran.app.designsystem.appString
import org.quran.app.domain.AudioPlayer
import org.quran.app.domain.QuranRepository
import org.quran.app.domain.RecitationRepository
import org.quran.app.domain.RecitationStorageRepository
import org.quran.app.domain.TranslationRepository
import org.quran.app.model.ReadingPreferences
import org.quran.app.model.StudyProgress
import org.quran.app.model.TranslationEdition
import org.quran.app.model.VerseId
import org.quran.app.reader.ReaderScreen
import org.quran.app.translations.TranslationEditionHeader
import org.quran.app.translations.TranslationVerseText

@Composable
internal fun ReaderEntry(
    route: Reader,
    quran: QuranRepository,
    progress: StudyProgress,
    selectedTranslationId: String?,
    translationEditions: List<TranslationEdition>,
    translationRepository: TranslationRepository,
    onRead: (VerseId) -> Unit,
    onBookmark: (VerseId) -> Unit,
    onPractice: (VerseId) -> Unit,
    onStudy: (VerseId) -> Unit,
    readingPreferences: ReadingPreferences,
    audioPlayer: AudioPlayer,
    recitationRepository: RecitationRepository,
    recitationStorage: RecitationStorageRepository,
    selectedReciterId: String,
) {
    val listeningScope = rememberCoroutineScope()
    val listening = remember(audioPlayer, recitationRepository, recitationStorage) {
        ReaderListeningController(recitationRepository, recitationStorage, audioPlayer, listeningScope)
    }
    val listeningState by listening.state.collectAsState()
    DisposableEffect(listening) { onDispose { listening.close() } }
    LaunchedEffect(selectedReciterId, listening) { listening.stop() }
    val reciter = recitationRepository.reciters().firstOrNull { it.id == selectedReciterId }
    val reciterName = reciter?.let { if (progress.language.isRtl) it.nameArabic else it.nameEnglish }.orEmpty()

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
            val chapter = translationRepository.chapter(edition, route.surah)
            translatedContent = ReaderTranslationState(
                edition = chapter.edition,
                verses = chapter.verses.associateBy { it.verseId },
                isCached = chapter.isCached,
                isOlder = chapter.isOlder,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            translatedContent = ReaderTranslationState(
                failed = true,
            )
        }
    }

    ReaderScreen(
        chapter = quran.chapters()[route.surah - 1],
        verses = quran.verses(route.surah),
        initialAyah = route.ayah,
        progress = progress,
        readingPreferences = readingPreferences,
        listeningState = listeningState,
        reciterName = reciterName,
        onListen = { listening.listen(selectedReciterId, it) },
        onTogglePlayback = listening::togglePlayback,
        onStop = listening::stop,
        onRetry = listening::retry,
        translationSummary = {
            TranslationEditionHeader(
                language = progress.language,
                edition = translatedContent.edition,
                isLoading = translatedContent.isLoading,
                errorMessage = if (translatedContent.failed) appString(QuranStrings.translationLoadFailed) else null,
                onRetry = { reloadToken++ },
            )
            if (translatedContent.isCached) {
                Text(appString(if (translatedContent.isOlder) QuranStrings.translationOlderCached else QuranStrings.translationCached))
            }
        },
        translationForVerse = { verseId ->
            TranslationVerseText(
                language = progress.language,
                edition = translatedContent.edition,
                translation = translatedContent.verses[verseId],
                textSize = readingPreferences.translationTextSize,
            )
        },
        onRead = onRead,
        onBookmark = onBookmark,
        onPractice = onPractice,
        onStudy = onStudy,
    )
}
