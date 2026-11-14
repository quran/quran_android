package com.quran.mobile.feature.memorize.model

import android.app.AlertDialog
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
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
 * set up without any internet connection. A checklist of the model's files, each picked on its own
 * with the system file picker, or all at once from the .zip they came in.
 */
class ModelImportActivity : ComponentActivity() {

  private val models: SpeechModelStore by lazy {
    ((application as QuranApplicationComponentProvider).provideQuranApplicationComponent() as MemorizeComponentInterface)
      .speechModelStore()
  }

  private var dialog: AlertDialog? = null
  // the file the picker was opened for, or null for the .zip
  private var expected: ModelFile? = null

  private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri == null) showChecklist() else import(uri)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    showChecklist()
  }

  override fun onDestroy() {
    dialog?.dismiss()
    super.onDestroy()
  }

  private fun showChecklist() {
    val missing = models.missingFiles()
    if (missing.isEmpty()) {
      Toast.makeText(this, R.string.memorize_model_imported, Toast.LENGTH_LONG).show()
      finish()
      return
    }
    val files = models.spec.files
    val items = files.map { file ->
      if (file in missing) {
        getString(R.string.memorize_import_choose_file, file.name, Formatter.formatShortFileSize(this, file.size))
      } else {
        getString(R.string.memorize_import_file_done, file.name)
      }
    } + getString(R.string.memorize_import_choose_zip)
    dialog = AlertDialog.Builder(this, dialogTheme())
      .setTitle(R.string.memorize_import_title)
      .setItems(items.toTypedArray()) { _, which ->
        val file = files.getOrNull(which)
        if (file != null && file !in missing) {
          showChecklist()
        } else {
          expected = file
          picker.launch(arrayOf("*/*"))
        }
      }
      .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
      .setOnCancelListener { finish() }
      .show()
  }

  private fun import(uri: Uri) {
    val expected = expected
    lifecycleScope.launch {
      val installed = models.importFile(uri)
      val message = when {
        installed.isNotEmpty() -> getString(R.string.memorize_import_installed, installed.joinToString { it.name })
        expected != null -> getString(R.string.memorize_import_wrong_file, expected.name)
        else -> getString(R.string.memorize_import_wrong_zip)
      }
      Toast.makeText(this@ModelImportActivity, message, Toast.LENGTH_LONG).show()
      showChecklist()
    }
  }

  private fun dialogTheme(): Int {
    val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    return if (night) android.R.style.Theme_DeviceDefault_Dialog_Alert else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
  }
}
