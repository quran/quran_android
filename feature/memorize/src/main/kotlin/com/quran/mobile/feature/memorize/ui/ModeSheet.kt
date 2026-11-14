package com.quran.mobile.feature.memorize.ui

import android.app.Activity
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.memorize.R
import com.quran.mobile.feature.memorize.session.MemorizeState
import kotlinx.coroutines.launch

/** What the mode window shows: where the session starts, the ways to recite, and whether it records. */
internal data class ModeSheetState(
  /** E.g. "Al-Mulk, ayah 1". */
  val start: String,
  val modes: List<MemorizeState.Mode>,
  val record: RecordLine,
) {
  /** Whether the session will be recorded, and whether the user decides it here. */
  sealed interface RecordLine {
    /** Not recorded. */
    data object Off : RecordLine

    /** Recorded for "Record and contribute". */
    data object Contributing : RecordLine

    /** Recorded: "Record my recitation" is on. */
    data object Always : RecordLine

    /** "Record my recitation" asks each time: the user decides here. */
    data object Ask : RecordLine
  }
}

/**
 * The window the microphone opens: Memorization or Recitation (and salah after a long press).
 * Choosing one starts the session at once.
 */
internal object ModeSheet {

  fun show(
    activity: Activity,
    state: ModeSheetState,
    onChoose: (mode: MemorizeState.Mode, record: Boolean) -> Unit,
    onCancel: () -> Unit,
  ) {
    val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
    val host = ComposeView(activity)
    host.setContent {
      QuranTheme {
        ModeBottomSheet(
          state = state,
          onChoose = { mode, record ->
            root.removeView(host)
            onChoose(mode, record)
          },
          onCancel = {
            root.removeView(host)
            onCancel()
          },
        )
      }
    }
    // the sheet is a window of its own: its host takes no room
    root.addView(host, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeBottomSheet(
  state: ModeSheetState,
  onChoose: (MemorizeState.Mode, Boolean) -> Unit,
  onCancel: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scope = rememberCoroutineScope()
  ModalBottomSheet(
    onDismissRequest = onCancel,
    sheetState = sheetState,
    // on a tablet the ways to recite sit side by side, so the sheet may be wider than a phone's
    sheetMaxWidth = 840.dp,
    dragHandle = null,
    shape = RectangleShape,
    containerColor = Color.Transparent,
    contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    ModeSheetContent(state) { mode, record ->
      scope.launch { sheetState.hide() }.invokeOnCompletion { onChoose(mode, record) }
    }
  }
}

@Composable
internal fun ModeSheetContent(
  state: ModeSheetState,
  modifier: Modifier = Modifier,
  onChoose: (MemorizeState.Mode, Boolean) -> Unit,
) {
  var recordThis by rememberSaveable { mutableStateOf(false) }
  val record = when (state.record) {
    ModeSheetState.RecordLine.Off -> false
    ModeSheetState.RecordLine.Contributing, ModeSheetState.RecordLine.Always -> true
    ModeSheetState.RecordLine.Ask -> recordThis
  }
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerHighest)
      .navigationBarsPadding()
      .padding(bottom = 8.dp)
  ) {
    Box(
      modifier = Modifier
        .align(Alignment.CenterHorizontally)
        .padding(top = 18.dp)
        .size(width = 36.dp, height = 4.dp)
        .clip(RoundedCornerShape(percent = 100))
        .background(MaterialTheme.colorScheme.outlineVariant)
    )
    Text(
      text = stringResource(R.string.memorize_sheet_title),
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(top = 14.dp, start = 18.dp, end = 18.dp),
    )
    Text(
      text = stringResource(R.string.memorize_sheet_start, state.start),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 6.dp, bottom = 12.dp, start = 18.dp, end = 18.dp),
    )
    BoxWithConstraints {
      // side by side where there's room for them (a tablet), else one under the other
      val cards = maxWidth >= 600.dp
      val choices: @Composable (Modifier) -> Unit = { item ->
        state.modes.forEachIndexed { index, mode ->
          if (index > 0 && !cards) SheetDivider()
          val (icon, title, description) = when (mode) {
            MemorizeState.Mode.MEMORIZE -> Triple(
              MemorizeIcons.VisibilityOff,
              stringResource(R.string.memorize_mode_memorize_title),
              stringResource(R.string.memorize_mode_memorize_description),
            )
            MemorizeState.Mode.RECITE -> Triple(
              QuranIcons.MenuBook,
              stringResource(R.string.memorize_mode_recite_title),
              stringResource(R.string.memorize_mode_recite_description),
            )
            MemorizeState.Mode.SALAH -> Triple(
              MemorizeIcons.Mosque,
              stringResource(R.string.memorize_mode_salah_title),
              stringResource(R.string.memorize_mode_salah_description),
            )
          }
          if (cards) {
            ModeCard(icon, title, description, item) { onChoose(mode, record) }
          } else {
            ModeRow(icon, title, description) { onChoose(mode, record) }
          }
        }
      }
      if (cards) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 18.dp, vertical = 8.dp),
        ) { choices(Modifier.weight(1f).fillMaxHeight()) }
      } else {
        Column { choices(Modifier) }
      }
    }
    when (state.record) {
      ModeSheetState.RecordLine.Off -> Unit
      ModeSheetState.RecordLine.Contributing -> {
        SheetDivider()
        RecordedNote(stringResource(R.string.memorize_sheet_recorded_contribute))
      }
      ModeSheetState.RecordLine.Always -> {
        SheetDivider()
        RecordedNote(stringResource(R.string.memorize_sheet_recorded))
      }
      ModeSheetState.RecordLine.Ask -> {
        SheetDivider()
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = recordThis, role = Role.Switch, onValueChange = { recordThis = it })
            .heightIn(min = 56.dp)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
          Text(
            text = stringResource(R.string.memorize_sheet_record_this),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
          )
          Switch(checked = recordThis, onCheckedChange = null)
        }
      }
    }
  }
}

@Composable
private fun ModeRow(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(role = Role.Button, onClick = onClick)
      .heightIn(min = 72.dp)
      .padding(horizontal = 18.dp, vertical = 14.dp),
  ) {
    // level with the title, the description runs on below
    Icon(
      icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.padding(top = 1.dp).size(24.dp),
    )
    Spacer(Modifier.width(16.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
      Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
      )
    }
  }
}

@Composable
private fun ModeCard(icon: ImageVector, title: String, description: String, modifier: Modifier, onClick: () -> Unit) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = modifier,
  ) {
    Column(
      Modifier
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
      Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
      Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 12.dp),
      )
      Text(
        description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
      )
    }
  }
}

/** Says the session will be recorded (the user always knows). */
@Composable
private fun RecordedNote(text: String) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 48.dp)
      .padding(horizontal = 18.dp, vertical = 12.dp),
  ) {
    Box(
      modifier = Modifier
        .padding(horizontal = 8.dp)
        .size(8.dp)
        .clip(CircleShape)
        .background(RecordingRed)
    )
    // the text lines up with the ways to recite above
    Spacer(Modifier.width(16.dp))
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun SheetDivider() {
  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 18.dp))
}

/** The recording dot, as on the floating mic. */
internal val RecordingRed = Color(0xFFE53935)
