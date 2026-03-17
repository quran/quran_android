package com.quran.labs.androidquran.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.tv.ui.theme.QuranGold

@Composable
fun TvHomeScreen(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .background(MaterialTheme.colorScheme.background)
      .padding(32.dp),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Text(
        text = "Quran for Android TV",
        style = MaterialTheme.typography.displayLarge,
        color = QuranGold
      )
      Spacer(modifier = Modifier.height(16.dp))
      Text(
        text = "Coming Soon",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground
      )
    }
  }
}
