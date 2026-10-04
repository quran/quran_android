package org.quran.app.memorization

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.quran.app.designsystem.*
import org.quran.app.domain.AudioPlayer
import org.quran.app.domain.RecitationRepository
import org.quran.app.domain.RecitationStorageRepository
import org.quran.app.domain.RepeatSession
import org.quran.app.domain.PracticeSessionStore
import org.quran.app.model.StudyProgress
import org.quran.app.model.Verse
import org.quran.app.model.VerseId
import org.quran.app.model.PracticeSessionSnapshot

@Composable
fun MemorizationScreen(
    verse: Verse,
    progress: StudyProgress,
    onMemorized: (VerseId) -> Unit,
    audioPlayer: AudioPlayer,
    onImport: ((String) -> Unit) -> Unit,
    recitationRepository: RecitationRepository,
    selectedReciterId: String,
    onReciterSelected: (String) -> Unit,
    chapterVerses: List<Verse> = listOf(verse),
    recitationStorage: RecitationStorageRepository? = null,
    practiceSessionStore: PracticeSessionStore? = null,
) {
    val maxSize = if (progress.childMode) 5 else 20
    val maxEnd = minOf(chapterVerses.last().id.ayah, verse.id.ayah + maxSize - 1)
    val restored = remember(verse.id, progress.childMode, chapterVerses, practiceSessionStore) {
        practiceSessionStore?.read()?.takeIf {
            it.surah == verse.id.surah && it.startAyah == verse.id.ayah &&
                it.endAyah in verse.id.ayah..maxEnd && it.currentAyah in it.startAyah..it.endAyah &&
                it.repetitionsPerVerse > 0 && it.completedRepetitions >= 0
        }
    }
    var endAyah by remember(verse.id, progress.childMode) { mutableStateOf(restored?.endAyah ?: verse.id.ayah) }
    val selectedVerses = remember(chapterVerses, verse.id, endAyah) {
        chapterVerses.filter { it.id.ayah in verse.id.ayah..endAyah }
    }
    val selectedIds = remember(selectedVerses) { selectedVerses.map { it.id } }
    var count by remember(verse.id, progress.childMode) { mutableStateOf(restored?.repetitionsPerVerse ?: if (progress.childMode) 3 else 5) }
    var until by remember(verse.id) { mutableStateOf(restored?.repeatUntilMemorized ?: true) }
    var autoplayRequested by remember(verse.id, progress.childMode) { mutableStateOf(restored?.autoplayRequested ?: false) }
    var restoreConsumed by remember(verse.id, progress.childMode, chapterVerses, practiceSessionStore) {
        mutableStateOf(false)
    }
    val session = remember(selectedIds, count, until) {
        RepeatSession(selectedIds, count, until)
    }
    var state by remember(session) { mutableStateOf(session.state()) }
    LaunchedEffect(session) {
        if (!restoreConsumed) {
            restored?.takeIf {
                it.endAyah == endAyah && it.repetitionsPerVerse == count && it.repeatUntilMemorized == until &&
                    VerseId(it.surah, it.currentAyah) in selectedIds
            }?.let {
                session.restore(VerseId(it.surah, it.currentAyah), it.completedRepetitions, it.complete)
                state = session.state()
            }
            restoreConsumed = true
        }
    }
    val currentVerse = selectedVerses.first { it.id == state.currentVerse }
    var hidden by remember(state.currentVerse) { mutableStateOf(false) }
    var audioUris by remember(selectedIds, selectedReciterId) { mutableStateOf(emptyMap<VerseId, String>()) }
    var importedUri by remember(selectedIds, selectedReciterId) { mutableStateOf<String?>(null) }
    var playing by remember(session) { mutableStateOf(false) }
    var message by remember(selectedIds) { mutableStateOf("") }
    val lifetime = remember(session, audioPlayer) { PlaybackLifetime() }
    val playbackError = rememberUpdatedState(appString(QuranStrings.recordingPlaybackError))
    val importError = rememberUpdatedState(appString(QuranStrings.recordingImportError))
    val hasAudio = audioUris.keys.containsAll(selectedIds) || importedUri != null
    val controller = remember(session, audioPlayer, audioUris, importedUri) {
        RepeatPlaybackController(
            session, audioPlayer,
            onChanged = { next, isPlaying -> state = next; playing = isPlaying },
            onError = { message = playbackError.value },
            audioForVerse = if (importedUri == null) ({ id -> audioUris[id] }) else null,
        )
    }
    val latestPlaybackController = rememberUpdatedState(controller)
    val clearQueueAudioCallback: () -> Unit = {
        lifetime.importGeneration++
        latestPlaybackController.value.pause()
        audioPlayer.clearLocal()
        audioUris = emptyMap()
        importedUri = null
        message = ""
    }
    val clearQueueAudio = rememberUpdatedState(clearQueueAudioCallback)
    val onQueueAudioReadyCallback: (Map<VerseId, String>) -> Unit = { uris ->
        latestPlaybackController.value.pause()
        audioUris = uris
        importedUri = null
        message = ""
    }
    val onQueueAudioReady = rememberUpdatedState(onQueueAudioReadyCallback)
    val queueScope = rememberCoroutineScope()
    val queueController = remember(recitationRepository, recitationStorage) {
        RecitationQueueController(
            recitationRepository,
            queueScope,
            onAudioReady = { onQueueAudioReady.value(it) },
            onAudioCleared = { clearQueueAudio.value() },
            storage = recitationStorage,
        )
    }
    val queueState by queueController.state.collectAsState()
    LaunchedEffect(queueController, selectedReciterId, selectedIds) {
        queueController.select(selectedReciterId, selectedIds)
    }
    DisposableEffect(queueController) { onDispose { queueController.close() } }

    fun clearAudio() {
        clearQueueAudio.value()
        queueController.releasePublishedAudioAfterSourceReplacement()
    }
    DisposableEffect(lifetime) { onDispose { lifetime.active = false } }
    DisposableEffect(controller) { onDispose { controller.dispose() } }
    SideEffect {
        practiceSessionStore?.save(
            PracticeSessionSnapshot(
                surah = verse.id.surah,
                startAyah = verse.id.ayah,
                endAyah = endAyah,
                currentAyah = state.currentVerse.ayah,
                repetitionsPerVerse = count,
                completedRepetitions = state.completedRepetitions,
                repeatUntilMemorized = until,
                complete = state.complete,
                autoplayRequested = autoplayRequested,
            ),
        )
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(QuranSpacing.Large),
    ) {
        ScreenTitle(
            appString(if (selectedIds.size == 1) QuranStrings.memorizeOneAyah else QuranStrings.practiceRangeTitle),
            appString(QuranStrings.practiceAyah, currentVerse.id.surah, currentVerse.id.ayah),
        )
        PracticeRangeCard(verse.id, endAyah, maxEnd, maxSize) { next ->
            clearAudio()
            endAyah = next.coerceIn(verse.id.ayah..maxEnd)
        }
        if (selectedIds.size > 1) QuranText(appString(QuranStrings.practiceCurrentPosition, selectedIds.indexOf(state.currentVerse) + 1, selectedIds.size))
        PracticeVerseCard(currentVerse, hidden) { hidden = it }
        PracticeRepeatCard(
            state, count, until, playing,
            canMarkMemorized = !state.complete || currentVerse.id !in progress.memorized,
            onCountChanged = { autoplayRequested = false; controller.pause(); count = it },
            onUntilChanged = { autoplayRequested = false; controller.pause(); until = it },
            onManualRepeat = { controller.repeatManually() },
            onMemorized = {
                val memorized = state.currentVerse
                controller.markMemorized()
                onMemorized(memorized)
            },
            onReset = { autoplayRequested = false; controller.reset(); hidden = false },
        )
        RecitationQueueCard(
            selectedIds, recitationRepository, selectedReciterId, progress.language.isRtl,
            state = queueState,
            onReciterSelected = { id -> clearAudio(); onReciterSelected(id) },
            onPrepareAudio = queueController::prepareAudio,
        )
        ImportedRecordingCard(selectedIds.size == 1, !queueState.isDownloading, message) {
            controller.pause()
            val importGeneration = ++lifetime.importGeneration
            onImport { uri ->
                if (lifetime.active && lifetime.importGeneration == importGeneration) {
                    queueScope.launch {
                        try {
                            if (!lifetime.active || lifetime.importGeneration != importGeneration) return@launch
                            latestPlaybackController.value.pause()
                            audioPlayer.clearLocal()
                            audioUris = emptyMap()
                            importedUri = null
                            queueController.releasePublishedAudioAfterSourceReplacementAndWait()
                            if (!lifetime.active || lifetime.importGeneration != importGeneration) return@launch
                            audioPlayer.loadLocal(uri)
                            importedUri = uri
                            message = ""
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            if (lifetime.active && lifetime.importGeneration == importGeneration) message = importError.value
                        }
                    }
                }
            }
        }
        if (hasAudio) PaperCard {
            Action(appString(if (playing) QuranStrings.pause else QuranStrings.playAndRepeat),
                { if (playing) { autoplayRequested = false; controller.pause() } else { autoplayRequested = true; controller.play() } }, !state.complete)
        }
    }
}
