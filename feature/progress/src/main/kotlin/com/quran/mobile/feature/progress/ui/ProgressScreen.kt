package com.quran.mobile.feature.progress.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.WidthClass
import com.quran.labs.androidquran.common.ui.core.widthClassOf
import com.quran.mobile.feature.progress.R
import androidx.appcompat.R as AppCompatR

/** Sura names, for the screens that list suras. */
internal val LocalSuraNames = staticCompositionLocalOf<(Int) -> String> { { sura -> sura.toString() } }

/** What the user chose on the screen: the section, and the bookmark or session open in it. */
@Immutable
data class ProgressSelection(
  val section: ProgressSection = ProgressSection.BOOKMARKS,
  val bookmark: ReadingBookmarkType? = null,
  val session: String? = null,
)

/** What the screen asks of its host. */
@Immutable
class ProgressActions(
  val onSelection: (ProgressSelection) -> Unit = {},
  val onAddBookmark: () -> Unit = {},
  val onAllowNotifications: () -> Unit = {},
  val onReminderTime: () -> Unit = {},
  val onStartCycle: (ReadingBookmarkType) -> Unit = {},
  val onPlayRecording: (String) -> Unit = {},
  val onBack: () -> Unit = {},
)

/**
 * Progress, by the window's width (Material's navigation by size):
 * - expanded (a tablet, e.g. the MatePad 12 X) and medium: a navigation rail, the list, and the
 *   chosen bookmark or session beside it (the list narrower where there's less room);
 * - compact (a phone): a navigation bar and one pane at a time.
 *
 * On a wide screen a bookmark is always open (the first one, until another is chosen).
 */
@Composable
fun ProgressScreen(
  state: ProgressUiState,
  selection: ProgressSelection,
  actions: ProgressActions,
  dark: Boolean,
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(
    modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.surface)
  ) {
    val width = maxWidth
    when (widthClassOf(width)) {
      WidthClass.Expanded -> Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Rail(selection, actions)
        // a tablet in portrait gives the list less, so the days beside it fit
        TwoPanes(state, selection, actions, dark, listWidth = if (width >= 1200.dp) 300.dp else 240.dp)
      }
      WidthClass.Medium -> Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Rail(selection, actions)
        TwoPanes(state, selection, actions, dark, listWidth = 220.dp)
      }
      WidthClass.Compact -> CompactProgress(state, selection, actions, dark)
    }
  }
}

private data class Destination(val section: ProgressSection, val label: Int, val icon: ImageVector)

private val destinations = listOf(
  Destination(ProgressSection.BOOKMARKS, R.string.progress_section_bookmarks, ProgressIcons.Bookmark),
  Destination(ProgressSection.MEMORIZING, R.string.progress_section_memorizing, ProgressIcons.Memorizing),
  Destination(ProgressSection.SALAH, R.string.progress_section_salah, ProgressIcons.Mosque),
  Destination(ProgressSection.SESSIONS, R.string.progress_section_sessions, ProgressIcons.Sessions),
)

@Composable
private fun BackButton(onBack: () -> Unit) {
  IconButton(onClick = onBack) {
    Icon(
      QuranIcons.ArrowBack,
      contentDescription = stringResource(AppCompatR.string.abc_action_bar_up_description),
    )
  }
}

@Composable
private fun Rail(selection: ProgressSelection, actions: ProgressActions) {
  NavigationRail(
    containerColor = panelColor(),
    header = { BackButton(actions.onBack) },
    windowInsets = WindowInsets(0, 0, 0, 0),
  ) {
    destinations.forEach { destination ->
      NavigationRailItem(
        selected = selection.section == destination.section,
        onClick = { actions.onSelection(selection.copy(section = destination.section)) },
        icon = { Icon(destination.icon, contentDescription = null) },
        label = { Text(stringResource(destination.label)) },
        colors = NavigationRailItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
      )
    }
  }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TwoPanes(
  state: ProgressUiState,
  selection: ProgressSelection,
  actions: ProgressActions,
  dark: Boolean,
  listWidth: Dp,
) {
  when (selection.section) {
    ProgressSection.BOOKMARKS -> {
      val open = state.bookmarks.firstOrNull { it.slot == selection.bookmark } ?: state.bookmarks.firstOrNull()
      BookmarkList(
        bookmarks = state.bookmarks,
        selected = open?.slot,
        canAdd = state.canAddBookmark,
        onSelect = { actions.onSelection(selection.copy(bookmark = it)) },
        onAdd = actions.onAddBookmark,
        modifier = Modifier.width(listWidth).fillMaxHeight(),
      )
      VerticalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
      if (open == null) {
        Choose(stringResource(R.string.progress_choose_bookmark), Modifier.weight(1f))
      } else {
        BookmarkDetail(
          bookmark = open,
          reminder = state.reminder,
          uncreditedTodayMinutes = state.uncreditedTodayMinutes,
          onAllowNotifications = actions.onAllowNotifications,
          onReminderTime = actions.onReminderTime,
          onStartCycle = { actions.onStartCycle(open.slot) },
          modifier = Modifier.weight(1f).fillMaxHeight(),
          dark = dark,
        )
      }
    }
    ProgressSection.MEMORIZING -> MemorizingPane(state.memorizing, Modifier.weight(1f).fillMaxHeight(), dark)
    ProgressSection.SALAH -> SalahPane(state.salah, Modifier.weight(1f).fillMaxHeight(), dark)
    ProgressSection.SESSIONS -> {
      SessionList(
        sessions = state.sessions,
        selected = selection.session,
        onSelect = { actions.onSelection(selection.copy(session = it)) },
        modifier = Modifier.width(listWidth + 40.dp).fillMaxHeight(),
      )
      VerticalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
      val open = state.sessions.firstOrNull { it.id == selection.session }
      if (open == null) {
        Choose(stringResource(R.string.progress_choose_session), Modifier.weight(1f))
      } else {
        SessionDetail(open, actions.onPlayRecording, Modifier.weight(1f).fillMaxHeight())
      }
    }
  }
}

/** One pane at a time: a list, or (once chosen) what's chosen in it, with Back to the list. */
@Composable
private fun OnePane(
  state: ProgressUiState,
  selection: ProgressSelection,
  actions: ProgressActions,
  dark: Boolean,
  modifier: Modifier = Modifier,
  showTitles: Boolean = true,
) {
  Box(modifier.fillMaxSize()) {
    when (selection.section) {
      ProgressSection.BOOKMARKS -> {
        val open = state.bookmarks.firstOrNull { it.slot == selection.bookmark }
        BackHandler(enabled = open != null) { actions.onSelection(selection.copy(bookmark = null)) }
        if (open == null) {
          BookmarkList(
            bookmarks = state.bookmarks,
            selected = null,
            canAdd = state.canAddBookmark,
            onSelect = { actions.onSelection(selection.copy(bookmark = it)) },
            onAdd = actions.onAddBookmark,
            modifier = Modifier.fillMaxSize(),
            showTitle = showTitles,
          )
        } else {
          BookmarkDetail(
            bookmark = open,
            reminder = state.reminder,
            uncreditedTodayMinutes = state.uncreditedTodayMinutes,
            onAllowNotifications = actions.onAllowNotifications,
            onReminderTime = actions.onReminderTime,
            onStartCycle = { actions.onStartCycle(open.slot) },
            modifier = Modifier.fillMaxSize(),
            dark = dark,
            showName = showTitles,
          )
        }
      }
      ProgressSection.MEMORIZING -> MemorizingPane(state.memorizing, Modifier.fillMaxSize(), dark)
      ProgressSection.SALAH -> SalahPane(state.salah, Modifier.fillMaxSize(), dark)
      ProgressSection.SESSIONS -> {
        val open = state.sessions.firstOrNull { it.id == selection.session }
        BackHandler(enabled = open != null) { actions.onSelection(selection.copy(session = null)) }
        if (open == null) {
          SessionList(
            sessions = state.sessions,
            selected = null,
            onSelect = { actions.onSelection(selection.copy(session = it)) },
            modifier = Modifier.fillMaxSize(),
            showTitle = showTitles,
          )
        } else {
          SessionDetail(open, actions.onPlayRecording, Modifier.fillMaxSize(), showTitle = showTitles)
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactProgress(state: ProgressUiState, selection: ProgressSelection, actions: ProgressActions, dark: Boolean) {
  val inDetail = (selection.section == ProgressSection.BOOKMARKS && state.bookmarks.any { it.slot == selection.bookmark }) ||
    (selection.section == ProgressSection.SESSIONS && state.sessions.any { it.id == selection.session })
  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          val openBookmark = state.bookmarks.firstOrNull { it.slot == selection.bookmark }
          val openSession = state.sessions.firstOrNull { it.id == selection.session }
          Text(
            when {
              selection.section == ProgressSection.BOOKMARKS && openBookmark != null -> openBookmark.name
              selection.section == ProgressSection.SESSIONS && openSession != null -> openSession.title
              else -> stringResource(destinations.first { it.section == selection.section }.label)
            }
          )
        },
        navigationIcon = {
          BackButton {
            when {
              selection.section == ProgressSection.BOOKMARKS && inDetail -> actions.onSelection(selection.copy(bookmark = null))
              selection.section == ProgressSection.SESSIONS && inDetail -> actions.onSelection(selection.copy(session = null))
              else -> actions.onBack()
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
      )
    },
    bottomBar = {
      NavigationBar(containerColor = panelColor()) {
        destinations.forEach { destination ->
          NavigationBarItem(
            selected = selection.section == destination.section,
            onClick = { actions.onSelection(selection.copy(section = destination.section)) },
            icon = { Icon(destination.icon, contentDescription = null) },
            label = { Text(stringResource(destination.label)) },
            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
          )
        }
      }
    },
    containerColor = MaterialTheme.colorScheme.surface,
  ) { padding ->
    Column(Modifier.padding(padding)) {
      OnePane(state, selection, actions, dark, Modifier.weight(1f), showTitles = false)
    }
  }
}
