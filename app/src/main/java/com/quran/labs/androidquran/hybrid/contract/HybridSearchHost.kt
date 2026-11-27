package com.quran.labs.androidquran.hybrid.contract

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.LocalQuranColors
import com.quran.labs.androidquran.common.ui.core.QuranIcons

interface HybridSearchActionHandler {
  fun openPage(page: Int, sura: Int, ayah: Int)
  fun playAyah(sura: Int, ayah: Int, page: Int)
  fun toggleBookmark(sura: Int, ayah: Int, page: Int)
}

interface HybridSearchHost {
  @Composable
  fun HybridSearchScreen(
    initialQuery: String?,
    actionHandler: HybridSearchActionHandler,
    bottomContentPadding: Dp,
    onBackOrClose: (() -> Unit)?,
    modifier: Modifier
  )
}

object DefaultHybridSearchHost : HybridSearchHost {
  @Composable
  override fun HybridSearchScreen(
    initialQuery: String?,
    actionHandler: HybridSearchActionHandler,
    bottomContentPadding: Dp,
    onBackOrClose: (() -> Unit)?,
    modifier: Modifier
  ) {
    val quranColors = LocalQuranColors.current
    val pillShape = RoundedCornerShape(999.dp)

    Column(
      modifier = modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .padding(horizontal = 16.dp)
        .padding(top = 12.dp, bottom = bottomContentPadding),
      verticalArrangement = Arrangement.Top
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(46.dp)
          .clip(pillShape)
          .background(quranColors.cardSurface)
          .border(
            width = 1.5.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
            shape = pillShape
          )
          .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
      ) {
        Icon(
          imageVector = QuranIcons.Search,
          contentDescription = stringResource(R.string.hybrid_nav_search),
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(18.dp)
        )
        Text(
          text = initialQuery?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.hybrid_search_placeholder),
          color = if (initialQuery.isNullOrBlank()) {
            MaterialTheme.colorScheme.onSurfaceVariant
          } else {
            MaterialTheme.colorScheme.onSurface
          },
          fontSize = 15.sp,
          fontWeight = FontWeight.Medium
        )
      }
      Spacer(modifier = Modifier.height(16.dp))
      Text(
        text = stringResource(R.string.hybrid_search_stub_message),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp
      )
    }
  }
}
