package com.quran.labs.androidquran.hybrid.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.LocalQuranColors
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.mobile.common.ui.core.R as UiCoreR

@Composable
fun FrostedAudioCapsule(
  qariName: String,
  suraName: String,
  activeAyah: SuraAyah,
  isPlaying: Boolean,
  isPaused: Boolean,
  isPreparing: Boolean,
  elapsedMs: Long,
  progressFraction: Float,
  downloadProgress: Int?,
  showNetworkPrompt: Boolean,
  onPlayPauseClick: () -> Unit,
  onExpandStudyDrawer: () -> Unit,
  onCancelDownload: () -> Unit,
  onAllowMobileDownload: () -> Unit,
  modifier: Modifier = Modifier
) {
  val quranColors = LocalQuranColors.current
  val accent = MaterialTheme.colorScheme.primary
  val isDark = !quranColors.isLightToolbar
  val floatBorder = Color.White.copy(alpha = if (isDark) 0.13f else 0.88f)
  val capsuleShape = RoundedCornerShape(28.dp)

  Box(
    modifier = modifier
      .fillMaxWidth()
      .navigationBarsPadding()
      .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(54.dp)
        .shadow(
          elevation = if (isDark) 12.dp else 10.dp,
          shape = capsuleShape,
          ambientColor = quranColors.shadowTint.copy(alpha = if (isDark) 0.29f else 0.13f),
          spotColor = quranColors.shadowTint.copy(alpha = if (isDark) 0.45f else 0.24f)
        )
        .clip(capsuleShape)
        .background(quranColors.glassFloatBg.copy(alpha = 1f))
        .border(width = 1.dp, color = floatBorder, shape = capsuleShape)
        .clickable(role = Role.Button, onClick = onExpandStudyDrawer)
        .padding(start = 10.dp, end = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      when {
        showNetworkPrompt -> {
          Text(
            text = stringResource(R.string.hybrid_reader_download_mobile_prompt),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
          )
          Text(
            text = stringResource(R.string.hybrid_reader_download_allow),
            color = quranColors.onCta,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
              .clip(RoundedCornerShape(999.dp))
              .background(quranColors.ctaBackground)
              .clickable(role = Role.Button, onClick = onAllowMobileDownload)
              .padding(horizontal = 10.dp, vertical = 5.dp)
          )
          Text(
            text = stringResource(R.string.hybrid_reader_download_cancel),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
              .clip(RoundedCornerShape(999.dp))
              .clickable(role = Role.Button, onClick = onCancelDownload)
              .padding(horizontal = 8.dp, vertical = 5.dp)
          )
        }
        downloadProgress != null -> {
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(accent.copy(alpha = if (isDark) 0.16f else 0.12f))
              .semantics(mergeDescendants = true) {}
              .clickable(role = Role.Button, onClick = onCancelDownload),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = ReaderIcons.Close,
              contentDescription = stringResource(R.string.hybrid_reader_download_cancel),
              tint = accent,
              modifier = Modifier.size(16.dp)
            )
          }
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = stringResource(R.string.hybrid_reader_downloading),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              if (downloadProgress >= 0) {
                Text(
                  text = "${QuranUtils.getLocalizedNumber(downloadProgress)}%",
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  fontSize = 10.5.sp,
                  fontWeight = FontWeight.SemiBold
                )
              }
            }
            SlimProgressTrack(
              progress = if (downloadProgress >= 0) (downloadProgress / 100f).coerceIn(0f, 1f) else 0.3f,
              trackHeight = 3.5.dp,
              thumbDiameter = 0.dp,
              trackColor = accent.copy(alpha = if (isDark) 0.20f else 0.18f),
              fillColor = accent
            )
          }
          ExpandDrawerButton(
            onClick = onExpandStudyDrawer
          )
        }
        else -> {
          // 36dp Circular Play/Pause CTA with Color-1-tinted Z2 shadow
          Box(
            modifier = Modifier
              .size(36.dp)
              .shadow(
                elevation = if (isDark) 7.dp else 6.dp,
                shape = CircleShape,
                ambientColor = quranColors.ctaBackground.copy(alpha = if (isDark) 0.22f else 0.10f),
                spotColor = quranColors.ctaBackground.copy(alpha = if (isDark) 0.35f else 0.18f)
              )
              .clip(CircleShape)
              .background(quranColors.ctaBackground)
              .semantics(mergeDescendants = true) {}
              .clickable(role = Role.Button, onClick = onPlayPauseClick),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (isPlaying) ReaderIcons.Pause else ReaderIcons.Play,
              contentDescription = stringResource(
                if (isPlaying) R.string.hybrid_reader_pause_cd else R.string.hybrid_reader_play_cd
              ),
              tint = quranColors.onCta,
              modifier = Modifier.size(15.dp)
            )
          }

          // Center meta + 3.5dp slim scrubber
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = qariName,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
              )
              Spacer(modifier = Modifier.width(8.dp))
              val timeStr = if (isPlaying || isPaused) {
                ReaderFormatters.formatDuration(elapsedMs)
              } else if (isPreparing) {
                stringResource(UiCoreR.string.loading)
              } else {
                ReaderFormatters.formatDuration(0L)
              }
              Text(
                text = stringResource(
                  R.string.hybrid_reader_capsule_meta_format,
                  timeStr,
                  suraName,
                  QuranUtils.getLocalizedNumber(activeAyah.ayah)
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
              )
            }
            SlimProgressTrack(
              progress = progressFraction,
              trackHeight = 3.5.dp,
              thumbDiameter = 9.dp,
              trackColor = accent.copy(alpha = if (isDark) 0.20f else 0.18f),
              fillColor = accent
            )
          }

          ExpandDrawerButton(
            onClick = onExpandStudyDrawer
          )
        }
      }
    }
  }
}

@Composable
private fun ExpandDrawerButton(
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .size(30.dp)
      .clip(CircleShape)
      .background(MaterialTheme.colorScheme.surfaceVariant)
      .semantics(mergeDescendants = true) {}
      .clickable(role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      imageVector = ReaderIcons.ChevronUp,
      contentDescription = stringResource(R.string.hybrid_reader_expand_drawer_cd),
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(15.dp)
    )
  }
}

@Composable
internal fun SlimProgressTrack(
  progress: Float,
  trackHeight: Dp,
  thumbDiameter: Dp,
  trackColor: androidx.compose.ui.graphics.Color,
  fillColor: androidx.compose.ui.graphics.Color,
  modifier: Modifier = Modifier
) {
  val clamped = progress.coerceIn(0f, 1f)
  val density = LocalDensity.current
  BoxWithConstraints(
    modifier = modifier
      .fillMaxWidth()
      .height(if (thumbDiameter > trackHeight) thumbDiameter else trackHeight),
    contentAlignment = Alignment.CenterStart
  ) {
    val totalWidthPx = with(density) { maxWidth.toPx() }
    val thumbRadiusPx = with(density) { thumbDiameter.toPx() * 0.5f }
    val trackShape = RoundedCornerShape(999.dp)

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(trackHeight)
        .clip(trackShape)
        .background(trackColor)
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth(clamped)
          .fillMaxHeight()
          .clip(trackShape)
          .background(fillColor)
      )
    }

    if (thumbDiameter > 0.dp) {
      val thumbCenterOffsetDp = with(density) {
        ((totalWidthPx * clamped) - thumbRadiusPx).coerceIn(0f, (totalWidthPx - 2f * thumbRadiusPx).coerceAtLeast(0f)).toDp()
      }
      Box(
        modifier = Modifier
          .offset(x = thumbCenterOffsetDp)
          .size(thumbDiameter)
          .clip(CircleShape)
          .background(fillColor)
      )
    }
  }
}
