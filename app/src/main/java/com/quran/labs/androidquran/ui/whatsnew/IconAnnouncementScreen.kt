package com.quran.labs.androidquran.ui.whatsnew

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranTheme

@Composable
internal fun IconAnnouncementScreen(onContinue: () -> Unit) {
  val accent = colorResource(R.color.icon_announcement_accent)
  Surface(color = MaterialTheme.colorScheme.background) {
    Column(
      modifier = Modifier.fillMaxSize().safeDrawingPadding(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(
        text = stringResource(R.string.whats_new_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
      )
      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 24.dp)
          .widthIn(max = 480.dp)
          .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp)
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          Text(
            text = stringResource(R.string.icon_announcement_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
          )
          Text(
            text = stringResource(R.string.icon_announcement_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 400.dp)
          )
        }
        IconComparison(accent)
        Surface(
          shape = RoundedCornerShape(24.dp),
          color = colorResource(R.color.icon_announcement_surface)
        ) {
          Column(Modifier.padding(16.dp)) {
            AnnouncementDetail(
              icon = R.drawable.ic_announcement_star,
              title = stringResource(R.string.icon_announcement_same_app_title),
              body = stringResource(R.string.icon_announcement_same_app_body)
            )
            HorizontalDivider(
              modifier = Modifier.padding(start = 56.dp),
              color = MaterialTheme.colorScheme.outlineVariant
            )
            AnnouncementDetail(
              icon = R.drawable.ic_announcement_schedule,
              title = stringResource(R.string.icon_announcement_release_title),
              body = stringResource(R.string.icon_announcement_release_body)
            )
          }
        }
      }
      Button(
        onClick = onContinue,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = accent,
          contentColor = colorResource(R.color.icon_announcement_on_accent)
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        modifier = Modifier
          .padding(horizontal = 24.dp, vertical = 16.dp)
          .widthIn(max = 480.dp)
          .fillMaxWidth()
      ) {
        Text(
          text = stringResource(R.string.whats_new_continue),
          style = MaterialTheme.typography.titleMedium
        )
      }
    }
  }
}

@Composable
private fun IconComparison(accent: Color) {
  BoxWithConstraints(Modifier.widthIn(max = 320.dp).fillMaxWidth()) {
    val iconSize = minOf(104.dp, (maxWidth - 64.dp) / 2)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      AnnouncementIcon(
        image = R.drawable.ic_previous_app_icon,
        description = stringResource(R.string.icon_announcement_current_icon),
        label = stringResource(R.string.icon_announcement_now),
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        iconSize = iconSize,
        modifier = Modifier.weight(1f),
        // Match the visible tile: the legacy PNG has a 152px icon in a 192px canvas.
        artworkScale = 192f / 152f
      )
      Box(
        modifier = Modifier
          .padding(top = (iconSize - 40.dp) / 2)
          .size(40.dp)
          .background(colorResource(R.color.icon_announcement_surface), CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          painter = painterResource(R.drawable.ic_announcement_forward),
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(24.dp)
        )
      }
      AnnouncementIcon(
        image = R.drawable.ic_upcoming_app_icon,
        description = stringResource(R.string.icon_announcement_upcoming_icon),
        label = stringResource(R.string.icon_announcement_coming_soon),
        labelColor = accent,
        iconSize = iconSize,
        modifier = Modifier.weight(1f)
      )
    }
  }
}

@Composable
private fun AnnouncementIcon(
  @DrawableRes image: Int,
  description: String,
  label: String,
  labelColor: Color,
  iconSize: Dp,
  modifier: Modifier = Modifier,
  artworkScale: Float = 1f
) {
  Column(
    modifier = modifier.semantics(mergeDescendants = true) {},
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Image(
      painter = painterResource(image),
      contentDescription = description,
      modifier = Modifier.size(iconSize).scale(artworkScale)
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Bold,
      color = labelColor,
      textAlign = TextAlign.Center
    )
  }
}

@Composable
private fun AnnouncementDetail(@DrawableRes icon: Int, title: String, body: String) {
  Row(
    modifier = Modifier.padding(vertical = 12.dp).semantics(mergeDescendants = true) {},
    horizontalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    Box(
      modifier = Modifier
        .size(42.dp)
        .background(colorResource(R.color.icon_announcement_accent), RoundedCornerShape(10.dp)),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = colorResource(R.color.icon_announcement_on_accent),
        modifier = Modifier.size(24.dp)
      )
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
      Text(text = title, style = MaterialTheme.typography.titleMedium)
      Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 390, heightDp = 844)
@Preview(name = "Arabic", locale = "ar", widthDp = 390, heightDp = 844)
@Preview(name = "Large text", fontScale = 2f, widthDp = 320, heightDp = 640)
@Preview(name = "Landscape", widthDp = 840, heightDp = 400)
@Composable
private fun IconAnnouncementPreview() {
  QuranTheme {
    IconAnnouncementScreen(onContinue = {})
  }
}
