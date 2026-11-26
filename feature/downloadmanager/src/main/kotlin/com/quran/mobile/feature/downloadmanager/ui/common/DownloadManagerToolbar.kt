package com.quran.mobile.feature.downloadmanager.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.quran.labs.androidquran.common.ui.core.LocalQuranColors
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.mobile.feature.downloadmanager.R

@Composable
fun DownloadManagerToolbar(
  title: String,
  onBackPressed: (() -> Unit),
  actions: @Composable (RowScope.() -> Unit) = {}
) {
  val quranColors = LocalQuranColors.current
  Column {
    TopAppBar(
      title = {
        Text(
          text = title,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      },
      navigationIcon = {
        IconButton(onClick = onBackPressed) {
          Icon(
            imageVector = QuranIcons.ArrowBack,
            contentDescription = stringResource(R.string.audio_manager_navigate_up)
          )
        }
      },
      colors = TopAppBarDefaults.topAppBarColors(
        containerColor = quranColors.toolbarBackground,
        titleContentColor = quranColors.onToolbar,
        navigationIconContentColor = quranColors.onToolbar,
        actionIconContentColor = quranColors.onToolbar
      ),
      actions = actions
    )
    HorizontalDivider(color = quranColors.divider)
  }
}
