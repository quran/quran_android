package com.quran.mobile.feature.sync

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.quran.labs.androidquran.common.ui.core.LocalQuranColors
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.common.ui.core.modifier.autoMirror
import com.quran.mobile.feature.sync.state.AccountProfile
import com.quran.mobile.feature.sync.state.SyncActivityState
import com.quran.mobile.feature.sync.state.SyncActivityState.Status
import com.quran.mobile.feature.sync.state.accountProfile
import com.quran.shared.auth.model.UserInfo
import com.quran.shared.pipeline.HeldReason
import com.quran.shared.pipeline.HeldSyncItem
import com.quran.shared.pipeline.SyncErrorKind
import com.quran.shared.pipeline.SyncResourceType
import kotlinx.collections.immutable.persistentListOf
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Composable
internal fun SyncAccountCard(
  profile: AccountProfile,
  summary: SyncStatusSummary,
  onOpenAccount: () -> Unit,
  onSyncNow: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.secondaryContainer,
    modifier = modifier.fillMaxWidth()
  ) {
    Column {
      ProfileRow(profile = profile, onClick = onOpenAccount)
      HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
      Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp)
      ) {
        StatusRow(summary = summary, onSyncNow = onSyncNow)
        if (summary.hasHeldItems) {
          ReviewHeldItemsRow(onClick = onOpenAccount)
        }
      }
    }
  }
}

@Composable
private fun ProfileRow(profile: AccountProfile, onClick: () -> Unit) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(
        onClickLabel = stringResource(R.string.quran_sync_account_title),
        role = Role.Button,
        onClick = onClick
      )
      .padding(16.dp)
  ) {
    AccountAvatar(profile = profile, size = 56.dp)
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = profile.name ?: profile.email ?: stringResource(R.string.quran_sync_signed_in),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSecondaryContainer
      )
      if (profile.name != null && profile.email != null) {
        Text(
          text = profile.email,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
    Icon(
      imageVector = QuranIcons.ChevronRight,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.autoMirror()
    )
  }
}

@Composable
private fun AccountAvatar(profile: AccountProfile, size: Dp) {
  val accent = MaterialTheme.colorScheme.primary
  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier
      .size(size)
      .clip(CircleShape)
      .background(accent.copy(alpha = 0.1f))
      .clearAndSetSemantics {}
  ) {
    if (profile.initials.isEmpty()) {
      Icon(
        imageVector = QuranIcons.Person,
        contentDescription = null,
        tint = accent,
        modifier = Modifier.size(size * 0.45f)
      )
    } else {
      Text(
        text = profile.initials,
        color = accent,
        fontWeight = FontWeight.SemiBold,
        // sized to the circle, so it doesn't follow the font scale
        fontSize = with(LocalDensity.current) { (size * 0.38f).toSp() }
      )
    }
    // drawn over the initials, which show while it loads or when it fails
    if (profile.photoUrl != null) {
      AsyncImage(
        model = profile.photoUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(size)
      )
    }
  }
}

@Composable
private fun StatusRow(summary: SyncStatusSummary, onSyncNow: () -> Unit) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    if (summary.icon != null) {
      SyncStatusIcon(icon = summary.icon, modifier = Modifier.size(22.dp))
    }
    Column(
      modifier = Modifier
        .weight(1f)
        .semantics(mergeDescendants = true) {}
    ) {
      Text(
        text = summary.title,
        style = MaterialTheme.typography.bodyLarge,
        color = when (summary.tone) {
          SyncStatusSummary.Tone.NORMAL -> MaterialTheme.colorScheme.onSecondaryContainer
          SyncStatusSummary.Tone.FAILURE -> MaterialTheme.colorScheme.error
          SyncStatusSummary.Tone.CAUTION -> LocalQuranColors.current.caution
        }
      )
      Text(
        text = summary.detail,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    SyncPillButton(button = summary.button, onClick = onSyncNow)
  }
}

@Composable
private fun SyncStatusIcon(icon: SyncStatusSummary.Icon, modifier: Modifier = Modifier) {
  Box(contentAlignment = Alignment.Center, modifier = modifier.clearAndSetSemantics {}) {
    when (icon) {
      SyncStatusSummary.Icon.SYNCED -> StatusIcon(QuranIcons.CheckCircle, LocalQuranColors.current.success)
      SyncStatusSummary.Icon.FAILED -> StatusIcon(QuranIcons.ErrorOutline, MaterialTheme.colorScheme.error)
      SyncStatusSummary.Icon.HELD -> StatusIcon(QuranIcons.Warning, LocalQuranColors.current.caution)
      SyncStatusSummary.Icon.SYNCING -> CircularProgressIndicator(
        strokeWidth = 2.dp,
        modifier = Modifier.size(18.dp)
      )
    }
  }
}

@Composable
private fun StatusIcon(imageVector: ImageVector, tint: Color) {
  Icon(imageVector = imageVector, contentDescription = null, tint = tint)
}

@Composable
private fun SyncPillButton(button: SyncStatusSummary.Button, onClick: () -> Unit) {
  val accent = MaterialTheme.colorScheme.primary
  Surface(
    onClick = onClick,
    enabled = button.isEnabled,
    shape = CircleShape,
    color = accent.copy(alpha = 0.1f),
    modifier = Modifier.alpha(if (button.isEnabled) 1f else 0.45f)
  ) {
    Text(
      text = stringResource(button.title),
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
      color = accent,
      maxLines = 1,
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
    )
  }
}

@Composable
private fun ReviewHeldItemsRow(onClick: () -> Unit) {
  val caution = LocalQuranColors.current.caution
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(14.dp),
    color = LocalQuranColors.current.cautionBackground,
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)
    ) {
      Text(
        text = stringResource(R.string.quran_sync_held_review),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = caution,
        modifier = Modifier.weight(1f)
      )
      Icon(
        imageVector = QuranIcons.ChevronRight,
        contentDescription = null,
        tint = caution,
        modifier = Modifier
          .autoMirror()
          .size(20.dp)
      )
    }
  }
}

private val previewNow = Clock.System.now()

private val previewHeldItem = HeldSyncItem(
  type = SyncResourceType.READING_BOOKMARK,
  id = "1",
  collectionId = null,
  reason = HeldReason.TOO_LONG,
  waitingOn = null,
  httpStatus = null,
  serverCode = null
)

private fun previewActivity(status: Status, held: Boolean = false) = SyncActivityState(
  status = status,
  lastSynced = (status as? Status.Synced)?.at,
  heldItems = if (held) persistentListOf(previewHeldItem) else persistentListOf()
)

@Composable
private fun PreviewCards(vararg activities: SyncActivityState) {
  val profile = accountProfile(
    UserInfo(id = "1", name = "Aisha Rahman", email = "aisha.rahman@example.com")
  )
  QuranTheme {
    Surface {
      Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(16.dp)
      ) {
        activities.forEach { activity ->
          SyncAccountCard(
            profile = profile,
            summary = cardSyncStatusSummary(activity, previewNow),
            onOpenAccount = {},
            onSyncNow = {}
          )
        }
      }
    }
  }
}

@Preview(heightDp = 900)
@Preview("dark theme", heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SyncAccountCardPreview() {
  PreviewCards(
    previewActivity(Status.Synced(previewNow - 2.minutes)),
    previewActivity(Status.Syncing),
    previewActivity(Status.Retrying(SyncErrorKind.NETWORK)),
    previewActivity(Status.Synced(previewNow), held = true)
  )
}

@Preview(heightDp = 700)
@Preview("dark theme", heightDp = 700, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SyncAccountCardFirstSyncPreview() {
  PreviewCards(
    previewActivity(Status.NeverSynced),
    previewActivity(Status.NeverSynced, held = true),
    previewActivity(Status.Failed(null))
  )
}
