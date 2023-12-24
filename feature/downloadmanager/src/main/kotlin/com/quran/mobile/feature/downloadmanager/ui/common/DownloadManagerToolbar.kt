package com.quran.mobile.feature.downloadmanager.ui.common

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.quran.labs.androidquran.common.ui.core.modifier.autoMirror

@Composable
fun DownloadManagerToolbar(
  title: String,
  backgroundColor: Color,
  tintColor: Color,
  onBackPressed: (() -> Unit),
  actions: @Composable (RowScope.() -> Unit) = {}
) {
  TopAppBar(
    title = {
      Text(
        text = title,
        color = tintColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    },
    navigationIcon = {
      IconButton(onClick = onBackPressed) {
        Icon(
          imageVector = Icons.Filled.ArrowBack,
          contentDescription = "",
          tint = tintColor,
          modifier = Modifier.autoMirror()
        )
      }
    },
    backgroundColor = backgroundColor,
    actions = actions
  )
}
