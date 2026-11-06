package com.quran.mobile.feature.audiobar

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import com.quran.labs.androidquran.common.ui.core.LocalQuranColors
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.audiobar.presenter.AudioBarPresenter
import com.quran.mobile.feature.audiobar.ui.AudioBar
import dev.zacsweers.metro.Inject

class AudioBarWrapper @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

  @Inject
  lateinit var audioBarPresenter: AudioBarPresenter

  var onFatihaPromptClick: (() -> Unit)? = null
  var onFatihaPromptClose: (() -> Unit)? = null
  var onFatihaPromptHeightChanged: ((Int) -> Unit)? = null

  var fatihaPrompt by mutableStateOf<FatihaPrompt?>(null)
  var fatihaPromptHeight by mutableIntStateOf(0)
    private set
  var fatihaBottomPaddingPx by mutableIntStateOf(0)

  init {
    (context as? AudioBarInjector)?.inject(this)
  }

  private fun updatePromptHeight(height: Int) {
    if (fatihaPromptHeight == height) return
    fatihaPromptHeight = height
    onFatihaPromptHeightChanged?.invoke(height)
  }

  @Composable
  override fun Content() {
    QuranTheme {
      val scope = rememberCoroutineScope()
      val flow = remember {
        scope.launchMolecule(mode = RecompositionMode.ContextClock) {
          audioBarPresenter.audioBarPresenter()
        }
      }
      val eventListeners = remember(audioBarPresenter) { audioBarPresenter.eventListeners() }
      val prompt = fatihaPrompt
      if (prompt == null) {
        SideEffect { updatePromptHeight(0) }
      }

      Card(
        // The prompt, when present, fills this card's rounded top, so the audio
        // section under it has a square top and the two meet flush. With the
        // prompt gone, that rounded top belongs to the audio bar again.
        shape = CardDefaults.shape.topOnly(),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column {
          if (prompt != null) {
            FatihaPromptRow(
              prompt = prompt,
              bottomPaddingPx = fatihaBottomPaddingPx,
              onClick = { onFatihaPromptClick?.invoke() },
              onClose = { onFatihaPromptClose?.invoke() },
              onHeightChanged = ::updatePromptHeight,
            )
          }
          AudioBar(
            flow,
            eventListeners,
            modifier = Modifier
              .padding(
                WindowInsets.navigationBars.add(WindowInsets.displayCutout)
                  .only(WindowInsetsSides.Horizontal.plus(WindowInsetsSides.Bottom))
                  .asPaddingValues()
              )
              .padding(top = if (prompt == null) 8.dp else 0.dp)
              .padding(horizontal = 16.dp)
              .heightIn(min = dimensionResource(id = R.dimen.audiobar_height))
          )
        }
      }
    }
  }

  data class FatihaPrompt(
    val text: String,
    val showClose: Boolean,
    val closeDescription: String
  )

  private fun Shape.topOnly(): Shape {
    return if (this is RoundedCornerShape) {
      this.copy(
        bottomStart = CornerSize(0.dp),
        bottomEnd = CornerSize(0.dp)
      )
    } else {
      this
    }
  }
}

@Composable
private fun FatihaPromptRow(
  prompt: AudioBarWrapper.FatihaPrompt,
  bottomPaddingPx: Int,
  onClick: () -> Unit,
  onClose: () -> Unit,
  onHeightChanged: (Int) -> Unit
) {
  val textColor = LocalQuranColors.current.defaultTextColor
  val bottomPadding = with(LocalDensity.current) { bottomPaddingPx.toDp() }
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp)
        // Text row only. The spacer below is the system inset and is not part of this height.
        .onSizeChanged { onHeightChanged(it.height) }
        .padding(start = 16.dp, end = 4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = prompt.text,
        modifier = Modifier.weight(1f),
        color = textColor,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
      if (prompt.showClose) {
        IconButton(onClick = onClose) {
          Icon(
            QuranIcons.Close,
            contentDescription = prompt.closeDescription,
            tint = textColor
          )
        }
      }
    }
    if (bottomPadding > 0.dp) {
      Spacer(Modifier.height(bottomPadding))
    }
  }
}
