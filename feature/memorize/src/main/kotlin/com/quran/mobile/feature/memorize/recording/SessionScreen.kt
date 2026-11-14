package com.quran.mobile.feature.memorize.recording

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.quran.data.model.highlight.HighlightInfo
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.mobile.feature.memorize.R
import com.quran.mobile.feature.memorize.presenter.Mark
import com.quran.mobile.feature.memorize.ui.MemorizeIcons
import com.quran.recitation.pages.PageRenderer
import com.quran.recitation.presenter.RecitationHighlightsPresenter.HighlightAction
import androidx.appcompat.R as AppCompatR

/** How a session went, for the legend under its pages. */
internal data class SessionCounts(val recited: Int, val hinted: Int, val missed: Int, val mistakes: Int)

/**
 * A recorded session played back on its own pages, without leaving the recordings: the page turns
 * with the word being heard, the hints, skipped words and mistakes are marked, and tapping a word
 * plays from there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionScreen(
  title: String,
  subtitle: String,
  /** The whole session as one recording, once read. */
  recording: Recording?,
  counts: SessionCounts,
  /** The session's pages, in order, and the page each word is on. */
  pages: List<Int>,
  pageOf: (Recording.Word) -> Int,
  /** Where this session's playback is, if it is the one loaded. */
  playback: RecordingPlayer.Playback?,
  renderer: PageRenderer?,
  onBack: () -> Unit,
  onExport: () -> Unit,
  onDelete: () -> Unit,
  onPlay: () -> Unit,
  onPause: () -> Unit,
  onSeek: (Long) -> Unit,
  onSkip: (Long) -> Unit,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              QuranIcons.ArrowBack,
              contentDescription = stringResource(AppCompatR.string.abc_action_bar_up_description),
            )
          }
        },
        actions = {
          IconButton(onClick = onExport) {
            Icon(MemorizeIcons.Export, contentDescription = stringResource(R.string.memorize_sessions_export))
          }
          IconButton(onClick = onDelete) {
            Icon(MemorizeIcons.Delete, contentDescription = stringResource(R.string.memorize_sessions_delete))
          }
        },
      )
    },
  ) { padding ->
    Column(modifier = Modifier.padding(padding).fillMaxSize()) {
      // a page view may draw beyond its bounds (its background)
      Box(modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
        if (recording != null && renderer != null && pages.isNotEmpty()) {
          SessionPages(recording, pages, pageOf, playback?.positionMs, renderer, onSeek)
        }
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      Legend(counts)
      // media playback reads left to right in every language (time, the timeline and the controls), as
      // Material's bidirectionality guidance has it
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Player(recording, playback, onPlay, onPause, onSeek, onSkip)
      }
    }
  }
}

/** The session's pages in mushaf order (the first on the right), following the word being heard. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionPages(
  recording: Recording,
  pages: List<Int>,
  pageOf: (Recording.Word) -> Int,
  positionMs: Long?,
  renderer: PageRenderer,
  onSeek: (Long) -> Unit,
) {
  val playingIndex = positionMs
    ?.let { SessionMarks.wordAt(recording, it) }
    ?.let { pages.indexOf(pageOf(recording.words[it])) }
    ?.takeIf { it >= 0 }
  val pagerState = rememberPagerState(initialPage = playingIndex ?: 0) { pages.size }
  var follow by remember { mutableStateOf(true) }
  val dragged by pagerState.interactionSource.collectIsDraggedAsState()
  LaunchedEffect(dragged) { if (dragged) follow = false }
  LaunchedEffect(playingIndex, follow) {
    if (follow && playingIndex != null && pagerState.currentPage != playingIndex) pagerState.animateScrollToPage(playingIndex)
  }
  val seek by rememberUpdatedState(onSeek)
  val direction = LocalLayoutDirection.current
  Box(modifier = Modifier.fillMaxSize()) {
    // pages turn as in a mushaf, whatever the language
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
      HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 1,
        key = { pages[it] },
        // the page view measures itself from the room it is given: give it all of it
        modifier = Modifier.fillMaxSize(),
      ) { index ->
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
          val page = pages[index]
          SessionPage(page, renderer, SessionMarks.marks(recording, positionMs) { pageOf(it) == page }) { ayah, word ->
            SessionMarks.timeOf(recording, ayah.sura, ayah.ayah, word)?.let {
              follow = true
              seek(it)
            }
          }
        }
      }
    }
    AnimatedVisibility(
      visible = !follow && playingIndex != null && pagerState.currentPage != playingIndex,
      enter = fadeIn(tween(200)),
      exit = fadeOut(tween(150)),
      modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
    ) {
      FilledTonalButton(onClick = { follow = true }) { Text(stringResource(R.string.memorize_sessions_follow)) }
    }
  }
}

/** One mushaf page, drawn as in the reader, with the session's marks on it. */
@Composable
private fun SessionPage(
  page: Int,
  renderer: PageRenderer,
  marks: Set<Mark>,
  onTapped: (com.quran.data.model.SuraAyah, Int?) -> Unit,
) {
  val tapped by rememberUpdatedState(onTapped)
  val preview = remember(page) { renderer.page(page) { ayah, word -> tapped(ayah, word) } }
  DisposableEffect(preview) { onDispose { preview.release() } }
  // only what changed is sent (the word being heard changes several times a second)
  val applied = remember(preview) { mutableStateOf(emptySet<Mark>()) }
  SideEffect {
    val before = applied.value
    if (before != marks) {
      val actions = (before - marks).map { HighlightAction.Unhighlight(it.info()) } +
        (marks - before).map { HighlightAction.Highlight(it.info()) }
      preview.apply(actions)
      applied.value = marks
    }
  }
  AndroidView(factory = { preview.view }, modifier = Modifier.fillMaxSize())
}

private fun Mark.info() = HighlightInfo(sura, ayah, word, type, scrollToAyah = false)

/** What the marks' colours mean, and how many words each. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(counts: SessionCounts) {
  val night = MaterialTheme.colorScheme.surface.luminance() < 0.5f
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
  ) {
    LegendItem(MaterialTheme.colorScheme.primary, stringResource(R.string.memorize_legend_recited), counts.recited)
    if (counts.hinted > 0) {
      LegendItem(if (night) Color(0xFF4DB6AC) else Color(0xFF00897B), stringResource(R.string.memorize_legend_hinted), counts.hinted)
    }
    if (counts.missed > 0) {
      LegendItem(if (night) Color(0xFFCE93D8) else Color(0xFFFB8C00), stringResource(R.string.memorize_legend_missed), counts.missed)
    }
    if (counts.mistakes > 0) {
      LegendItem(if (night) Color(0xFFEF5350) else Color(0xFFE53935), stringResource(R.string.memorize_legend_mistakes), counts.mistakes)
    }
  }
}

@Composable
private fun LegendItem(color: Color, label: String, count: Int) {
  // the screen's locale (its digits), followed if it changes
  val locale = LocalConfiguration.current.locales[0]
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
    Spacer(Modifier.width(8.dp))
    Text(
      "$label ${String.format(locale, "%d", count)}",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/** Play, pause, 5 seconds back and on, and where it is. */
@Composable
private fun Player(
  recording: Recording?,
  playback: RecordingPlayer.Playback?,
  onPlay: () -> Unit,
  onPause: () -> Unit,
  onSeek: (Long) -> Unit,
  onSkip: (Long) -> Unit,
) {
  val durationMs = recording?.durationMs ?: 0L
  val positionMs = playback?.positionMs ?: 0L
  val timeline = remember(recording) { recording?.let(::timelineOf).orEmpty() }
  var dragging by remember { mutableStateOf(false) }
  var dragged by remember { mutableFloatStateOf(0f) }
  val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
  val positionLabel = stringResource(R.string.memorize_sessions_position)
  Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
    SessionTimeline(
      words = timeline,
      durationMs = durationMs,
      positionMs = if (dragging) (dragged * durationMs).toLong() else positionMs,
      description = stringResource(R.string.memorize_sessions_timeline),
      onSeek = onSeek,
      modifier = Modifier.padding(horizontal = 8.dp),
    )
    Slider(
      value = if (dragging) dragged else fraction,
      onValueChange = {
        dragging = true
        dragged = it
      },
      onValueChangeFinished = {
        onSeek((dragged * durationMs).toLong())
        dragging = false
      },
      enabled = durationMs > 0,
      modifier = Modifier.semantics { contentDescription = positionLabel },
    )
    Row(modifier = Modifier.fillMaxWidth()) {
      val digits = MaterialTheme.typography.labelMedium.merge(TextStyle(fontFeatureSettings = "tnum"))
      val shown = if (dragging) (dragged * durationMs).toLong() else positionMs
      Text(DateUtils.formatElapsedTime(shown / 1000), style = digits, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Spacer(Modifier.weight(1f))
      Text(DateUtils.formatElapsedTime(durationMs / 1000), style = digits, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
      modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
      IconButton(onClick = { onSkip(-SKIP_MS) }, enabled = playback != null) {
        Icon(MemorizeIcons.Replay5, contentDescription = stringResource(R.string.memorize_sessions_back))
      }
      val playing = playback?.isPlaying == true
      FilledIconButton(onClick = if (playing) onPause else onPlay, enabled = durationMs > 0, modifier = Modifier.size(56.dp)) {
        Icon(
          if (playing) QuranIcons.Pause else QuranIcons.PlayArrow,
          contentDescription = stringResource(if (playing) R.string.memorize_recordings_pause else R.string.memorize_recordings_play),
          modifier = Modifier.size(32.dp),
        )
      }
      IconButton(onClick = { onSkip(SKIP_MS) }, enabled = playback != null) {
        Icon(MemorizeIcons.Forward5, contentDescription = stringResource(R.string.memorize_sessions_forward))
      }
    }
  }
}

private const val SKIP_MS = 5_000L
