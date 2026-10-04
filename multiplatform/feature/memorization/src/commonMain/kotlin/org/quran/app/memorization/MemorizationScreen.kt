package org.quran.app.memorization

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.quran.app.designsystem.*
import org.quran.app.domain.*
import org.quran.app.model.*

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
) {
    var count by remember(verse.id, progress.childMode) { mutableStateOf(if (progress.childMode) 3 else 5) }
    var until by remember(verse.id) { mutableStateOf(true) }
    val session = remember(verse.id, count, until) { RepeatSession(listOf(verse.id), count, until) }
    var state by remember(session) { mutableStateOf(session.state()) }
    var hidden by remember(verse.id) { mutableStateOf(false) }
    var downloadingAudio by remember(verse.id) { mutableStateOf(false) }
    var hasAudio by remember(verse.id) { mutableStateOf(false) }
    var playing by remember(session) { mutableStateOf(false) }
    var message by remember(verse.id) { mutableStateOf("") }
    val lifetime = remember(session, audioPlayer) { PlaybackLifetime() }
    val playbackError = rememberUpdatedState(appString(QuranStrings.recordingPlaybackError))
    val importError = rememberUpdatedState(appString(QuranStrings.recordingImportError))
    val decreaseRepetitions = appString(QuranStrings.decreaseRepetitions)
    val increaseRepetitions = appString(QuranStrings.increaseRepetitions)

    val controller = remember(session, audioPlayer) {
        RepeatPlaybackController(
            session, audioPlayer,
            onChanged = { next, isPlaying -> state = next; playing = isPlaying },
            onError = { message = playbackError.value },
        )
    }
    fun pause() = controller.pause()

    DisposableEffect(lifetime, controller) {
        onDispose {
            lifetime.active = false
            controller.dispose()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle(
            appString(QuranStrings.memorizeOneAyah),
            appString(QuranStrings.practiceAyah, verse.id.surah, verse.id.ayah),
        )
        PaperCard {
            if (hidden) {
                Text(appString(QuranStrings.reciteFromMemory))
            } else {
                ArabicVerse(verse.arabic, true)
            }
            Action(
                appString(if (hidden) QuranStrings.revealAyah else QuranStrings.hideAyah),
                { hidden = !hidden },
            )
        }
        PaperCard {
            Text(appString(QuranStrings.repetitions, state.completedRepetitions))
            Row {
                TextButton(onClick = { pause(); count-- }, enabled = count > 1, modifier = Modifier.semantics { contentDescription = decreaseRepetitions }) { Text("−") }
                Text("$count")
                TextButton(onClick = { pause(); count++ }, enabled = count < 20, modifier = Modifier.semantics { contentDescription = increaseRepetitions }) { Text("+") }
            }
            Row {
                Switch(until, { pause(); until = it })
                Text(appString(QuranStrings.repeatUntilMemorized))
            }
            Action(
                appString(QuranStrings.repeatedVerse),
                { controller.repeatManually() },
                !state.complete && !playing,
            )
            Action(
                appString(QuranStrings.iMemorizedThis),
                { controller.markMemorized(); onMemorized(verse.id) },
                verse.id !in progress.memorized,
            )
            if (state.complete) {
                Text(appString(QuranStrings.sessionComplete))
            }
            TextButton({ controller.reset(); hidden = false }) {
                Text(appString(QuranStrings.startAgain))
            }
        }
        ReciterAudioCard(
            verseId = verse.id,
            repository = recitationRepository,
            selectedReciterId = selectedReciterId,
            isArabic = progress.language.isRtl,
            onReciterSelected = onReciterSelected,
            onDownloadStateChanged = { downloadingAudio = it },
            onAudioCleared = { lifetime.importGeneration++; pause(); hasAudio = false; message = "" },
            onAudioReady = { uri ->
                pause()
                hasAudio = false
                audioPlayer.loadLocal(uri)
                hasAudio = true
                message = ""
            },
        )
        PaperCard {
            Text(appString(QuranStrings.ownRecitation), style = MaterialTheme.typography.titleLarge)
            Text(appString(QuranStrings.importRecordingHelp))
            Action(appString(QuranStrings.importRecording), {
                pause()
                val importGeneration = ++lifetime.importGeneration
                onImport { uri ->
                    if (lifetime.active && lifetime.importGeneration == importGeneration) {
                        hasAudio = false
                        runCatching { audioPlayer.loadLocal(uri) }
                            .onSuccess { hasAudio = true; message = "" }
                            .onFailure {
                                message = importError.value
                            }
                    }
                }
            }, !downloadingAudio)
            if (message.isNotEmpty()) Text(message)
        }
        if (hasAudio) {
            PaperCard {
            if (hasAudio) {
                Action(
                    appString(if (playing) QuranStrings.pause else QuranStrings.playAndRepeat),
                    { if (playing) pause() else controller.play() },
                    !state.complete,
                )
            }
            }
        }
    }
}
