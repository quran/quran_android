package com.quran.mobile.feature.appicon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.common.ui.core.QuranAccent
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.common.ui.core.modifier.autoMirror
import com.quran.mobile.feature.appicon.R
import com.quran.mobile.feature.appicon.model.AppIconOption
import com.quran.mobile.feature.appicon.model.AppIconSection
import com.quran.mobile.feature.appicon.state.AppIconEvent
import com.quran.mobile.feature.appicon.state.AppIconState
import kotlinx.collections.immutable.persistentListOf

@Composable
fun AppIconScreen(state: AppIconState, onBackPressed: () -> Unit) {
  Column(
    modifier = Modifier
      .background(MaterialTheme.colorScheme.surface)
      .fillMaxSize()
      .windowInsetsPadding(
        WindowInsets.systemBars
          .union(WindowInsets.displayCutout)
          .only(WindowInsetsSides.Horizontal)
      )
  ) {
    TopAppBar(
      title = {
        Text(
          text = stringResource(R.string.app_icon_title),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      },
      navigationIcon = {
        IconButton(onClick = onBackPressed) {
          Icon(
            imageVector = QuranIcons.ArrowBack,
            contentDescription = "",
            modifier = Modifier.autoMirror()
          )
        }
      }
    )

    AppIconList(state)
  }
}

@Composable
private fun AppIconList(state: AppIconState) {
  LazyColumn(contentPadding = WindowInsets.navigationBars.asPaddingValues()) {
    state.sections.forEach { section ->
      item(key = section.id) {
        Text(
          text = stringResource(section.title),
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
        )
      }

      items(section.options, key = { it.id }) { option ->
        AppIconRow(
          option = option,
          previewSize = section.previewSize,
          isSelected = option == state.selectedOption,
          onClick = { state.eventSink(AppIconEvent.Select(option)) }
        )
      }
    }

    item(key = "note") {
      Text(
        text = stringResource(R.string.app_icon_change_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp)
      )
    }
  }
}

@Composable
private fun AppIconRow(
  option: AppIconOption,
  previewSize: AppIconSection.PreviewSize,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    modifier = Modifier
      .fillMaxWidth()
      .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 8.dp)
  ) {
    val iconSize = when (previewSize) {
      AppIconSection.PreviewSize.LARGE -> 56.dp
      AppIconSection.PreviewSize.REGULAR -> 48.dp
    }
    AppIconImage(icon = option.icon, size = iconSize)

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(option.name),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface
      )
      if (option.isDefault) {
        Text(
          text = stringResource(R.string.app_icon_default),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    if (isSelected) {
      val accent = option.accent
      Icon(
        imageVector = QuranIcons.Check,
        contentDescription = null,
        tint = if (isSystemInDarkTheme()) accent.dark else accent.light
      )
    } else {
      Spacer(modifier = Modifier.size(24.dp))
    }
  }
}

@Preview
@Composable
private fun AppIconScreenPreview() {
  val options = listOf("Navy & Gold", "Teal & White", "Blue", "Green").mapIndexed { index, name ->
    AppIconOption(
      id = name,
      launcherAlias = name,
      name = android.R.string.unknownName,
      icon = android.R.drawable.sym_def_app_icon,
      accent = QuranAccent.NavyGold,
      isDefault = index == 0
    )
  }

  QuranTheme {
    AppIconScreen(
      state = AppIconState(
        sections = persistentListOf(
          AppIconSection(
            id = "signature",
            title = R.string.app_icon_section_signature,
            previewSize = AppIconSection.PreviewSize.LARGE,
            options = persistentListOf(options[0], options[1])
          ),
          AppIconSection(
            id = "colors",
            title = R.string.app_icon_section_colors,
            previewSize = AppIconSection.PreviewSize.REGULAR,
            options = persistentListOf(options[2], options[3])
          )
        ),
        selectedOption = options[0]
      ),
      onBackPressed = {}
    )
  }
}
