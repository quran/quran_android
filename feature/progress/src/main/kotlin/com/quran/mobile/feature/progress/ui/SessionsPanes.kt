package com.quran.mobile.feature.progress.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.mobile.feature.progress.R
import kotlinx.collections.immutable.ImmutableList

/** Memorizing: ayat held per juz (and per sura of the juz chosen), then accuracy and hints per day. */
@Composable
internal fun MemorizingPane(view: MemorizingView, modifier: Modifier = Modifier, dark: Boolean = false) {
  val panel = panelColor()
  val context = context()
  var selectedJuz by rememberSaveable(view.focusJuz) { mutableStateOf(view.focusJuz) }
  val accent = MaterialTheme.colorScheme.primary
  Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 22.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        stringResource(R.string.progress_section_memorizing),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.weight(1f),
      )
      if (!view.hasHistory) Label(stringResource(R.string.progress_sample), quiet = true)
    }
    Text(
      stringResource(R.string.progress_held_explained),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
    )
    Panel(panel) {
      Caption(stringResource(R.string.progress_held_title), null)
      HeldChart(
        held = view.juz,
        color = accent,
        selectedJuz = selectedJuz,
        onSelect = { selectedJuz = it },
        describe = { juz ->
          context.getString(R.string.progress_juz, juz.number) + ": " + context.getString(R.string.progress_held_of, juz.held, juz.ayat)
        },
        panel = panel,
        faded = !view.hasHistory,
      )
      selectedJuz?.let { juz ->
        val held = view.juz.firstOrNull { it.number == juz }
        if (held != null) {
          Text(
            stringResource(R.string.progress_held_summary, juz, held.held, held.ayat),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
          )
        }
        view.suras[juz].orEmpty().forEach { sura -> HeldRow(sura.number, sura.held, sura.ayat, accent) }
      }
    }
    Panel(panel) {
      Caption(stringResource(R.string.progress_accuracy_title), stringResource(R.string.progress_accuracy_per_day))
      DayBars(
        columns = if (view.hasHistory) view.days else SampleProgress.memorizing(view.days),
        aboveColor = accent,
        belowColor = MaterialTheme.colorScheme.tertiary,
        aboveLine = null,
        belowLine = null,
        describe = { column -> "${column.label}: ${column.above ?: 0}%, ${column.below ?: 0}" },
        panel = panel,
        faded = !view.hasHistory,
        dark = dark,
        aboveHeight = 150.dp,
        belowHeight = 100.dp,
      )
      Text(
        stringResource(R.string.progress_hints_per_day),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
      )
      if (!view.hasHistory) Note(stringResource(R.string.progress_no_sessions))
    }
  }
}

@Composable
private fun HeldRow(sura: Int, held: Int, ayat: Int, color: Color) {
  val naming = LocalSuraNames.current
  Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(
      naming(sura),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.width(140.dp),
    )
    // the share held, as a bar the width of the row
    Row(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
      val share = if (ayat == 0) 0f else held.toFloat() / ayat
      if (share > 0f) Spacer(Modifier.weight(share).height(8.dp).background(color))
      if (share < 1f) Spacer(Modifier.weight(1f - share))
    }
    Text(
      stringResource(R.string.progress_held_of, held, ayat),
      style = MaterialTheme.typography.bodySmall.tabular(),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = 12.dp),
    )
  }
}

/** Salah: prayers followed above the days, hints below. */
@Composable
internal fun SalahPane(view: SalahView, modifier: Modifier = Modifier, dark: Boolean = false) {
  val panel = panelColor()
  Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 22.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        stringResource(R.string.progress_salah_title),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.weight(1f),
      )
      if (!view.hasHistory) Label(stringResource(R.string.progress_sample), quiet = true)
    }
    Spacer(Modifier.height(18.dp))
    Panel(panel) {
      Caption(stringResource(R.string.progress_days_title), stringResource(R.string.progress_prayers_per_day))
      DayBars(
        columns = if (view.hasHistory) view.days else SampleProgress.salah(view.days),
        aboveColor = MaterialTheme.colorScheme.primary,
        belowColor = MaterialTheme.colorScheme.tertiary,
        aboveLine = null,
        belowLine = null,
        describe = { column -> "${column.label}: ${column.above ?: 0}, ${column.below ?: 0}" },
        panel = panel,
        faded = !view.hasHistory,
        dark = dark,
        aboveHeight = 150.dp,
        belowHeight = 100.dp,
      )
      Text(
        stringResource(R.string.progress_salah_hints),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
      )
      if (!view.hasHistory) Note(stringResource(R.string.progress_no_sessions))
    }
  }
}

/** Sessions by day, newest first; the chosen one is highlighted. */
@Composable
internal fun SessionList(
  sessions: ImmutableList<SessionView>,
  selected: String?,
  onSelect: (String) -> Unit,
  modifier: Modifier = Modifier,
  showTitle: Boolean = true,
) {
  val context = context()
  Column(modifier.verticalScroll(rememberScrollState()).padding(vertical = 20.dp)) {
    if (showTitle) {
      Text(
        stringResource(R.string.progress_section_sessions),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
      )
    }
    if (sessions.isEmpty()) {
      Text(
        stringResource(R.string.progress_sessions_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp),
      )
    }
    var lastDay: Long? = null
    sessions.forEach { session ->
      if (session.day != lastDay) {
        lastDay = session.day
        Text(
          context.dayWithWeekday(session.day),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
        )
      }
      val isSelected = session.id == selected
      Row(
        Modifier
          .padding(horizontal = 8.dp, vertical = 2.dp)
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
          .selectable(selected = isSelected, role = Role.Tab) { onSelect(session.id) }
          .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(modeIcon(session.mode), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Column(Modifier.padding(start = 14.dp)) {
          Text(session.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
          Text(
            modeName(session.mode) + stringResource(R.string.progress_separator) +
              DateUtils.formatDateTime(context, session.startedAtMs, DateUtils.FORMAT_SHOW_TIME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            stringResource(R.string.progress_session_counts, session.recited, session.hinted, session.missed) +
              stringResource(R.string.progress_separator) + minutes(session.minutes),
            style = MaterialTheme.typography.bodySmall.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

/**
 * One session: its passage, a strip of its ayat (each as wide as its words, coloured by how it went),
 * then each ayah. A recorded session opens in Recording sessions, where it plays back on its pages
 * with its timeline.
 */
@Composable
internal fun SessionDetail(session: SessionView, onPlay: (String) -> Unit, modifier: Modifier = Modifier, showTitle: Boolean = true) {
  val context = context()
  val separator = stringResource(R.string.progress_separator)
  Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 22.dp)) {
    if (showTitle) {
      Text(session.title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
    }
    Text(
      listOfNotNull(
        modeName(session.mode),
        context.dayWithWeekday(session.day),
        DateUtils.formatDateTime(context, session.startedAtMs, DateUtils.FORMAT_SHOW_TIME),
        minutes(session.minutes),
        stringResource(R.string.progress_session_finished).takeIf { session.finished },
      ).joinToString(separator),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
    )
    session.recordingId?.let { id ->
      FilledTonalButton(onClick = { onPlay(id) }, modifier = Modifier.padding(bottom = 14.dp)) {
        Icon(ProgressIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.progress_play_recording), modifier = Modifier.padding(start = 8.dp))
      }
    }
    Panel(panelColor()) {
      Caption(
        stringResource(R.string.progress_session_ayat),
        stringResource(R.string.progress_session_counts, session.recited, session.hinted, session.missed),
      )
      AyahStrip(session.ayahs)
      Spacer(Modifier.height(10.dp))
      session.ayahs.forEachIndexed { index, ayah ->
        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(
            stringResource(R.string.progress_session_ayah, ayah.suraName, ayah.sura, ayah.ayah),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            stringResource(R.string.progress_ayah_counts, ayah.words, ayah.ayahWords, ayah.hinted, ayah.missed),
            style = MaterialTheme.typography.bodySmall.tabular(),
            color = if (ayah.hinted + ayah.missed == 0 && ayah.words >= ayah.ayahWords) {
              MaterialTheme.colorScheme.onSurfaceVariant
            } else {
              MaterialTheme.colorScheme.error
            },
          )
        }
      }
    }
  }
}

/**
 * The session's ayat in a row, each as wide as its words: given hints in teal, skipped words in the
 * skipped colour (as on the page), the rest quiet. It runs from the start side.
 */
@Composable
private fun AyahStrip(ayahs: ImmutableList<SessionAyahView>) {
  val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
  val quiet = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
  val hinted = if (dark) Color(0xFF4DB6AC) else Color(0xFF00897B)
  val skipped = if (dark) Color(0xFFCE93D8) else Color(0xFFFB8C00)
  Row(Modifier.fillMaxWidth().height(14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
    ayahs.forEach { ayah ->
      val color = when {
        ayah.missed > 0 -> skipped
        ayah.hinted > 0 -> hinted
        else -> quiet
      }
      Spacer(
        Modifier
          .weight(ayah.ayahWords.coerceAtLeast(1).toFloat())
          .height(14.dp)
          .clip(RoundedCornerShape(3.dp))
          .background(color)
      )
    }
  }
}

@Composable
private fun modeName(mode: String): String = stringResource(
  when (mode) {
    "SALAH" -> R.string.progress_mode_salah
    "RECITE" -> R.string.progress_mode_recite
    else -> R.string.progress_mode_memorize
  }
)

private fun modeIcon(mode: String): ImageVector = when (mode) {
  "SALAH" -> ProgressIcons.Mosque
  "RECITE" -> QuranIcons.MenuBook
  else -> QuranIcons.Mic
}
