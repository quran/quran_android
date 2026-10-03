package org.quran.app.memorization

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.quran.app.designsystem.*
import org.quran.app.domain.*
import org.quran.app.model.*

/** Invalidates asynchronous imports/completions when the learner leaves or changes a session. */
private class PlaybackLifetime(var active: Boolean = true)

@Composable
fun MemorizationScreen(
    verse: Verse,
    progress: StudyProgress,
    onMemorized: (VerseId) -> Unit,
    audioPlayer: AudioPlayer,
    onImport: ((String) -> Unit) -> Unit,
) {
    var count by remember(verse.id, progress.childMode) { mutableStateOf(if (progress.childMode) 3 else 5) }
    var until by remember(verse.id) { mutableStateOf(true) }
    val session = remember(verse.id, count, until) { RepeatSession(listOf(verse.id), count, until) }
    var state by remember(session) { mutableStateOf(session.state()) }
    var hidden by remember(verse.id) { mutableStateOf(false) }
    var hasAudio by remember(verse.id) { mutableStateOf(false) }
    var playing by remember(session) { mutableStateOf(false) }
    var message by remember(verse.id) { mutableStateOf("") }
    val lifetime = remember(session, audioPlayer) { PlaybackLifetime() }
    val language = progress.language

    val controller = remember(session, audioPlayer) {
        RepeatPlaybackController(
            session, audioPlayer,
            onChanged = { next, isPlaying -> state = next; playing = isPlaying },
            onError = { message = label(language, "Recording could not be played. Choose a supported audio file.", "تعذر تشغيل التسجيل. اختر ملفاً صوتياً مدعوماً.") },
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
            label(language, "One verse at a time", "آية بعد آية"),
            label(language, "Self recitation · ${verse.id.surah}:${verse.id.ayah}", "تسميع ذاتي · ${verse.id.surah}:${verse.id.ayah}"),
        )
        PaperCard {
            if (hidden) {
                Text(label(language, "Recite from memory, then reveal to check.", "اقرأ من الذاكرة ثم أظهر الآية للمراجعة."))
            } else {
                ArabicVerse(verse.arabic, true)
            }
            Action(
                label(language, if (hidden) "Reveal verse" else "Hide verse", if (hidden) "إظهار الآية" else "إخفاء الآية"),
                { hidden = !hidden },
            )
        }
        PaperCard {
            Text(label(language, "Repetitions: ${state.completedRepetitions}", "التكرارات: ${state.completedRepetitions}"))
            Row {
                TextButton(onClick = { pause(); count-- }, enabled = count > 1) { Text("−") }
                Text("$count")
                TextButton(onClick = { pause(); count++ }, enabled = count < 20) { Text("+") }
            }
            Row {
                Switch(until, { pause(); until = it })
                Text(label(language, "Repeat until memorized", "التكرار حتى الحفظ"))
            }
            Action(
                label(language, "I repeated this verse", "كررت هذه الآية"),
                { controller.repeatManually() },
                !state.complete && !playing,
            )
            Action(
                label(language, "I have memorized it", "حفظت الآية"),
                { controller.markMemorized(); onMemorized(verse.id) },
                verse.id !in progress.memorized,
            )
            if (state.complete) {
                Text(label(language, "Session complete. Memorization is self assessed.", "اكتملت الجلسة. تقييم الحفظ ذاتي."))
            }
            TextButton({ controller.reset(); hidden = false }) {
                Text(label(language, "Start again", "ابدأ مجدداً"))
            }
        }
        PaperCard {
            Text(label(language, "Your own recitation", "تلاوتك الخاصة"), style = MaterialTheme.typography.titleLarge)
            Text(label(language, "Import a recording of this verse that you own or have permission to use. Audio repeats only after real playback finishes.", "استورد تسجيلاً لهذه الآية تملكه أو لديك إذن باستخدامه. التكرار بعد انتهاء التشغيل الفعلي."))
            Action(label(language, "Import verse recording", "استيراد تسجيل الآية"), {
                pause()
                onImport { uri ->
                    if (lifetime.active) {
                        hasAudio = false
                        runCatching { audioPlayer.loadLocal(uri) }
                            .onSuccess { hasAudio = true; message = "" }
                            .onFailure {
                                message = label(language, "Recording could not be imported. Choose a local audio file.", "تعذر استيراد التسجيل. اختر ملفاً صوتياً محلياً.")
                            }
                    }
                }
            })
            if (hasAudio) {
                Action(
                    label(language, if (playing) "Pause" else "Play and repeat", if (playing) "إيقاف مؤقت" else "تشغيل وتكرار"),
                    { if (playing) pause() else controller.play() },
                    !state.complete,
                )
            }
            if (message.isNotEmpty()) Text(message)
        }
    }
}
