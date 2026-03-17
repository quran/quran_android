package com.quran.labs.androidquran.tv.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.quran.labs.androidquran.tv.ui.theme.QuranGold
import com.quran.labs.androidquran.tv.ui.theme.QuranGreen

@Composable
fun TvHomeScreen(
  onNavigate: (String) -> Unit = {},
  modifier: Modifier = Modifier
) {
  val cards = remember { homeCards }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(32.dp)
    ) {
      // Header
      Text(
        text = "Quran",
        style = MaterialTheme.typography.displayMedium,
        color = QuranGreen,
        modifier = Modifier.padding(bottom = 24.dp)
      )

      // Main content grid
      LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize()
      ) {
        items(cards) { card ->
          HomeCardItem(
            card = card,
            onClick = { onNavigate(card.id) }
          )
        }
      }
    }
  }
}

@Composable
fun HomeCardItem(
  card: HomeCard,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  var isFocused by remember { mutableStateOf(false) }

  Card(
    onClick = onClick,
    modifier = modifier
      .width(300.dp)
      .height(180.dp)
      .border(
        width = if (isFocused) 3.dp else 0.dp,
        color = QuranGreen,
        shape = RoundedCornerShape(16.dp)
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
                QuranGreen.copy(alpha = 0.2f),
                QuranGreen.copy(alpha = 0.1f)
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
        .padding(24.dp),
      contentAlignment = Alignment.CenterStart
    ) {
      Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center
      ) {
        Text(
          text = card.title,
          style = MaterialTheme.typography.headlineMedium,
          color = if (isFocused) QuranGold else MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = card.description,
          style = MaterialTheme.typography.bodyMedium,
          color = if (isFocused) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          },
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(top = 8.dp)
        )
      }
    }
  }
}
