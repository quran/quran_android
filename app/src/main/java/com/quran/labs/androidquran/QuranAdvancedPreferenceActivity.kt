package com.quran.labs.androidquran

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import com.quran.labs.androidquran.service.util.PermissionUtil
import com.quran.labs.androidquran.ui.fragment.QuranAdvancedSettingsFragment
import com.quran.labs.androidquran.ui.util.ToastCompat
import com.quran.labs.androidquran.util.QuranSettings

class QuranAdvancedPreferenceActivity : AppCompatActivity() {

  companion object {
    private const val SI_LOCATION_TO_WRITE = "SI_LOCATION_TO_WRITE"
    private const val REQUEST_WRITE_TO_SDCARD_PERMISSION = 1
  }

  private var locationToWrite: String? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    (application as QuranApplication).refreshLocale(this, false)
    super.onCreate(savedInstanceState)
    setContentView(R.layout.preferences)

    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    toolbar.setTitle(R.string.prefs_category_advanced)
    setSupportActionBar(toolbar)
    val ab = supportActionBar
    ab?.setDisplayHomeAsUpEnabled(true)

    if (savedInstanceState != null) {
      locationToWrite = savedInstanceState.getString(SI_LOCATION_TO_WRITE)
    }

    val fm = supportFragmentManager
    val fragment = fm.findFragmentById(R.id.content)
    if (fragment == null) {
      fm.beginTransaction()
        .replace(R.id.content, QuranAdvancedSettingsFragment())
        .commit()
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    if (locationToWrite != null) {
      outState.putString(SI_LOCATION_TO_WRITE, locationToWrite)
    }
    super.onSaveInstanceState(outState)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == android.R.id.home) {
      finish()
      return true
    }
    return super.onOptionsItemSelected(item)
  }

  fun requestWriteExternalSdcardPermission(newLocation: String) {
    if (PermissionUtil.canRequestWriteExternalStoragePermission(this)) {
      QuranSettings.getInstance(this).setSdcardPermissionsDialogPresented()
      locationToWrite = newLocation
      ActivityCompat.requestPermissions(
        this,
        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
        REQUEST_WRITE_TO_SDCARD_PERMISSION
      )
    } else {
      // in the future, we should make this a direct link - perhaps using a Snackbar.
      ToastCompat.makeText(this, R.string.please_grant_permissions, Toast.LENGTH_SHORT).show()
    }
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
  ) {
    if (requestCode == REQUEST_WRITE_TO_SDCARD_PERMISSION) {
      if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED && locationToWrite != null) {
        val fragment = supportFragmentManager.findFragmentById(R.id.content)
        if (fragment is QuranAdvancedSettingsFragment) {
          val location = locationToWrite
          if (location != null) {
            fragment.moveFiles(location)
          }
        }
      }
      locationToWrite = null
    }
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
  }

}
