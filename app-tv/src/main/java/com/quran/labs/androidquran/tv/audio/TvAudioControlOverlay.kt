package com.quran.labs.androidquran.tv.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.compose.material3.MaterialTheme

/**
 * Audio playback state
 */
data class AudioState(
  val isPlaying: Boolean = false,
  val currentPosition: Int = 0,
  val duration: Int = 0,
  val surahName: String = "",
  val surahNumber: Int = 0,
  val ayahNumber: Int = 0,
  val repeatCount: Int = 0,
  val qariName: String = "Sheikh Mishary Rashid Alafasy"
)

/**
 * TV Audio control overlay - simplified version
 */
@Composable
fun TvAudioControlOverlay(
  audioState: AudioState,
  onPlayPause: () -> Unit = {},
  onNext: () -> Unit = {},
  onPrevious: () -> Unit = {},
  onRepeat: () -> Unit = {},
  onQariChange: () -> Unit = {},
  onDismiss: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.Black.copy(alpha = 0.8f))
      .clickable { onDismiss() }
      .padding(48.dp),
    contentAlignment = Alignment.Center
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .border(
          width = 2.dp,
          color = MaterialTheme.colorScheme.primary,
          shape = RoundedCornerShape(24.dp)
        )
        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
        .padding(32.dp)
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Audio Playback",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
          )
          Box(
            modifier = Modifier
              .size(48.dp, 48.dp)
              .background(MaterialTheme.colorScheme.error, RoundedCornerShape(8.dp))
              .clickable { onDismiss() }
              .padding(8.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "✕",
              style = MaterialTheme.typography.titleLarge,
              fontSize = 24.sp,
              color = Color.White
            )
          }
        }

        // Current playing info
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = audioState.qariName,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = "Surah ${audioState.surahName} (${audioState.surahNumber}): Ayah ${audioState.ayahNumber}",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary
          )
        }

        // Progress bar
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(
              MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(4.dp)
            )
        ) {
          val progress = if (audioState.duration > 0) {
            audioState.currentPosition.toFloat() / audioState.duration
          } else 0f
          Box(
            modifier = Modifier
              .fillMaxWidth(progress)
              .height(8.dp)
              .background(
                MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(4.dp)
              )
          )
        }

        // Progress text
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = formatTime(audioState.currentPosition),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = formatTime(audioState.duration),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        // Playback controls
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Repeat button
          AudioButtonIcon("↻", "Repeat", onClick = onRepeat)

          // Previous button
          AudioButtonIcon("⏮", "Previous", onClick = onPrevious)

          // Play/Pause button (larger)
          Box(
            modifier = Modifier
              .size(96.dp, 96.dp)
              .background(
                MaterialTheme.colorScheme.primary,
                RoundedCornerShape(48.dp)
              )
              .clickable { onPlayPause() },
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = if (audioState.isPlaying) "⏸" else "▶",
              style = MaterialTheme.typography.displayLarge,
              fontSize = 48.sp,
              color = Color.White
            )
          }

          // Next button
          AudioButtonIcon("⏭", "Next", onClick = onNext)

          // Qari selector button
          AudioButtonIcon("☰", "Qari", onClick = onQariChange)
        }

        // Additional controls
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly
        ) {
          Text(
            text = "◄◄ Back 10s",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = "Shuffle: OFF",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = "Speed: 1.0x",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}

@Composable
private fun AudioButtonIcon(
  icon: String,
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  var isFocused by remember { mutableStateOf(false) }

  Box(
    modifier = modifier
      .size(72.dp, 72.dp)
      .background(
        if (isFocused) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        RoundedCornerShape(8.dp)
      )
      .clickable { onClick() }
      .onFocusChanged { focusState ->
        isFocused = focusState.hasFocus
      }
      .padding(8.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = icon,
      style = MaterialTheme.typography.displaySmall,
      fontSize = 36.sp,
      color = if (isFocused) Color.White
      else MaterialTheme.colorScheme.onSurface
    )
  }
}

/**
 * Format time in seconds to MM:SS
 */
private fun formatTime(seconds: Int): String {
  val mins = seconds / 60
  val secs = seconds % 60
  return "%02d:%02d".format(mins, secs)
}

/**
 * Sample audio screen - can be opened from settings or home
 */
@Composable
fun TvAudioScreen(
  modifier: Modifier = Modifier
) {
  // Sample audio state
  val audioState = remember {
    AudioState(
      isPlaying = true,
      currentPosition = 145, // 2:25
      duration = 385, // 6:25
      surahName = "Ar-Rahman",
      surahNumber = 55,
      ayahNumber = 1,
      repeatCount = 0,
      qariName = "Sheikh Mishary Rashid Alafasy"
    )
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
  ) {
    // Background with page info
    Column(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Text(
        text = "Audio Playback",
        style = MaterialTheme.typography.displayLarge,
        color = MaterialTheme.colorScheme.primary
      )
      Text(
        text = "Press OK to open controls",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    // Audio overlay (shown by default)
    TvAudioControlOverlay(
      audioState = audioState,
      onPlayPause = { },
      onNext = { },
      onPrevious = { },
      onRepeat = { },
      onQariChange = { },
      onDismiss = { }
    )
  }
}
