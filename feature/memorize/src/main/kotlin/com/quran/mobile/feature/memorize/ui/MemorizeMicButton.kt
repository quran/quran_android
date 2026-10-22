package com.quran.mobile.feature.memorize.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.mobile.feature.memorize.R

/** What the floating mic button shows. */
internal data class MicButtonState(
  val listening: Boolean,
  val preparing: Boolean,
  val recording: Boolean,
  val canReplay: Boolean,
  val replaying: Boolean,
)

/**
 * The floating mic of a memorization session: blue and pulsing while it listens (with a red dot
 * when the session is recorded), grey while paused, with buttons to end the session and to listen
 * to the recording.
 */
@Composable
internal fun MemorizeMicButton(
  state: MicButtonState,
  onToggle: () -> Unit,
  onEnd: () -> Unit,
  onReplay: () -> Unit,
) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    if (!state.listening) {
      SmallButton(QuranIcons.Close, stringResource(R.string.memorize_mic_end), onEnd)
      if (state.canReplay) {
        SmallButton(
          if (state.replaying) QuranIcons.Pause else QuranIcons.PlayArrow,
          stringResource(if (state.replaying) R.string.memorize_mic_replay_pause else R.string.memorize_mic_replay),
          onReplay,
        )
      }
    }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
      if (state.listening) Pulse()
      val description = stringResource(if (state.listening) R.string.memorize_mic_pause else R.string.memorize_mic_resume)
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(60.dp)
          .clip(CircleShape)
          .background(if (state.listening) LISTENING else MaterialTheme.colorScheme.surfaceVariant)
          .semantics { contentDescription = description }
          .clickable(role = Role.Button, onClick = onToggle),
      ) {
        Icon(
          imageVector = if (state.listening) QuranIcons.Mic else MemorizeIcons.MicOff,
          contentDescription = null,
          tint = if (state.listening) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(30.dp),
        )
        if (state.preparing) {
          CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(56.dp))
        }
      }
      if (state.recording) {
        Box(
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 12.dp, end = 12.dp)
            .size(14.dp)
            .clip(CircleShape)
            .background(RECORDING)
            .border(2.dp, Color.White, CircleShape)
        )
      }
    }
  }
}

@Composable
private fun Pulse() {
  val transition = rememberInfiniteTransition(label = "listening")
  val progress = transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(1_400, easing = LinearEasing), RepeatMode.Restart),
    label = "pulse",
  )
  Box(
    modifier = Modifier
      .size(60.dp)
      .scale(1f + 0.35f * progress.value)
      .graphicsLayer { alpha = 0.45f * (1f - progress.value) }
      .clip(CircleShape)
      .background(LISTENING)
  )
}

@Composable
private fun SmallButton(icon: ImageVector, description: String, onClick: () -> Unit, size: Dp = 44.dp) {
  Surface(
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surfaceVariant,
    shadowElevation = 3.dp,
    modifier = Modifier.size(size),
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .semantics { contentDescription = description }
        .clickable(role = Role.Button, onClick = onClick),
    ) {
      Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

private val LISTENING = Color(0xFF1E88E5)
private val RECORDING = Color(0xFFE53935)
