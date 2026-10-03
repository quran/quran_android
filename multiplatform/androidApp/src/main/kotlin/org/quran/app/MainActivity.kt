package org.quran.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import org.quran.app.data.*
import org.quran.app.domain.AudioPlayer

class MainActivity : ComponentActivity() {
  private var imported: ((String) -> Unit)? = null
  private lateinit var audioPlayer: AudioPlayer
  private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    uri?.let {
      imported?.invoke(it.toString())
    }; imported = null
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState); initializePlatform(this); audioPlayer =
      platformAudioPlayer();
    val store = platformSettingsStore();
    setContent {
      QuranApp(store, audioPlayer) {
        callback ->
        imported = callback;
        picker.launch("audio/*")
      }
    }
  }

  override fun onDestroy() {
    audioPlayer.release(); super.onDestroy()
  }
}
