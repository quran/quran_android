package org.quran.app.memorization

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.quran.app.designsystem.*
import org.quran.app.domain.AudioPlayer
import org.quran.app.domain.RecitationRepository
import org.quran.app.domain.RepeatSession
import org.quran.app.model.StudyProgress
import org.quran.app.model.Verse
import org.quran.app.model.VerseId

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
) {
    val maxSize = if (progress.childMode) 5 else 20
    val maxEnd = minOf(chapterVerses.last().id.ayah, verse.id.ayah + maxSize - 1)
    var endAyah by remember(verse.id, progress.childMode) { mutableStateOf(verse.id.ayah) }
    val selectedVerses = remember(chapterVerses, verse.id, endAyah) {
        chapterVerses.filter { it.id.ayah in verse.id.ayah..endAyah }
    }
    val selectedIds = remember(selectedVerses) { selectedVerses.map { it.id } }
    var count by remember(verse.id, progress.childMode) { mutableStateOf(if (progress.childMode) 3 else 5) }
    var until by remember(verse.id) { mutableStateOf(true) }
    val session = remember(selectedIds, count, until) { RepeatSession(selectedIds, count, until) }
    var state by remember(session) { mutableStateOf(session.state()) }
    val currentVerse = selectedVerses.first { it.id == state.currentVerse }
    var hidden by remember(state.currentVerse) { mutableStateOf(false) }
    var downloadingAudio by remember(selectedIds) { mutableStateOf(false) }
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
    fun clearAudio() {
        lifetime.importGeneration++
        controller.pause()
        audioUris = emptyMap()
        importedUri = null
        message = ""
    }
    DisposableEffect(lifetime) { onDispose { lifetime.active = false } }
    DisposableEffect(controller) { onDispose { controller.dispose() } }
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
            onCountChanged = { controller.pause(); count = it },
            onUntilChanged = { controller.pause(); until = it },
            onManualRepeat = { controller.repeatManually() },
            onMemorized = {
                val memorized = state.currentVerse
                controller.markMemorized()
                onMemorized(memorized)
            },
            onReset = { controller.reset(); hidden = false },
        )
        RecitationQueueCard(
            selectedIds, recitationRepository, selectedReciterId, progress.language.isRtl,
            onReciterSelected = onReciterSelected,
            onDownloadStateChanged = { downloadingAudio = it },
            onAudioCleared = ::clearAudio,
            onAudioReady = { uris -> controller.pause(); audioUris = uris; importedUri = null; message = "" },
        )
        ImportedRecordingCard(selectedIds.size == 1, !downloadingAudio, message) {
            controller.pause()
            val importGeneration = ++lifetime.importGeneration
            onImport { uri ->
                if (lifetime.active && lifetime.importGeneration == importGeneration) {
                    audioUris = emptyMap()
                    importedUri = null
                    runCatching { audioPlayer.loadLocal(uri) }
                        .onSuccess { importedUri = uri; message = "" }
                        .onFailure { message = importError.value }
                }
            }
        }
        if (hasAudio) PaperCard {
            Action(appString(if (playing) QuranStrings.pause else QuranStrings.playAndRepeat),
                { if (playing) controller.pause() else controller.play() }, !state.complete)
        }
    }
}
