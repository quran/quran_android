package com.quran.mobile.feature.progress.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.quran.data.model.bookmark.ReadingBookmarkPurpose
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.ReadingBookmarkSlots
import com.quran.mobile.feature.progress.R
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun bookmarkColor(slot: ReadingBookmarkType): Color = colorResource(ReadingBookmarkSlots[slot].colorResourceId)

/** The bookmarks that are on, with where each is; the chosen one is highlighted. */
@Composable
internal fun BookmarkList(
  bookmarks: ImmutableList<BookmarkView>,
  selected: ReadingBookmarkType?,
  canAdd: Boolean,
  onSelect: (ReadingBookmarkType) -> Unit,
  onAdd: () -> Unit,
  modifier: Modifier = Modifier,
  showTitle: Boolean = true,
) {
  Column(modifier.verticalScroll(rememberScrollState()).padding(vertical = 20.dp)) {
    if (showTitle) {
      Text(
        stringResource(R.string.progress_section_bookmarks),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
      )
    }
    bookmarks.forEach { bookmark ->
      val isSelected = bookmark.slot == selected
      Row(
        Modifier
          .padding(horizontal = 8.dp, vertical = 2.dp)
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
          .selectable(selected = isSelected, role = Role.Tab) { onSelect(bookmark.slot) }
          .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          if (bookmark.placed) ProgressIcons.Bookmark else QuranIcons.BookmarkBorder,
          contentDescription = null,
          tint = bookmarkColor(bookmark.slot),
          modifier = Modifier.size(24.dp),
        )
        Column(Modifier.padding(start = 14.dp)) {
          Text(bookmark.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
          Text(
            listSummary(bookmark),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
          )
        }
      }
    }
    if (canAdd) {
      Row(
        Modifier
          .padding(horizontal = 8.dp, vertical = 4.dp)
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .selectable(selected = false, role = Role.Button, onClick = onAdd)
          .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(ProgressIcons.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Text(
          stringResource(R.string.progress_add_bookmark),
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(start = 14.dp),
        )
      }
    }
  }
}

/** E.g. "Reading · juz 14 · day 12 of 30". */
@Composable
private fun listSummary(bookmark: BookmarkView): String {
  val separator = stringResource(R.string.progress_separator)
  val parts = buildList {
    add(purposeName(bookmark.purpose))
    val juz = bookmark.juz
    if (juz == null) {
      add(stringResource(R.string.progress_not_placed))
    } else {
      add(stringResource(R.string.progress_juz, juz))
      if (bookmark.purpose == ReadingBookmarkPurpose.READING) {
        val dayN = bookmark.dayN
        when {
          dayN != null -> add(stringResource(R.string.progress_day_of, dayN, bookmark.targetDays))
          bookmark.targetDays == 0 -> add(stringResource(R.string.progress_no_deadline))
        }
      }
    }
  }
  return parts.joinToString(separator)
}

/**
 * One bookmark: its name and purpose, a sentence on its cycle, the juz line, the days (pages above,
 * minutes below) and the cycle's facts.
 */
@Composable
internal fun BookmarkDetail(
  bookmark: BookmarkView,
  reminder: ReminderView,
  uncreditedTodayMinutes: Int,
  onAllowNotifications: () -> Unit,
  onReminderTime: () -> Unit,
  onStartCycle: () -> Unit,
  modifier: Modifier = Modifier,
  dark: Boolean = false,
  /** False where the app bar already says it (a phone). */
  showName: Boolean = true,
) {
  val color = bookmarkColor(bookmark.slot)
  val panel = panelColor()
  val context = context()
  var selectedJuz by rememberSaveable(bookmark.slot) { mutableStateOf<Int?>(null) }
  Column(
    modifier
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 28.dp, vertical = 22.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      if (showName) {
        Text(bookmark.name, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
      }
      Label(purposeName(bookmark.purpose))
      Spacer(Modifier.weight(1f))
      if (!bookmark.hasHistory && bookmark.placed) Label(stringResource(R.string.progress_sample), quiet = true)
    }
    Text(
      sentence(bookmark),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
    )
    if (!bookmark.placed) {
      Text(
        stringResource(R.string.progress_not_placed_detail),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      return@Column
    }

    Panel(panel) {
      Caption(stringResource(R.string.progress_juz_title), paceText(bookmark))
      JuzLine(
        line = bookmark.line,
        color = color,
        selectedJuz = selectedJuz,
        onSelect = { selectedJuz = it },
        describe = { juz -> juzDetail(context, bookmark.line, juz) },
        modifier = Modifier.padding(top = 4.dp),
      )
      JuzLegend(
        color = color,
        showPace = bookmark.line.pace != null,
        now = stringResource(R.string.progress_legend_now),
        pace = stringResource(R.string.progress_legend_pace),
        read = stringResource(R.string.progress_legend_read),
        modifier = Modifier.padding(top = 6.dp),
      )
      selectedJuz?.let { juz ->
        Text(
          juzDetail(context, bookmark.line, juz),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(top = 8.dp),
        )
      }
    }

    Panel(panel) {
      val goal = bookmark.goal
      Caption(
        stringResource(R.string.progress_days_title),
        listOfNotNull(
          stringResource(R.string.progress_pages_per_day),
          goal?.let { stringResource(R.string.progress_goal, it) },
        ).joinToString(stringResource(R.string.progress_separator)),
      )
      val columns = if (bookmark.hasHistory) bookmark.days else SampleProgress.reading(bookmark.days)
      DayBars(
        columns = columns,
        aboveColor = color,
        belowColor = MaterialTheme.colorScheme.primary,
        aboveLine = goal,
        belowLine = if (bookmark.hasHistory) bookmark.averageMinutes else null,
        describe = { column -> dayDescription(context, column) },
        panel = panel,
        faded = !bookmark.hasHistory,
        dark = dark,
      )
      Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
          listOfNotNull(
            stringResource(R.string.progress_minutes_per_day),
            bookmark.averageMinutes?.takeIf { bookmark.hasHistory }?.let { stringResource(R.string.progress_average, it) },
          ).joinToString(stringResource(R.string.progress_separator)),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f),
        )
        bookmark.todayMinutes?.takeIf { bookmark.hasHistory }?.let {
          Text(
            stringResource(R.string.progress_today_value, it),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      if (!bookmark.hasHistory) Note(stringResource(R.string.progress_no_history))
      if (uncreditedTodayMinutes > 0) {
        Note(pluralStringResource(R.plurals.progress_uncredited, uncreditedTodayMinutes, uncreditedTodayMinutes))
      }
    }

    Facts(bookmark, reminder, panel, onAllowNotifications, onReminderTime)

    if (bookmark.purpose == ReadingBookmarkPurpose.READING) {
      TextButton(onClick = onStartCycle, modifier = Modifier.padding(top = 4.dp)) {
        Text(stringResource(R.string.progress_start_cycle))
      }
    }
  }
}

/** E.g. "Started Sunday 3 November · finishes by 2 December · page 262, Al-Hijr 15:1". */
@Composable
private fun sentence(bookmark: BookmarkView): String {
  val context = context()
  val separator = stringResource(R.string.progress_separator)
  val parts = buildList {
    bookmark.cycleStartDay?.let { add(stringResource(R.string.progress_started, context.dayWithWeekday(it))) }
    bookmark.followedSinceDay?.let { add(stringResource(R.string.progress_kept_since, context.dayWithWeekday(it))) }
    bookmark.finishByDay?.let { day ->
      add(
        if (bookmark.overdue) {
          stringResource(R.string.progress_overdue_since, context.dayOnly(day))
        } else {
          stringResource(R.string.progress_finishes_by, context.dayOnly(day))
        }
      )
    }
    bookmark.place?.let { add(stringResource(R.string.progress_at, it.page, it.suraName, it.sura, it.ayah)) }
  }
  return parts.joinToString(separator)
}

@Composable
private fun paceText(bookmark: BookmarkView): String? {
  val ahead = bookmark.ahead ?: return null
  return when {
    ahead > 0 -> pluralStringResource(R.plurals.progress_ahead, ahead, ahead, bookmark.targetDays)
    ahead < 0 -> pluralStringResource(R.plurals.progress_behind, -ahead, -ahead, bookmark.targetDays)
    else -> stringResource(R.string.progress_on_pace, bookmark.targetDays)
  }
}

private fun juzDetail(context: android.content.Context, line: JuzLineState, juz: Int): String {
  val pages = line.juz.first { it.juz == juz }
  return context.getString(R.string.progress_juz_detail, juz, pages.firstPage, pages.lastPage, pages.read, pages.pages)
}

private fun dayDescription(context: android.content.Context, column: DayColumn): String {
  val above = column.above ?: return context.getString(R.string.progress_day_to_come, column.label)
  val res = context.resources
  return context.getString(
    R.string.progress_day_semantics,
    column.label,
    res.getQuantityString(R.plurals.progress_pages, above, above),
    res.getQuantityString(R.plurals.progress_minutes, column.below ?: 0, column.below ?: 0),
  )
}

/** The cycle's facts in one row: time read and pages, the last cycle, the reminder. */
@Composable
private fun Facts(
  bookmark: BookmarkView,
  reminder: ReminderView,
  panel: Color,
  onAllowNotifications: () -> Unit,
  onReminderTime: () -> Unit,
) {
  val context = context()
  val reading = bookmark.purpose == ReadingBookmarkPurpose.READING
  Surface(color = panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
    // side by side where there's room, else one under the other
    BoxWithConstraints {
      val stacked = maxWidth < 560.dp
      val facts: @Composable (Modifier) -> Unit = { fact ->
        FactsContent(bookmark, reminder, reading, stacked, fact, onAllowNotifications, onReminderTime)
      }
      if (stacked) {
        Column(Modifier.padding(vertical = 6.dp)) { facts(Modifier.fillMaxWidth().padding(vertical = 6.dp)) }
      } else {
        Row(Modifier.height(IntrinsicSize.Min).padding(vertical = 12.dp)) { facts(Modifier.weight(1f)) }
      }
    }
  }
}

@Composable
private fun FactsContent(
  bookmark: BookmarkView,
  reminder: ReminderView,
  reading: Boolean,
  stacked: Boolean,
  fact: Modifier,
  onAllowNotifications: () -> Unit,
  onReminderTime: () -> Unit,
) {
  val context = context()
  val divider: @Composable () -> Unit = {
    if (stacked) {
      HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)
    } else {
      Divider()
    }
  }
  Fact(fact, duration(bookmark.cycleReadingMs), stringResource(R.string.progress_fact_reading, pages(bookmark.cyclePages)))
  if (reading) {
    divider()
    val lastDays = bookmark.lastCycleDays
    val lastStart = bookmark.lastCycleStartDay
    val lastEnd = bookmark.lastCycleEndDay
    if (lastDays != null && lastStart != null && lastEnd != null) {
      Fact(fact, days(lastDays), stringResource(R.string.progress_fact_last, context.dayRange(lastStart, lastEnd)))
    } else {
      Fact(fact, stringResource(R.string.progress_fact_none), stringResource(R.string.progress_fact_last_label))
    }
    // the reminder is about an open khatma with a deadline
    if (bookmark.dayN == null) return
    divider()
    when {
      !reminder.on -> Fact(fact, null, stringResource(R.string.progress_fact_reminder_off), onClick = onReminderTime)
      !reminder.allowed -> Fact(
        fact,
        null,
        stringResource(R.string.progress_reminder_needs_permission),
        action = stringResource(R.string.progress_allow),
        onClick = onAllowNotifications,
      )
      else -> Fact(
        fact, context.timeOfDay(reminder.minuteOfDay), stringResource(R.string.progress_fact_reminder), onClick = onReminderTime
      )
    }
  }
}

@Composable
private fun Fact(
  modifier: Modifier,
  value: String?,
  label: String,
  action: String? = null,
  onClick: (() -> Unit)? = null,
) {
  val clickable = if (onClick != null) Modifier.selectable(selected = false, role = Role.Button, onClick = onClick) else Modifier
  Column(modifier.then(clickable).padding(horizontal = 16.dp, vertical = 2.dp)) {
    value?.let {
      Text(it, style = MaterialTheme.typography.titleMedium.tabular(), color = MaterialTheme.colorScheme.onSurface)
    }
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    action?.let {
      Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
    }
  }
}

@Composable
private fun Divider() {
  VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceVariant)
}

@Composable
internal fun Panel(color: Color, content: @Composable () -> Unit) {
  Surface(
    color = color,
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
  ) {
    Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp)) { content() }
  }
}

@Composable
internal fun Caption(title: String, detail: String?) {
  Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
  }
}

@Composable
internal fun Note(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(top = 8.dp),
  )
}

/** A small outlined label, e.g. the purpose; [quiet] for "Sample data". */
@Composable
internal fun Label(text: String, quiet: Boolean = false) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    border = BorderStroke(1.dp, if (quiet) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.outline),
    color = Color.Transparent,
  ) {
    Text(
      text,
      style = if (quiet) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
      color = if (quiet) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = if (quiet) 8.dp else 10.dp, vertical = 3.dp),
    )
  }
}

/** Shown in a pane with nothing chosen yet. */
@Composable
internal fun Choose(text: String, modifier: Modifier = Modifier) {
  Column(modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
