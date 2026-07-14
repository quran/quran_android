package com.quran.labs.androidquran.ui.fragment

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.QuranTheme

@Composable
fun BookmarksEmptyState(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 28.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Icon(
      imageVector = QuranIcons.BookmarkBorder,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.outline,
      modifier = Modifier.padding(bottom = 14.dp)
    )
    Text(
      text = stringResource(R.string.bookmarks_empty_title),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface,
      textAlign = TextAlign.Center
    )
    Text(
      text = stringResource(R.string.bookmarks_empty_subtitle),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 6.dp)
    )
  }
}

@Preview
@Preview("dark theme", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BookmarksEmptyStatePreview() {
  QuranTheme {
    Surface {
      BookmarksEmptyState()
    }
  }
}
