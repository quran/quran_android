package com.quran.labs.androidquran.tv.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text

/**
 * Simple setting item model
 */
data class SettingItem(
  val id: String,
  val title: String,
  val description: String,
  val type: SettingType
)

sealed class SettingType {
  data class Toggle(val defaultValue: Boolean) : SettingType()
  data class Navigation(val route: String) : SettingType()
  data class Action(val action: () -> Unit) : SettingType()
}

/**
 * Sample settings for demonstration
 */
val sampleSettings = listOf(
  SettingItem(
    id = "dark_mode",
    title = "Dark Mode",
    description = "Use dark theme",
    type = SettingType.Toggle(false)
  ),
  SettingItem(
    id = "arabic_names",
    title = "Arabic Surah Names",
    description = "Show surah names in Arabic",
    type = SettingType.Toggle(true)
  ),
  SettingItem(
    id = "translations",
    title = "Translations",
    description = "Manage Quran translations",
    type = SettingType.Navigation("translations")
  ),
  SettingItem(
    id = "audio",
    title = "Audio Settings",
    description = "Configure audio playback",
    type = SettingType.Navigation("audio")
  ),
  SettingItem(
    id = "about",
    title = "About",
    description = "App version and information",
    type = SettingType.Navigation("about")
  )
)

@Composable
fun TvSettingsScreen(
  modifier: Modifier = Modifier
) {
  val settings = remember { sampleSettings.toMutableList() }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .padding(32.dp)
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Header
      Text(
        text = "Settings",
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 24.dp)
      )

      // Settings list
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        items(settings) { setting ->
          SettingCard(setting = setting)
        }
      }
    }
  }
}

@Composable
fun SettingCard(
  setting: SettingItem,
  modifier: Modifier = Modifier
) {
  var isFocused by remember { mutableStateOf(false) }

  // Handle toggle state
  var checkedState by remember {
    mutableStateOf(
      when (setting.type) {
        is SettingType.Toggle -> (setting.type as SettingType.Toggle).defaultValue
        else -> false
      }
    )
  }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(100.dp)
      .background(
        if (isFocused) {
          Brush.horizontalGradient(
            colors = listOf(
              MaterialTheme.colorScheme.surfaceVariant,
              MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
            )
          )
        } else {
          Brush.horizontalGradient(
            colors = listOf(
              MaterialTheme.colorScheme.surface,
              MaterialTheme.colorScheme.surfaceVariant
            )
          )
        }
      )
      .border(
        width = if (isFocused) 2.dp else 1.dp,
        color = if (isFocused) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp)
      )
      .padding(24.dp)
      .onFocusChanged { focusState ->
        isFocused = focusState.hasFocus
      }
      .clickable {
        when (setting.type) {
          is SettingType.Toggle -> {
            checkedState = !checkedState
          }
          is SettingType.Navigation -> {
            // Handle navigation
          }
          is SettingType.Action -> {
            (setting.type as SettingType.Action).action()
          }
        }
      },
    contentAlignment = Alignment.CenterStart
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.Center
      ) {
        Text(
          text = setting.title,
          style = MaterialTheme.typography.titleLarge,
          color = if (isFocused) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
          fontWeight = FontWeight.SemiBold
        )
        Text(
          text = setting.description,
          style = MaterialTheme.typography.bodyMedium,
          color = if (isFocused) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 4.dp)
        )
      }

      // Setting control
      when (setting.type) {
        is SettingType.Toggle -> {
          Switch(
            checked = checkedState,
            onCheckedChange = { checkedState = it }
          )
        }
        is SettingType.Navigation -> {
          Text(
            text = ">",
            style = MaterialTheme.typography.headlineMedium,
            color = if (isFocused) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        is SettingType.Action -> {
          Text(
            text = ">",
            style = MaterialTheme.typography.headlineMedium,
            color = if (isFocused) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}
