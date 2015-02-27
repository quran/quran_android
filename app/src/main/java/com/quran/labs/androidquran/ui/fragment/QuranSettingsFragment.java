package com.quran.labs.androidquran.ui.fragment;

import com.quran.labs.androidquran.QuranPreferenceActivity;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.StorageUtils;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class QuranSettingsFragment extends PreferenceFragment implements
    SharedPreferences.OnSharedPreferenceChangeListener {
  private static final String TAG = QuranSettingsFragment.class.getSimpleName();

  private ListPreference mListStoragePref;
  private MoveFilesAsyncTask mMoveFilesTask;
  private List<StorageUtils.Storage> mStorageList;
  private LoadStorageOptionsTask mLoadStorageOptionsTask;
  private int mAppSize;
  private boolean mIsPaused;
  private String mInternalSdcardLocation;
  private AlertDialog mDialog;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    addPreferencesFromResource(R.xml.quran_preferences);

    final Context context = getActivity();
    // remove the tablet mode preference if it doesn't exist
    if (!QuranScreenInfo.getOrMakeInstance(context).isTablet(context)) {
      Preference tabletModePreference =
          findPreference(Constants.PREF_TABLET_ENABLED);
      PreferenceCategory category =
          (PreferenceCategory) findPreference(
              Constants.PREF_DISPLAY_CATEGORY);
      category.removePreference(tabletModePreference);
    }

    mInternalSdcardLocation =
        Environment.getExternalStorageDirectory().getAbsolutePath();

    mListStoragePref = (ListPreference) findPreference(
        getString(R.string.prefs_app_location));
    mListStoragePref.setEnabled(false);

    final File[] mountPoints = ContextCompat.getExternalFilesDirs(context, null);
    if (mountPoints.length > 1) {
      mStorageList = new ArrayList<>();
      for (int i = 0; i < mountPoints.length; i++) {
        final StorageUtils.Storage s;
        if (i == 0) {
          s = new StorageUtils.Storage(
              getString(R.string.prefs_sdcard_internal),
              mInternalSdcardLocation);
        } else if (mountPoints[i] != null) {
          s = new StorageUtils.Storage(
              getString(R.string.prefs_sdcard_external),
              mountPoints[i].getAbsolutePath());
        } else {
          s = null;
        }

        if (s != null) {
          mStorageList.add(s);
        }
      }
    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
      try {
        mStorageList = StorageUtils
            .getAllStorageLocations(context.getApplicationContext());
      } catch (Exception e) {
        mStorageList = new ArrayList<>();
      }
    }

    // Hide app location pref if there is no storage option
    // except for the normal Environment.getExternalStorageDirectory
    if (mStorageList == null || mStorageList.size() <= 1) {
      Log.d(TAG, "removing advanced settings from preferences");
      hideStorageListPref();
    } else {
      mLoadStorageOptionsTask = new LoadStorageOptionsTask(context);
      mLoadStorageOptionsTask.execute();
    }
  }

  @Override
  public void onDestroy() {
    if (mDialog != null) {
      mDialog.dismiss();
    }
    super.onDestroy();
  }

  private void hideStorageListPref() {
    PreferenceCategory category =
        (PreferenceCategory) findPreference(
            Constants.PREF_DOWNLOAD_CATEGORY);
    category.removePreference(mListStoragePref);
  }

  private void loadStorageOptions(Context context) {
    try {
      CharSequence[] values = new CharSequence[mStorageList.size()];
      CharSequence[] displayNames = new CharSequence[mStorageList.size()];
      int i = 0;
      final HashMap<String, Integer> storageEmptySpaceMap =
          new HashMap<>(mStorageList.size());
      for (StorageUtils.Storage storage : mStorageList) {
        values[i] = storage.getMountPoint();
        displayNames[i] = storage.getLabel() + " " +
            storage.getFreeSpace()
            + " " + getString(R.string.prefs_megabytes);
        i++;
        storageEmptySpaceMap.put(storage.getMountPoint(),
            storage.getFreeSpace());
      }

      String msg = getString(R.string.prefs_app_location_summary) + "\n"
          + getString(R.string.prefs_app_size) + " " + mAppSize
          + " " + getString(R.string.prefs_megabytes);
      mListStoragePref.setSummary(msg);
      mListStoragePref.setEntries(displayNames);
      mListStoragePref.setEntryValues(values);

      final QuranSettings settings = QuranSettings.getInstance(context);
      String current = settings.getAppCustomLocation();
      if (TextUtils.isEmpty(current)) {
        current = values[0].toString();
      }
      mListStoragePref.setValue(current);

      mListStoragePref
          .setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
              final Context context = getActivity();
              if (TextUtils.isEmpty(settings.getAppCustomLocation()) &&
                  Environment.getExternalStorageDirectory().equals(newValue)) {
                // do nothing since we're moving from empty settings to
                // the default sdcard setting, which are the same, but write it.
                return false;
              }

              // this is called right before the preference is saved
              String newLocation = (String) newValue;
              String current = settings.getAppCustomLocation();
              if (mAppSize < storageEmptySpaceMap.get(newLocation)) {
                if (current == null || !current.equals(newLocation)) {
                  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT ||
                      newLocation.equals(mInternalSdcardLocation)) {
                    moveFiles(newLocation);
                  } else {
                    showKitKatConfirmation(newLocation);
                  }
                }
              } else {
                Toast.makeText(context,
                    getString(
                        R.string.prefs_no_enough_space_to_move_files),
                    Toast.LENGTH_LONG).show();
              }
              // this says, "don't write the preference"
              return false;
            }
          });
      mListStoragePref.setEnabled(true);
    } catch (Exception e) {
      Log.e(TAG, "error loading storage options", e);
      hideStorageListPref();
    }
  }

  private void showKitKatConfirmation(final String newLocation) {
    final Context context = getActivity();
    final AlertDialog.Builder b = new AlertDialog.Builder(context)
        .setTitle(R.string.kitkat_external_title)
        .setMessage(R.string.kitkat_external_message)
        .setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            moveFiles(newLocation);
            dialog.dismiss();
            mDialog = null;
          }
        })
        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
            mDialog = null;
          }
        });
    mDialog = b.create();
    mDialog.show();
  }

  private void moveFiles(String newLocation) {
    mMoveFilesTask = new MoveFilesAsyncTask(getActivity(), newLocation);
    mMoveFilesTask.execute();
  }

  @Override
  public void onResume() {
    super.onResume();
    mIsPaused = false;
    getPreferenceScreen().getSharedPreferences()
        .registerOnSharedPreferenceChangeListener(this);
  }

  @Override
  public void onPause() {
    mIsPaused = true;
    getPreferenceScreen().getSharedPreferences()
        .unregisterOnSharedPreferenceChangeListener(this);
    super.onPause();
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
      String key) {
    if (key.equals(Constants.PREF_USE_ARABIC_NAMES)) {
      final Context context = getActivity();
      if (context instanceof QuranPreferenceActivity) {
        ((QuranPreferenceActivity) context).restartActivity();
      }
    }
  }

  private class MoveFilesAsyncTask extends AsyncTask<Void, Void, Boolean> {

    private String newLocation;
    private ProgressDialog dialog;
    private Context appContext;

    private MoveFilesAsyncTask(Context context, String newLocation) {
      this.newLocation = newLocation;
      this.appContext = context.getApplicationContext();
    }

    @Override
    protected void onPreExecute() {
      dialog = new ProgressDialog(getActivity());
      dialog.setMessage(appContext.getString(R.string.prefs_copying_app_files));
      dialog.setCancelable(false);
      dialog.show();
    }

    @Override
    protected Boolean doInBackground(Void... voids) {
      return QuranFileUtils.moveAppFiles(appContext, newLocation);
    }

    @Override
    protected void onPostExecute(Boolean result) {
      if (!mIsPaused) {
        dialog.dismiss();
        if (result) {
          QuranSettings.getInstance(appContext).setAppCustomLocation(newLocation);
          if (mListStoragePref != null) {
            mListStoragePref.setValue(newLocation);
          }
        } else {
          Toast.makeText(appContext,
              getString(R.string.prefs_err_moving_app_files),
              Toast.LENGTH_LONG).show();
        }
        dialog = null;
        mMoveFilesTask = null;
      }
    }
  }

  private class LoadStorageOptionsTask extends AsyncTask<Void, Void, Void> {

    private ProgressDialog dialog;
    private Context appContext;

    public LoadStorageOptionsTask(Context context) {
      this.appContext = context.getApplicationContext();
    }

    @Override
    protected void onPreExecute() {
      dialog = new ProgressDialog(getActivity());
      dialog.setMessage(getString(R.string.prefs_calculating_app_size));
      dialog.setCancelable(false);
      dialog.show();
    }

    @Override
    protected Void doInBackground(Void... voids) {
      mAppSize = QuranFileUtils.getAppUsedSpace(appContext);
      return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
      if (!mIsPaused) {
        loadStorageOptions(appContext);
        mLoadStorageOptionsTask = null;
        dialog.dismiss();
        dialog = null;
      }
    }
  }
}
