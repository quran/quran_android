package com.quran.labs.androidquran.hybrid.hub

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.LocalQuranColors
import com.quran.labs.androidquran.hybrid.contract.AdaptiveMotionTokens
import com.quran.labs.androidquran.hybrid.contract.DailyWirdState
import com.quran.labs.androidquran.hybrid.contract.VerticalHairlineDivider
import com.quran.labs.androidquran.util.QuranUtils

@Composable
internal fun DailyWirdHeroCard(
  dailyWird: DailyWirdState,
  fallbackSuraName: String,
  onContinueClick: () -> Unit,
  onEditGoalClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val quranColors = LocalQuranColors.current
  val isDark = !quranColors.isLightToolbar
  val headingColor = if (isDark) quranColors.onToolbar else MaterialTheme.colorScheme.onBackground
  val cardShape = RoundedCornerShape(22.dp)
  val cardBorder = if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.05f)
  val cardGradient = remember(isDark, quranColors.surfaceElevated, quranColors.cardSurface) {
    Brush.verticalGradient(
      colors = listOf(
        if (isDark) quranColors.surfaceElevated else Color.White,
        quranColors.cardSurface
      )
    )
  }

  val suraName = dailyWird.lastSuraName.ifBlank { fallbackSuraName }
  val localizedAyah = QuranUtils.getLocalizedNumber(dailyWird.lastAyah.coerceAtLeast(1))
  val localizedJuz = QuranUtils.getLocalizedNumber(dailyWird.lastJuz.coerceIn(1, 30))
  val localizedPage = QuranUtils.getLocalizedNumber(dailyWird.lastPage.coerceIn(1, 604))

  val localizedRead = QuranUtils.getLocalizedNumber(dailyWird.pagesReadToday.coerceAtLeast(0))
  val localizedGoal = QuranUtils.getLocalizedNumber(dailyWird.goalPages.coerceAtLeast(1))
  val formattedPercent = HybridNumberFormatter.formatPercent(dailyWird.progressPercent)

  val animatedProgress by animateFloatAsState(
    targetValue = dailyWird.progressFraction,
    animationSpec = AdaptiveMotionTokens.TabSwitchFloatSpec,
    label = "dailyWirdProgress"
  )

  Column(
    modifier = modifier
      .fillMaxWidth()
      .shadow(
        elevation = if (isDark) 7.dp else 6.dp,
        shape = cardShape,
        ambientColor = quranColors.shadowTint,
        spotColor = quranColors.shadowTint
      )
      .clip(cardShape)
      .background(cardGradient)
      .border(width = 1.dp, color = cardBorder, shape = cardShape)
      .padding(start = 16.dp, top = 15.dp, end = 16.dp, bottom = 14.dp)
      .testTag("hub_hero_card"),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Top
    ) {
      Column(
        modifier = Modifier
          .weight(1f)
          .padding(end = 10.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
          Icon(
            imageVector = BookArchitecturalIcon,
            contentDescription = null,
            tint = quranColors.editorialAccent,
            modifier = Modifier.size(13.dp)
          )
          Text(
            text = stringResource(R.string.hybrid_hub_continue_reading),
            color = quranColors.editorialAccent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
          )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
          text = stringResource(R.string.hybrid_hub_sura_ayah_format, suraName, localizedAyah),
          color = headingColor,
          fontSize = 16.5.sp,
          fontWeight = FontWeight.Bold,
          lineHeight = 24.sp,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = stringResource(R.string.hybrid_hub_juz_format, localizedJuz),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            maxLines = 1
          )
          Spacer(modifier = Modifier.size(6.dp))
          VerticalHairlineDivider(
            height = 10.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
          )
          Spacer(modifier = Modifier.size(6.dp))
          Text(
            text = stringResource(R.string.hybrid_hub_page_full_format, localizedPage),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            maxLines = 1
          )
        }
      }

      Row(
        modifier = Modifier
          .padding(top = 2.dp)
          .shadow(
            elevation = if (isDark) 7.dp else 6.dp,
            shape = CircleShape,
            ambientColor = quranColors.ctaBackground,
            spotColor = quranColors.ctaBackground
          )
          .clip(CircleShape)
          .background(quranColors.ctaBackground)
          .clickable(role = Role.Button, onClick = onContinueClick)
          .padding(horizontal = 13.dp, vertical = 7.dp)
          .testTag("hub_continue_cta"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Text(
          text = stringResource(R.string.hybrid_hub_resume_cta),
          color = quranColors.onCta,
          fontSize = 12.5.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1
        )
        Icon(
          imageVector = ChevronLeftArchitecturalIcon,
          contentDescription = null,
          tint = quranColors.onCta,
          modifier = Modifier.size(13.dp)
        )
      }
    }

    HorizontalDivider(
      thickness = 1.dp,
      color = quranColors.divider.copy(alpha = 0.75f)
    )

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .clickable(role = Role.Button, onClick = onEditGoalClick)
        .padding(vertical = 2.dp)
        .testTag("hub_daily_wird_row")
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = stringResource(R.string.hybrid_hub_daily_wird_percent_format, formattedPercent),
          color = headingColor,
          fontSize = 11.5.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1
        )
        Text(
          text = stringResource(R.string.hybrid_hub_wird_pages_format, localizedRead, localizedGoal),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 11.5.sp,
          maxLines = 1
        )
      }
      Spacer(modifier = Modifier.height(6.dp))
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(6.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.surfaceVariant)
      ) {
        if (animatedProgress > 0f) {
          Box(
            modifier = Modifier
              .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
              .fillMaxHeight()
              .clip(CircleShape)
              .background(quranColors.editorialAccent)
          )
        }
      }
    }
  }
}
