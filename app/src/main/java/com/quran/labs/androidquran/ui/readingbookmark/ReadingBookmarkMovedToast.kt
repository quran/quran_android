package com.quran.labs.androidquran.ui.readingbookmark

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranTheme

/**
 * The "movable reading bookmark" toast (design option B: a dark, bottom-anchored snackbar).
 * Shown once with the full education copy the first time a user ever saves a reading bookmark,
 * and as a shorter "moved" notice on every save after that.
 */
@Composable
internal fun ReadingBookmarkMovedToast(
  title: String,
  movedFromText: String?,
  body: String?,
  onUndo: (() -> Unit)?,
  onDismiss: (() -> Unit)?,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(13.dp))
      .background(MaterialTheme.colorScheme.inverseSurface)
      .padding(horizontal = 15.dp, vertical = 13.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.inverseOnSurface,
        modifier = Modifier.weight(1f)
      )
      if (onUndo != null) {
        Text(
          text = stringResource(R.string.undo),
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.inversePrimary,
          modifier = Modifier
            .clickable(onClick = onUndo)
            .padding(2.dp)
        )
      }
      if (onDismiss != null) {
        Spacer(modifier = Modifier.width(14.dp))
        Icon(
          imageVector = DismissIcon,
          contentDescription = stringResource(R.string.reading_bookmark_dismiss_education),
          tint = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f),
          modifier = Modifier
            .clickable(onClick = onDismiss)
            .padding(9.dp)
            .size(14.dp)
        )
      }
    }
    if (movedFromText != null) {
      Text(
        text = movedFromText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f),
        modifier = Modifier.padding(top = 4.dp)
      )
    }
    if (body != null) {
      Text(
        text = body,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f),
        modifier = Modifier.padding(top = 8.dp)
      )
    }
  }
}

@Preview
@Preview("dark theme", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReadingBookmarkMovedToastEducationPreview() {
  QuranTheme {
    Surface {
      ReadingBookmarkMovedToast(
        title = stringResource(R.string.reading_bookmark_movable_education_title),
        movedFromText = stringResource(R.string.reading_bookmark_moved_from, "Page 77"),
        body = stringResource(R.string.reading_bookmark_movable_education_body),
        onUndo = {},
        onDismiss = {},
        modifier = Modifier.padding(14.dp)
      )
    }
  }
}

@Preview("education, no previous bookmark")
@Composable
private fun ReadingBookmarkMovedToastEducationNoPreviousPreview() {
  QuranTheme {
    Surface {
      ReadingBookmarkMovedToast(
        title = stringResource(R.string.reading_bookmark_movable_education_title),
        movedFromText = null,
        body = stringResource(R.string.reading_bookmark_movable_education_body),
        onUndo = null,
        onDismiss = {},
        modifier = Modifier.padding(14.dp)
      )
    }
  }
}

@Preview("short, moved from a previous bookmark")
@Composable
private fun ReadingBookmarkMovedToastShortPreview() {
  QuranTheme {
    Surface {
      ReadingBookmarkMovedToast(
        title = stringResource(R.string.reading_bookmark_moved_from_title, "Sura An-Nisā' Ayah 1"),
        movedFromText = null,
        body = null,
        onDismiss = null,
        onUndo = {},
        modifier = Modifier.padding(14.dp)
      )
    }
  }
}

@Preview("short, no previous bookmark")
@Composable
private fun ReadingBookmarkMovedToastShortAddedPreview() {
  QuranTheme {
    Surface {
      ReadingBookmarkMovedToast(
        title = stringResource(R.string.reading_bookmark_added),
        movedFromText = null,
        body = null,
        onUndo = null,
        onDismiss = null,
        modifier = Modifier.padding(14.dp)
      )
    }
  }
}
