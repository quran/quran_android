package com.quran.labs.androidquran.hybrid.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.LocalQuranColors
import com.quran.labs.androidquran.util.QuranUtils
import kotlin.math.roundToInt

private val WIRD_GOAL_PRESETS = listOf(4, 10, 20, 30, 40)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DailyWirdGoalDialog(
  currentGoalPages: Int,
  onSave: (Int) -> Unit,
  onDismiss: () -> Unit
) {
  val quranColors = LocalQuranColors.current
  val isDark = !quranColors.isLightToolbar
  val headingColor = if (isDark) quranColors.onToolbar else MaterialTheme.colorScheme.onBackground
  var selectedGoal by remember(currentGoalPages) {
    mutableIntStateOf(currentGoalPages.coerceIn(1, 604))
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = quranColors.cardSurface,
    titleContentColor = headingColor,
    textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    shape = RoundedCornerShape(24.dp),
    title = {
      Text(
        text = stringResource(R.string.hybrid_hub_wird_goal_title),
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold
      )
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Text(
          text = stringResource(R.string.hybrid_hub_wird_goal_subtitle),
          fontSize = 13.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          StepperCircleButton(
            label = "-",
            onClick = { selectedGoal = (selectedGoal - 1).coerceAtLeast(1) }
          )
          Text(
            text = stringResource(
              R.string.hybrid_hub_wird_goal_unit,
              QuranUtils.getLocalizedNumber(selectedGoal)
            ),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = headingColor
          )
          StepperCircleButton(
            label = "+",
            onClick = { selectedGoal = (selectedGoal + 1).coerceAtMost(604) }
          )
        }

        Slider(
          value = selectedGoal.coerceIn(1, 100).toFloat(),
          onValueChange = { selectedGoal = it.roundToInt().coerceIn(1, 604) },
          valueRange = 1f..100f,
          colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
          )
        )

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          WIRD_GOAL_PRESETS.forEach { preset ->
            val selected = selectedGoal == preset
            val pillBg = if (selected) {
              MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.18f else 0.12f)
            } else {
              if (isDark) quranColors.surfaceElevated else MaterialTheme.colorScheme.surfaceVariant
            }
            val pillBorder = if (selected) {
              MaterialTheme.colorScheme.primary
            } else {
              quranColors.divider
            }
            Box(
              modifier = Modifier
                .clip(CircleShape)
                .background(pillBg)
                .border(1.dp, pillBorder, CircleShape)
                .semantics(mergeDescendants = true) {}
                .clickable(role = Role.Button) { selectedGoal = preset }
                .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
              Text(
                text = QuranUtils.getLocalizedNumber(preset),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.primary else headingColor
              )
            }
          }
        }
        Spacer(modifier = Modifier.height(2.dp))
      }
    },
    confirmButton = {
      TextButton(
        onClick = { onSave(selectedGoal) },
        modifier = Modifier
          .semantics(mergeDescendants = true) {}
          .testTag("hub_wird_goal_save")
      ) {
        Text(
          text = stringResource(R.string.hybrid_hub_save),
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary
        )
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(
          text = stringResource(R.string.hybrid_hub_cancel),
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  )
}

@Composable
private fun StepperCircleButton(
  label: String,
  onClick: () -> Unit
) {
  val quranColors = LocalQuranColors.current
  val isDark = !quranColors.isLightToolbar
  val bg = if (isDark) quranColors.surfaceElevated else MaterialTheme.colorScheme.surfaceVariant
  val ink = if (isDark) quranColors.onToolbar else MaterialTheme.colorScheme.onBackground
  Box(
    modifier = Modifier
      .size(34.dp)
      .clip(CircleShape)
      .background(bg)
      .border(1.dp, quranColors.divider, CircleShape)
      .semantics(mergeDescendants = true) {}
      .clickable(role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = label,
      color = ink,
      fontSize = 16.sp,
      fontWeight = FontWeight.Bold
    )
  }
}
