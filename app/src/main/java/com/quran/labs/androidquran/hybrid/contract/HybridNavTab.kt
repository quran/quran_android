package com.quran.labs.androidquran.hybrid.contract

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.LocalQuranColors
import com.quran.labs.androidquran.common.ui.core.QuranIcons

enum class HybridNavTab(@StringRes val labelRes: Int) {
  READ(R.string.hybrid_nav_read),
  INDEX(R.string.hybrid_nav_index),
  SEARCH(R.string.hybrid_nav_search),
  BOOKMARKS(R.string.hybrid_nav_bookmarks)
}

@Composable
fun FloatingBottomNav(
  selectedTab: HybridNavTab,
  onTabSelected: (HybridNavTab) -> Unit,
  modifier: Modifier = Modifier
) {
  val quranColors = LocalQuranColors.current
  val isDark = !quranColors.isLightToolbar
  val navShape = RoundedCornerShape(26.dp)
  val itemShape = RoundedCornerShape(20.dp)
  val navInsets = WindowInsets.navigationBars.asPaddingValues()

  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(bottom = navInsets.calculateBottomPadding() + 20.dp)
      .padding(horizontal = 16.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(58.dp)
        .clip(navShape)
        .background(quranColors.navBg)
        .border(width = 1.dp, color = quranColors.navBorder, shape = navShape)
        .padding(horizontal = 6.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      val tabs = HybridNavTab.entries
      tabs.forEachIndexed { index, tab ->
        val selected = tab == selectedTab
        val activeFill = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.14f else 0.11f)
        val containerColor by animateColorAsState(
          targetValue = if (selected) activeFill else Color.Transparent,
          animationSpec = AdaptiveMotionTokens.TabSwitchColorSpec,
          label = "navTabContainer"
        )
        val contentColor by animateColorAsState(
          targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          },
          animationSpec = AdaptiveMotionTokens.TabSwitchColorSpec,
          label = "navTabContent"
        )
        val label = stringResource(tab.labelRes)
        val interactionSource = remember { MutableInteractionSource() }

        Column(
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(itemShape)
            .background(containerColor)
            .clickable(
              interactionSource = interactionSource,
              indication = null,
              role = Role.Tab,
              onClick = { onTabSelected(tab) }
            ),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Icon(
            imageVector = tab.iconVector(),
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(19.dp)
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = label,
            color = contentColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        if (index < tabs.lastIndex) {
          VerticalHairlineDivider(
            height = 10.dp,
            color = quranColors.divider.copy(alpha = 0.85f)
          )
        }
      }
    }
  }
}

private fun HybridNavTab.iconVector(): ImageVector = when (this) {
  HybridNavTab.READ -> QuranIcons.MenuBook
  HybridNavTab.INDEX -> IndexListIcon
  HybridNavTab.SEARCH -> QuranIcons.Search
  HybridNavTab.BOOKMARKS -> QuranIcons.BookmarkBorder
}
