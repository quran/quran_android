package com.quran.mobile.feature.memorize.recording

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quran.mobile.feature.memorize.R

/** Recording sessions on a wide screen: the list, and the chosen session played back beside it. */
@Composable
internal fun SessionsSideBySide(
  width: Dp,
  list: @Composable () -> Unit,
  session: (@Composable () -> Unit)?,
) {
  Row(Modifier.fillMaxSize()) {
    Box(Modifier.width(if (width >= 1200.dp) 400.dp else 340.dp).fillMaxHeight()) { list() }
    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Box(Modifier.weight(1f).fillMaxHeight()) {
      if (session != null) {
        session()
      } else {
        Text(
          stringResource(R.string.memorize_sessions_choose),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.align(Alignment.Center).padding(28.dp),
        )
      }
    }
  }
}
