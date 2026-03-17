package com.quran.labs.androidquran.tv.juz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
fun TvJuzGridScreen(
  onJuzClick: (Int) -> Unit = {},
  modifier: Modifier = Modifier
) {
  val juzList = remember { getAllJuz() }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .padding(32.dp)
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Header
      Text(
        text = "Juz",
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 24.dp)
      )

      // Juz grid - 5 columns for TV (30 juz fits nicely in 6 rows)
      LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
      ) {
        items(juzList) { juz ->
          JuzCard(
            juz = juz,
            onClick = { onJuzClick(juz.startingPage) }
          )
        }
      }
    }
  }
}

@Composable
fun JuzCard(
  juz: JuzModel,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  var isFocused by remember { mutableStateOf(false) }

  Card(
    onClick = onClick,
    modifier = modifier
      .width(220.dp)
      .height(140.dp)
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
        .padding(20.dp),
      contentAlignment = Alignment.Center
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        // Juz number badge
        Box(
          modifier = Modifier
            .background(
              if (isFocused) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.secondary,
              shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
          Text(
            text = "${juz.number}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold
          )
        }

        Text(
          text = juz.name,
          style = MaterialTheme.typography.titleLarge,
          color = if (isFocused) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(top = 12.dp),
          textAlign = TextAlign.Center
        )

        Text(
          text = "Pages ${juz.startingPage}-${juz.endingPage}",
          style = MaterialTheme.typography.bodyMedium,
          color = if (isFocused) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 8.dp),
          textAlign = TextAlign.Center
        )
      }
    }
  }
}
