package com.quran.labs.androidquran.tv.bookmark

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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

/**
 * Simple bookmark model for TV UI
 * In production, this should integrate with the common:bookmark module
 */
data class BookmarkItem(
  val id: Long,
  val title: String,
  val description: String,
  val page: Int,
  val sura: Int? = null,
  val ayah: Int? = null
)

/**
 * Sample bookmarks for demonstration
 * In production, load from the bookmark database
 */
val sampleBookmarks = listOf(
  BookmarkItem(
    id = 1,
    title = "Ayat al-Kursi",
    description = "The Throne Verse - Surah Al-Baqarah 2:255",
    page = 44,
    sura = 2,
    ayah = 255
  ),
  BookmarkItem(
    id = 2,
    title = "Surah Al-Fatihah",
    description = "The Opening - Page 1",
    page = 1,
    sura = 1,
    ayah = null
  ),
  BookmarkItem(
    id = 3,
    title = "Surah Yasin",
    description = "The Heart of the Quran",
    page = 440,
    sura = 36,
    ayah = null
  ),
  BookmarkItem(
    id = 4,
    title = "Surah Ar-Rahman",
    description = "The Beneficent",
    page = 531,
    sura = 55,
    ayah = null
  ),
  BookmarkItem(
    id = 5,
    title = "Last Read - Page 604",
    description = "Continue reading",
    page = 604,
    sura = null,
    ayah = null
  )
)

@Composable
fun TvBookmarksScreen(
  onBookmarkClick: (Int) -> Unit = {},
  modifier: Modifier = Modifier
) {
  val bookmarks = remember { sampleBookmarks }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .padding(32.dp)
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Header
      Text(
        text = "Bookmarks",
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 24.dp)
      )

      // Bookmarks list
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        items(bookmarks) { bookmark ->
          BookmarkCard(
            bookmark = bookmark,
            onClick = { onBookmarkClick(bookmark.page) }
          )
        }
      }
    }
  }
}

@Composable
fun BookmarkCard(
  bookmark: BookmarkItem,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  var isFocused by remember { mutableStateOf(false) }

  Card(
    onClick = onClick,
    modifier = modifier
      .fillMaxWidth()
      .height(100.dp)
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
            Brush.horizontalGradient(
              colors = listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                MaterialTheme.colorScheme.surfaceVariant
              )
            )
          } else {
            Brush.horizontalGradient(
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
            text = bookmark.title,
            style = MaterialTheme.typography.titleLarge,
            color = if (isFocused) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
          )
          Text(
            text = bookmark.description,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isFocused) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
              else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
          )
        }

        // Page indicator
        Box(
          modifier = Modifier
            .background(
              if (isFocused) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.secondary,
              shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
          Text(
            text = "P. ${bookmark.page}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
  }
}
