package com.quran.mobile.feature.voicesearch

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.voicesearch.di.VoiceSearchComponentInterface
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.launch

class VoiceSearchActivity : ComponentActivity() {

  @Inject
  lateinit var voiceSearchPresenter: VoiceSearchPresenter

  private val requestPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      voiceSearchPresenter.onEvent(VoiceSearchEvent.StartRecording)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val injector = (application as? QuranApplicationComponentProvider)
      ?.provideQuranApplicationComponent() as? VoiceSearchComponentInterface
    injector?.voiceSearchComponentFactory()?.generate()?.inject(this)

    voiceSearchPresenter.initialize()

    lifecycleScope.launch {
      voiceSearchPresenter.navigationEvents.collect { result ->
        val intent = when (result) {
          is NavigationResult.VerseSelected -> Intent().apply {
            putExtra(EXTRA_SURA, result.sura)
            putExtra(EXTRA_AYAH, result.ayah)
          }
          is NavigationResult.TextSearch -> Intent().apply {
            putExtra(EXTRA_TRANSCRIBED_TEXT, result.text)
          }
        }
        setResult(Activity.RESULT_OK, intent)
        finish()
      }
    }

    enableEdgeToEdge()

    setContent {
      QuranTheme {
        val state by voiceSearchPresenter.state.collectAsState()

        VoiceSearchScreen(
          state = state,
          onEvent = { event ->
            if (event is VoiceSearchEvent.StartRecording && !hasRecordAudioPermission()) {
              requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
              voiceSearchPresenter.onEvent(event)
            }
          },
          onBackPressed = { finish() }
        )
      }
    }
  }

  override fun onDestroy() {
    if (::voiceSearchPresenter.isInitialized) {
      voiceSearchPresenter.release()
    }
    super.onDestroy()
  }

  private fun hasRecordAudioPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
      this, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
  }

  companion object {
    const val EXTRA_TRANSCRIBED_TEXT = "extra_transcribed_text"
    const val EXTRA_SURA = "extra_sura"
    const val EXTRA_AYAH = "extra_ayah"
  }
}
