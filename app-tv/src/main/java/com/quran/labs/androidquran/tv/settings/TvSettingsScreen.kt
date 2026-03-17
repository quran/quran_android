package com.quran.labs.androidquran.tv.settings

import android.content.Context
import android.content.SharedPreferences
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import kotlinx.coroutines.launch

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
  data class Toggle(val defaultValue: Boolean, val key: String) : SettingType()
  data class Navigation(val route: String) : SettingType()
  data class Action(val action: () -> Unit) : SettingType()
}

/**
 * SharedPreferences keys
 */
object QuranPreferences {
  const val PREFS_NAME = "com.quran.labs.androidquran.preferences"
  const val PREF_ARABIC_NAMES = "useArabicNames"
  const val PREF_NIGHT_MODE = "nightMode"
  const val PREF_KEEP_SCREEN_ON = "keepScreenOn"
  const val PREF_LANDSCAPE_MODE = "landscapeMode"
  const val PREF_TRANSLATION_TEXT_SIZE = "translationTextSize"
}

/**
 * Sample settings for TV
 */
fun getTvSettings(): List<SettingItem> {
  return listOf(
    SettingItem(
      id = "arabic_names",
      title = "Arabic Surah Names",
      description = "Show surah names in Arabic",
      type = SettingType.Toggle(false, QuranPreferences.PREF_ARABIC_NAMES)
    ),
    SettingItem(
      id = "night_mode",
      title = "Night Mode",
      description = "Use dark theme",
      type = SettingType.Toggle(false, QuranPreferences.PREF_NIGHT_MODE)
    ),
    SettingItem(
      id = "audio_settings",
      title = "Audio Settings",
      description = "Quran playback and audio controls",
      type = SettingType.Navigation("audio")
    ),
    SettingItem(
      id = "keep_screen_on",
      title = "Keep Screen On",
      description = "Prevent screen from sleeping while reading",
      type = SettingType.Toggle(true, QuranPreferences.PREF_KEEP_SCREEN_ON)
    ),
    SettingItem(
      id = "about",
      title = "About",
      description = "App version and information",
      type = SettingType.Action({})
    )
  )
}

@Composable
fun TvSettingsScreen(
  onNavigateToAudio: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val settings = remember { getTvSettings() }

  // Get SharedPreferences
  val prefs = remember {
    context.getSharedPreferences(QuranPreferences.PREFS_NAME, Context.MODE_PRIVATE)
  }

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
          TvSettingCard(
            setting = setting,
            prefs = prefs,
            onNavigate = { route ->
              if (route == "audio") {
                onNavigateToAudio()
              }
            }
          )
        }
      }
    }
  }
}

@Composable
fun TvSettingCard(
  setting: SettingItem,
  prefs: SharedPreferences,
  onNavigate: (String) -> Unit = {},
  modifier: Modifier = Modifier
) {
  var isFocused by remember { mutableStateOf(false) }

  // Handle toggle state
  val initialValue = remember {
    when (setting.type) {
      is SettingType.Toggle -> prefs.getBoolean(
        (setting.type as SettingType.Toggle).key,
        (setting.type as SettingType.Toggle).defaultValue
      )
      else -> false
    }
  }
  var checkedState by remember { mutableStateOf(initialValue) }

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
      .clickable {
        when (setting.type) {
          is SettingType.Toggle -> {
            checkedState = !checkedState
            prefs.edit().putBoolean((setting.type as SettingType.Toggle).key, checkedState).apply()
          }
          is SettingType.Navigation -> {
            onNavigate((setting.type as SettingType.Navigation).route)
          }
          is SettingType.Action -> {
            (setting.type as SettingType.Action).action()
          }
        }
      }
      .onFocusChanged { focusState ->
        isFocused = focusState.hasFocus
      }
      .padding(24.dp),
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
            onCheckedChange = { checked ->
              checkedState = checked
              prefs.edit().putBoolean((setting.type as SettingType.Toggle).key, checked).apply()
            }
          )
        }
        is SettingType.Navigation, is SettingType.Action -> {
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
