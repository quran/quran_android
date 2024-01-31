package com.quran.labs.androidquran.bridge

import androidx.compose.ui.platform.ComposeView
import com.quran.data.di.QuranScope
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.audiobar.state.AudioBarScreen
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.CircuitContent
import javax.inject.Inject

@QuranScope
class AudioBarComposeBridge @Inject constructor(private val circuit: Circuit) {

  fun initializeAudioBar(composeView: ComposeView) {
    composeView.apply {
      setContent {
        QuranTheme {
          CircuitCompositionLocals(circuit) {
            CircuitContent(AudioBarScreen)
          }
        }
      }
    }
  }
}
