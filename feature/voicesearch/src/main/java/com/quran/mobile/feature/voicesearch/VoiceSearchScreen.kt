package com.quran.mobile.feature.voicesearch

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.mobile.feature.voicesearch.asr.ModelState
import com.quran.mobile.voicesearch.VerseMatch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSearchScreen(
  state: VoiceSearchState,
  onEvent: (VoiceSearchEvent) -> Unit,
  onBackPressed: () -> Unit
) {
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(state.errorMessage) {
    state.errorMessage?.let {
      snackbarHostState.showSnackbar(
        message = it,
        duration = SnackbarDuration.Short
      )
      onEvent(VoiceSearchEvent.DismissError)
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.voice_search_title)) },
        navigationIcon = {
          IconButton(onClick = onBackPressed) {
            Icon(QuranIcons.ArrowBack, contentDescription = stringResource(R.string.voice_search_back))
          }
        }
      )
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
    modifier = Modifier.windowInsetsPadding(
      WindowInsets.systemBars
        .union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Horizontal)
    )
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(horizontal = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      when (state.screenState) {
        ScreenState.Idle -> IdleContent(state.modelState, onEvent)
        ScreenState.ModelDownloading -> ModelDownloadingContent(state.modelState, onEvent)
        ScreenState.Ready -> ReadyContent(onEvent)
        ScreenState.Recording -> RecordingContent(state.amplitude, onEvent)
        ScreenState.Transcribing -> TranscribingContent()
        ScreenState.Results -> ResultsContent(
          transcribedText = state.transcribedText,
          verseMatches = state.verseMatches,
          onEvent = onEvent
        )
      }
    }
  }
}

@Composable
private fun IdleContent(modelState: ModelState, onEvent: (VoiceSearchEvent) -> Unit) {
  Column(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Icon(
      imageVector = QuranIcons.Mic,
      contentDescription = null,
      modifier = Modifier.size(64.dp),
      tint = MaterialTheme.colorScheme.primary
    )

    Spacer(modifier = Modifier.height(24.dp))

    Text(
      text = stringResource(R.string.voice_search_description),
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(horizontal = 32.dp)
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = stringResource(R.string.voice_search_model_required),
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 32.dp)
    )

    Spacer(modifier = Modifier.height(32.dp))

    Button(onClick = { onEvent(VoiceSearchEvent.DownloadModel) }) {
      Text(stringResource(R.string.voice_search_download_model))
    }

    if (modelState is ModelState.Error) {
      Spacer(modifier = Modifier.height(16.dp))
      Text(
        text = modelState.message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall
      )
    }
  }
}

@Composable
private fun ModelDownloadingContent(modelState: ModelState, onEvent: (VoiceSearchEvent) -> Unit) {
  Column(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = stringResource(R.string.voice_search_downloading_model),
      style = MaterialTheme.typography.titleMedium
    )

    Spacer(modifier = Modifier.height(24.dp))

    val progress = (modelState as? ModelState.Downloading)?.progress ?: 0f
    LinearProgressIndicator(
      progress = { progress },
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 48.dp)
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = "${(progress * 100).toInt()}%",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = stringResource(R.string.voice_search_model_size),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(24.dp))

    OutlinedButton(onClick = { onEvent(VoiceSearchEvent.CancelDownload) }) {
      Text(stringResource(R.string.voice_search_cancel))
    }
  }
}

@Composable
private fun ReadyContent(onEvent: (VoiceSearchEvent) -> Unit) {
  Column(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = stringResource(R.string.voice_search_tap_to_recite),
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.padding(bottom = 32.dp)
    )

    Box(
      modifier = Modifier
        .size(96.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.primary)
        .clickable { onEvent(VoiceSearchEvent.StartRecording) },
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = QuranIcons.Mic,
        contentDescription = stringResource(R.string.voice_search_start),
        modifier = Modifier.size(48.dp),
        tint = MaterialTheme.colorScheme.onPrimary
      )
    }
  }
}

@Composable
private fun RecordingContent(amplitude: Float, onEvent: (VoiceSearchEvent) -> Unit) {
  val infiniteTransition = rememberInfiniteTransition(label = "pulse")
  val pulseScale = infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = 1.15f,
    animationSpec = infiniteRepeatable(
      animation = tween(600, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulseScale"
  )

  Column(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = stringResource(R.string.voice_search_listening),
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.padding(bottom = 32.dp)
    )

    val dynamicScale = pulseScale.value + (amplitude * 0.3f)
    Box(
      modifier = Modifier
        .size(96.dp)
        .scale(dynamicScale)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.error)
        .clickable { onEvent(VoiceSearchEvent.StopRecording) },
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = QuranIcons.Stop,
        contentDescription = stringResource(R.string.voice_search_stop),
        modifier = Modifier.size(48.dp),
        tint = MaterialTheme.colorScheme.onError
      )
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(
      text = stringResource(R.string.voice_search_tap_to_stop),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
private fun TranscribingContent() {
  Column(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    CircularProgressIndicator(modifier = Modifier.size(48.dp))

    Spacer(modifier = Modifier.height(24.dp))

    Text(
      text = stringResource(R.string.voice_search_recognizing),
      style = MaterialTheme.typography.titleMedium
    )
  }
}

@Composable
private fun ResultsContent(
  transcribedText: String,
  verseMatches: List<VerseMatch>,
  onEvent: (VoiceSearchEvent) -> Unit
) {
  Column(modifier = Modifier.fillMaxSize()) {
    // Transcribed text header
    Text(
      text = transcribedText,
      style = MaterialTheme.typography.titleMedium,
      textAlign = TextAlign.End,
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp)
    )

    if (verseMatches.isEmpty()) {
      // No matches found — offer text search fallback
      Spacer(modifier = Modifier.height(16.dp))
      Text(
        text = stringResource(R.string.voice_search_no_matches),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
      )
      Spacer(modifier = Modifier.height(16.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
      ) {
        OutlinedButton(onClick = { onEvent(VoiceSearchEvent.SearchText) }) {
          Text(stringResource(R.string.voice_search_search_text))
        }
        OutlinedButton(onClick = { onEvent(VoiceSearchEvent.Reset) }) {
          Text(stringResource(R.string.voice_search_try_again))
        }
      }
    } else {
      // Action buttons
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        TextButton(onClick = { onEvent(VoiceSearchEvent.SearchText) }) {
          Text(stringResource(R.string.voice_search_search_text))
        }
        TextButton(onClick = { onEvent(VoiceSearchEvent.Reset) }) {
          Text(stringResource(R.string.voice_search_try_again))
        }
      }

      // Verse match results
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(verseMatches) { match ->
          VerseMatchCard(
            match = match,
            onClick = { onEvent(VoiceSearchEvent.SelectVerse(match.sura, match.ayah)) }
          )
        }
      }
    }
  }
}

@Composable
private fun VerseMatchCard(match: VerseMatch, onClick: () -> Unit) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "${match.sura}:${match.ayah}",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary
        )
        Text(
          text = "${(match.score * 100).toInt()}%",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = match.verseText,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.End,
        modifier = Modifier.fillMaxWidth()
      )
    }
  }
}
