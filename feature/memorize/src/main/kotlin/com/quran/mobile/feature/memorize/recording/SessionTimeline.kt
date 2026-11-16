package com.quran.mobile.feature.memorize.recording

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Where in a session's time each word was, and how it went (see [SessionTimeline]). */
internal data class TimelineWord(val atMs: Long, val kind: Kind, val startsAyah: Boolean) {
  enum class Kind { RECITED, HINTED, MISSED, MISTAKE }
}

/** The words of [recording] that have a time, in order. */
internal fun timelineOf(recording: Recording): List<TimelineWord> = recording.words.mapNotNull { word ->
  val at = word.startMs ?: word.endMs ?: return@mapNotNull null
  val kind = when {
    word.status == Recording.WordStatus.HINTED -> TimelineWord.Kind.HINTED
    (word.mistakes ?: 0) > 0 -> TimelineWord.Kind.MISTAKE
    word.status == Recording.WordStatus.MISSED -> TimelineWord.Kind.MISSED
    else -> TimelineWord.Kind.RECITED
  }
  TimelineWord(at, kind, startsAyah = word.id.word == 1)
}

/**
 * The session across its time: a short mark for every word recited, a tall one in the legend's
 * colour where a word was given, skipped or got a mistake, and a tick where each ayah starts. The
 * part already played is darker. Tapping plays from there. It runs left to right, as the player does.
 */
@Composable
internal fun SessionTimeline(
  words: List<TimelineWord>,
  durationMs: Long,
  positionMs: Long,
  description: String,
  onSeek: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (durationMs <= 0 || words.isEmpty()) return
  val night = MaterialTheme.colorScheme.surface.luminance() < 0.5f
  val recited = MaterialTheme.colorScheme.onSurfaceVariant
  val tick = MaterialTheme.colorScheme.outline
  val colors = remember(night) {
    mapOf(
      TimelineWord.Kind.HINTED to if (night) Color(0xFF4DB6AC) else Color(0xFF00897B),
      TimelineWord.Kind.MISSED to if (night) Color(0xFFCE93D8) else Color(0xFFFB8C00),
      TimelineWord.Kind.MISTAKE to if (night) Color(0xFFEF5350) else Color(0xFFE53935),
    )
  }
  val seek by rememberUpdatedState(onSeek)
  Canvas(
    modifier
      .fillMaxWidth()
      .height(36.dp)
      .semantics { contentDescription = description }
      .pointerInput(durationMs) {
        detectTapGestures { offset -> seek((offset.x / size.width * durationMs).toLong().coerceIn(0, durationMs)) }
      }
  ) {
    val mid = size.height / 2
    fun x(ms: Long) = (ms.toFloat() / durationMs).coerceIn(0f, 1f) * size.width
    val played = x(positionMs)
    words.forEach { word ->
      val wx = x(word.atMs)
      val past = wx <= played
      if (word.startsAyah) {
        drawLine(tick.copy(alpha = 0.5f), Offset(wx, 2.dp.toPx()), Offset(wx, size.height - 2.dp.toPx()), 1.dp.toPx())
      }
      val color = colors[word.kind]
      if (color == null) {
        drawLine(
          recited.copy(alpha = if (past) 0.75f else 0.3f),
          Offset(wx, mid - 4.dp.toPx()), Offset(wx, mid + 4.dp.toPx()),
          2.dp.toPx(), StrokeCap.Round,
        )
      } else {
        drawLine(
          color.copy(alpha = if (past) 1f else 0.6f),
          Offset(wx, mid - 12.dp.toPx()), Offset(wx, mid + 12.dp.toPx()),
          3.dp.toPx(), StrokeCap.Round,
        )
      }
    }
  }
}
