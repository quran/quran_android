package com.quran.mobile.feature.audiobar

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.dimensionResource
import app.cash.molecule.AndroidUiDispatcher
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.audiobar.presenter.AudioBarPresenter
import com.quran.mobile.feature.audiobar.ui.AudioBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import javax.inject.Inject

class AudioBarWrapper @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

  private val scope = CoroutineScope(SupervisorJob() + AndroidUiDispatcher.Main)

  @Inject
  lateinit var audioBarPresenter: AudioBarPresenter

  init {
    (context as? AudioBarInjector)?.inject(this)
  }

  @Composable
  override fun Content() {
    QuranTheme {
      val eventListeners = audioBarPresenter.eventListeners()
      val flow = scope.launchMolecule(mode = RecompositionMode.ContextClock) {
        audioBarPresenter.audioBarPresenter()
      }

      Column {
        AudioBar(
          flow,
          eventListeners,
          modifier = Modifier
            .height(dimensionResource(id = R.dimen.audiobar_height))
            .padding(
              WindowInsets.navigationBars.add(WindowInsets.displayCutout)
                .only(WindowInsetsSides.Horizontal)
                .asPaddingValues()
            )
        )
        Spacer(
          modifier = Modifier
            .fillMaxWidth()
            .background(color = Color(0xaa000000))
            .windowInsetsBottomHeight(WindowInsets.navigationBars)
        )
      }
    }
  }

  override fun onDetachedFromWindow() {
    scope.cancel()
    super.onDetachedFromWindow()
  }
}
