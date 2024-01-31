package com.quran.mobile.feature.audiobar

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.AbstractComposeView
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
  @Inject lateinit var audioBarPresenter: AudioBarPresenter

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

      AudioBar(flow, eventListeners)
    }
  }

  override fun onDetachedFromWindow() {
    scope.cancel()
    super.onDetachedFromWindow()
  }
}
