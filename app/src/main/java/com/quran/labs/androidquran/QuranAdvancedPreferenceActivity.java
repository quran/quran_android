package com.quran.labs.androidquran;

import android.Manifest;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.widget.Toast;

import com.quran.labs.androidquran.service.util.PermissionUtil;
import com.quran.labs.androidquran.ui.QuranActionBarActivity;
import com.quran.labs.androidquran.ui.fragment.QuranAdvancedSettingsFragment;
import com.quran.labs.androidquran.util.AudioManagerUtils;
import com.quran.labs.androidquran.util.QuranSettings;

public class QuranAdvancedPreferenceActivity extends QuranActionBarActivity {

  private static final String SI_LOCATION_TO_WRITE = "SI_LOCATION_TO_WRITE";
  private static final int REQUEST_WRITE_TO_SDCARD_PERMISSION = 1;

  private String mLocationToWrite;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    ((QuranApplication) getApplication()).refreshLocale(this, false);
    super.onCreate(savedInstanceState);
    setContentView(R.layout.preferences);

    final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    toolbar.setTitle(R.string.prefs_category_advanced);
    setSupportActionBar(toolbar);
    final ActionBar ab = getSupportActionBar();
    if (ab != null) {
      ab.setDisplayHomeAsUpEnabled(true);
    }

    AudioManagerUtils.clearCache();

    if (savedInstanceState != null) {
      mLocationToWrite = savedInstanceState.getString(SI_LOCATION_TO_WRITE);
    }

    final FragmentManager fm = getFragmentManager();
    final Fragment fragment = fm.findFragmentById(R.id.content);
    if (fragment == null) {
      fm.beginTransaction()
          .replace(R.id.content, new QuranAdvancedSettingsFragment())
          .commit();
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    if (mLocationToWrite != null) {
      outState.putString(SI_LOCATION_TO_WRITE, mLocationToWrite);
    }
    super.onSaveInstanceState(outState);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      finish();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  public void requestWriteExternalSdcardPermission(String newLocation) {
    if (PermissionUtil.canRequestWriteExternalStoragePermission(this)) {
      QuranSettings.getInstance(this).setSdcardPermissionsDialogPresented();

      mLocationToWrite = newLocation;
      ActivityCompat.requestPermissions(this,
          new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE },
          REQUEST_WRITE_TO_SDCARD_PERMISSION);
    } else {
      // in the future, we should make this a direct link - perhaps using a Snackbar.
      Toast.makeText(this, R.string.please_grant_permissions, Toast.LENGTH_SHORT).show();
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
      @NonNull int[] grantResults) {
    if (requestCode == REQUEST_WRITE_TO_SDCARD_PERMISSION) {
      if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        if (mLocationToWrite != null) {
          Fragment fragment = getFragmentManager().findFragmentById(R.id.content);
          if (fragment instanceof QuranAdvancedSettingsFragment) {
            ((QuranAdvancedSettingsFragment) fragment).moveFiles(mLocationToWrite);
          }

        }
      }
      mLocationToWrite = null;
    }
  }
}
