package com.quran.labs.androidquran.tv.surah

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

@Composable
fun TvSurahGridScreen(
  onSurahClick: (Int) -> Unit = {},
  onBackClick: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val surahs = remember { getAllSurahs() }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .padding(32.dp)
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Header
      Text(
        text = "Surahs",
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 24.dp)
      )

      // Surah grid - 6 columns for TV
      LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        items(surahs) { surah ->
          SurahCard(
            surah = surah,
            onClick = { onSurahClick(surah.number) }
          )
        }
      }
    }
  }
}

@Composable
fun SurahCard(
  surah: SurahModel,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  var isFocused by remember { mutableStateOf(false) }

  Card(
    onClick = onClick,
    modifier = modifier
      .width(180.dp)
      .height(120.dp)
      .border(
        width = if (isFocused) 2.dp else 0.dp,
        color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(12.dp)
      )
      .onFocusChanged { focusState ->
        isFocused = focusState.hasFocus
      }
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          if (isFocused) {
            Brush.verticalGradient(
              colors = listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                MaterialTheme.colorScheme.surfaceVariant
              )
            )
          } else {
            Brush.verticalGradient(
              colors = listOf(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.surface
              )
            )
          }
        )
        .padding(16.dp),
      contentAlignment = Alignment.Center
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        // Surah number badge
        Box(
          modifier = Modifier
            .background(
              if (isFocused) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.secondary,
              shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Text(
            text = "${surah.number}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold
          )
        }

        Text(
          text = surah.name,
          style = MaterialTheme.typography.titleMedium,
          color = if (isFocused) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(top = 8.dp),
          textAlign = TextAlign.Center,
          maxLines = 1
        )

        Text(
          text = "${surah.numberOfAyahs} verses",
          style = MaterialTheme.typography.labelMedium,
          color = if (isFocused) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 4.dp),
          textAlign = TextAlign.Center
        )

        Row(
          modifier = Modifier.padding(top = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Text(
            text = if (surah.revelationType == "Meccan") "Meccan" else "Medinan",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}
