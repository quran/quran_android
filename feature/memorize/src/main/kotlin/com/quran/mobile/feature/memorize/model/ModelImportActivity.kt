package com.quran.mobile.feature.memorize.model

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.memorize.R
import com.quran.mobile.feature.memorize.di.MemorizeComponentInterface
import kotlinx.coroutines.launch

/**
 * Installs the speech model from files the user picks (e.g. copied from a computer), so it can be
 * set up without any internet connection. It has no screen of its own: just the system file picker.
 */
class ModelImportActivity : ComponentActivity() {

  private val models: SpeechModelStore by lazy {
    ((application as QuranApplicationComponentProvider).provideQuranApplicationComponent() as MemorizeComponentInterface)
      .speechModelStore()
  }

  private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
    if (uris.isEmpty()) finish() else import(uris)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (savedInstanceState == null) picker.launch(arrayOf("*/*"))
  }

  private fun import(uris: List<Uri>) {
    lifecycleScope.launch {
      val installed = models.import(uris)
      Toast.makeText(
        this@ModelImportActivity,
        if (installed) R.string.memorize_model_imported else R.string.memorize_model_import_failed,
        Toast.LENGTH_LONG
      ).show()
      finish()
    }
  }
}
