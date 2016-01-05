package com.quran.labs.androidquran.ui.fragment;

import com.quran.labs.androidquran.QuranPreferenceActivity;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.model.bookmark.BookmarkImportExportModel;
import com.quran.labs.androidquran.service.util.PermissionUtil;
import com.quran.labs.androidquran.ui.AudioManagerActivity;
import com.quran.labs.androidquran.ui.TranslationManagerActivity;
import com.quran.labs.androidquran.ui.preference.DataListPreference;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.StorageUtils;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import timber.log.Timber;

public class QuranSettingsFragment extends PreferenceFragment implements
    SharedPreferences.OnSharedPreferenceChangeListener {

  private DataListPreference mListStoragePref;
  private MoveFilesAsyncTask mMoveFilesTask;
  private List<StorageUtils.Storage> mStorageList;
  private LoadStorageOptionsTask mLoadStorageOptionsTask;
  private int mAppSize;
  private boolean mIsPaused;
  private String mInternalSdcardLocation;
  private AlertDialog mDialog;
  private Context mAppContext;
  private Subscription mExportSubscription = null;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    addPreferencesFromResource(R.xml.quran_preferences);

    final Context context = getActivity();
    mAppContext = context.getApplicationContext();
    // remove the tablet mode preference if it doesn't exist
    if (!QuranScreenInfo.getOrMakeInstance(context).isTablet(context)) {
      Preference tabletModePreference =
          findPreference(Constants.PREF_TABLET_ENABLED);
      PreferenceCategory category =
          (PreferenceCategory) findPreference(Constants.PREF_DISPLAY_CATEGORY);
      category.removePreference(tabletModePreference);
    }

    // handle translation manager click
    final Preference translationPref = findPreference(Constants.PREF_TRANSLATION_MANAGER);
    translationPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
      @Override
      public boolean onPreferenceClick(Preference preference) {
        startActivity(new Intent(getActivity(), TranslationManagerActivity.class));
        return true;
      }
    });

    // handle audio manager click
    final Preference audioManagerPref = findPreference(Constants.PREF_AUDIO_MANAGER);
    audioManagerPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
      @Override
      public boolean onPreferenceClick(Preference preference) {
        startActivity(new Intent(getActivity(), AudioManagerActivity.class));
        return true;
      }
    });

    final Preference exportPref = findPreference(Constants.PREF_EXPORT);
    exportPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
      @Override
      public boolean onPreferenceClick(Preference preference) {
        BookmarkImportExportModel model = new BookmarkImportExportModel(getActivity());
        if (mExportSubscription == null) {
          mExportSubscription = model.exportBookmarksObservable()
              .observeOn(AndroidSchedulers.mainThread())
              .subscribe(new Subscriber<Uri>() {
                @Override
                public void onCompleted() {
                  mExportSubscription = null;
                }

                @Override
                public void onError(Throwable e) {
                  mExportSubscription = null;
                  if (isAdded()) {
                    Toast.makeText(context, R.string.export_data_error, Toast.LENGTH_LONG).show();
                  }
                }

                @Override
                public void onNext(Uri uri) {
                  Intent shareIntent = new Intent(Intent.ACTION_SEND);
                  shareIntent.setType("application/json");
                  shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                  shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                  List<ResolveInfo> intents = mAppContext.getPackageManager()
                      .queryIntentActivities(shareIntent, 0);
                  if (intents.size() > 1) {
                    // if only one, then that is likely Quran for Android itself, so don't show
                    // the chooser since it doesn't really make sense.
                    context.startActivity(Intent.createChooser(shareIntent,
                        context.getString(R.string.prefs_export_title)));
                  } else {
                    File exportedPath = new File(mAppContext.getExternalFilesDir(null), "backups");
                    String exported = mAppContext.getString(
                        R.string.exported_data, exportedPath.toString());
                    Toast.makeText(mAppContext, exported, Toast.LENGTH_LONG).show();
                  }
                }
              });
        }
        return true;
      }
    });

    mInternalSdcardLocation =
        Environment.getExternalStorageDirectory().getAbsolutePath();

    mListStoragePref = (DataListPreference) findPreference(getString(R.string.prefs_app_location));
    mListStoragePref.setEnabled(false);

    try {
      mStorageList = StorageUtils.getAllStorageLocations(context.getApplicationContext());
    } catch (Exception e) {
      Timber.d("Exception while trying to get storage locations",e);
      mStorageList = new ArrayList<>();
    }

    // Hide app location pref if there is no storage option
    // except for the normal Environment.getExternalStorageDirectory
    if (mStorageList == null || mStorageList.size() <= 1) {
      Timber.d("removing advanced settings from preferences");
      hideStorageListPref();
    } else {
      mLoadStorageOptionsTask = new LoadStorageOptionsTask(context);
      mLoadStorageOptionsTask.execute();
    }
  }

  @Override
  public void onDestroy() {
    if (mExportSubscription != null) {
      mExportSubscription.unsubscribe();
    }

    if (mDialog != null) {
      mDialog.dismiss();
    }
    super.onDestroy();
  }

  private void hideStorageListPref() {
    PreferenceCategory category =
        (PreferenceCategory) findPreference(Constants.PREF_ADVANCED_CATEGORY);
    category.removePreference(mListStoragePref);
  }

  private void loadStorageOptions(Context context) {
    try {
      if (mAppSize == -1) {
        // sdcard is not mounted...
        hideStorageListPref();
        return;
      }

      mListStoragePref.setLabelsAndSummaries(context, mAppSize, mStorageList);
      final HashMap<String, StorageUtils.Storage> storageMap =
          new HashMap<>(mStorageList.size());
      for (StorageUtils.Storage storage : mStorageList) {
        storageMap.put(storage.getMountPoint(), storage);
      }

      mListStoragePref
          .setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
              final Context context = getActivity();
              final QuranSettings settings = QuranSettings.getInstance(context);

              if (TextUtils.isEmpty(settings.getAppCustomLocation()) &&
                  Environment.getExternalStorageDirectory().equals(newValue)) {
                // do nothing since we're moving from empty settings to
                // the default sdcard setting, which are the same, but write it.
                return false;
              }

              // this is called right before the preference is saved
              String newLocation = (String) newValue;
              StorageUtils.Storage destStorage = storageMap.get(newLocation);
              String current = settings.getAppCustomLocation();
              if (mAppSize < destStorage.getFreeSpace()) {
                if (current == null || !current.equals(newLocation)) {
                  if (destStorage.doesRequirePermission()) {
                    if (!PermissionUtil.haveWriteExternalStoragePermission(context)) {
                      requestExternalStoragePermission(newLocation);
                      return false;
                    }

                    // we have the permission, so fall through and handle the move
                  }
                  handleMove(newLocation);
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
      Timber.e("error loading storage options",e);
      hideStorageListPref();
    }
  }

  private void requestExternalStoragePermission(String newLocation) {
    Activity activity = getActivity();
    if (activity instanceof QuranPreferenceActivity) {
      ((QuranPreferenceActivity) activity).requestWriteExternalSdcardPermission(newLocation);
    }
  }

  private void handleMove(String newLocation) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT ||
        newLocation.equals(mInternalSdcardLocation)) {
      moveFiles(newLocation);
    } else {
      showKitKatConfirmation(newLocation);
    }
  }

  private void showKitKatConfirmation(final String newLocation) {
    final Context context = getActivity();
    final AlertDialog.Builder b = new AlertDialog.Builder(context)
        .setTitle(R.string.warning)
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

  public void moveFiles(String newLocation) {
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

    private Context appContext;

    public LoadStorageOptionsTask(Context context) {
      this.appContext = context.getApplicationContext();
    }

    @Override
    protected void onPreExecute() {
      mListStoragePref.setSummary(R.string.prefs_calculating_app_size);
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
        mListStoragePref.setSummary(R.string.prefs_app_location_summary);
      }
    }
  }
}
