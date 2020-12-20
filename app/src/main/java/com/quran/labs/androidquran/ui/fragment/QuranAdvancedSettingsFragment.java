package com.quran.labs.androidquran.ui.fragment;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import com.quran.labs.androidquran.BuildConfig;
import com.quran.labs.androidquran.QuranAdvancedPreferenceActivity;
import com.quran.labs.androidquran.QuranApplication;
import com.quran.labs.androidquran.QuranImportActivity;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.model.bookmark.BookmarkImportExportModel;
import com.quran.labs.androidquran.service.util.PermissionUtil;
import com.quran.labs.androidquran.ui.preference.DataListPreference;
import com.quran.labs.androidquran.ui.util.ToastCompat;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.util.RecordingLogTree;
import com.quran.labs.androidquran.util.StorageUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.inject.Inject;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.observers.DisposableMaybeObserver;
import io.reactivex.observers.DisposableSingleObserver;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;

public class QuranAdvancedSettingsFragment extends PreferenceFragmentCompat {
  private static final int REQUEST_CODE_IMPORT = 1;

  private DataListPreference listStoragePref;
  private MoveFilesAsyncTask moveFilesAsyncTask;
  private List<StorageUtils.Storage> storageList;
  private LoadStorageOptionsTask loadStorageOptionsTask;
  private int appSize;
  private boolean isPaused;
  private String internalSdcardLocation;
  private AlertDialog dialog;
  private Context appContext;
  private Disposable exportSubscription = null;
  private Disposable logsSubscription;

  @Inject BookmarkImportExportModel bookmarkImportExportModel;
  @Inject QuranFileUtils quranFileUtils;
  @Inject QuranScreenInfo quranScreenInfo;

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.quran_advanced_preferences);

    final Context context = requireActivity();
    appContext = context.getApplicationContext();

    // field injection
    ((QuranApplication) appContext).getApplicationComponent().inject(this);


    final Preference logsPref = findPreference(Constants.PREF_LOGS);
    if (BuildConfig.DEBUG || "beta".equals(BuildConfig.BUILD_TYPE)) {
      logsPref.setOnPreferenceClickListener(preference -> {
        if (logsSubscription == null) {
          logsSubscription = Observable.fromIterable(Timber.forest())
              .filter(tree -> tree instanceof RecordingLogTree)
              .firstElement()
              .map(tree -> ((RecordingLogTree) tree).getLogs())
              .map(logs -> QuranUtils.getDebugInfo(appContext, quranScreenInfo) + "\n\n" + logs)
              .subscribeOn(Schedulers.io())
              .observeOn(AndroidSchedulers.mainThread())
              .subscribeWith(new DisposableMaybeObserver<String>() {
                @Override
                public void onSuccess(@NonNull String logs) {
                  Intent intent = new Intent(Intent.ACTION_SEND);
                  intent.setType("message/rfc822");
                  intent.putExtra(Intent.EXTRA_EMAIL,
                      new String[]{ appContext.getString(R.string.logs_email) });
                  intent.putExtra(Intent.EXTRA_TEXT, logs);
                  intent.putExtra(Intent.EXTRA_SUBJECT, "Logs");
                  startActivity(Intent.createChooser(intent,
                      appContext.getString(R.string.prefs_send_logs_title)));
                  logsSubscription = null;
                }

                @Override
                public void onError(@NonNull Throwable e) {
                }

                @Override
                public void onComplete() {
                }
              });
        }
        return true;
      });
    } else {
      removeAdvancePreference(logsPref);
    }

    final Preference importPref = findPreference(Constants.PREF_IMPORT);
    importPref.setOnPreferenceClickListener(preference -> {
      Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
      intent.setType("*/*");
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        String[] mimeTypes = new String[]{"application/*", "text/*"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
      }
      startActivityForResult(intent, REQUEST_CODE_IMPORT);
      return true;
    });

    final Preference exportPref = findPreference(Constants.PREF_EXPORT);
    exportPref.setOnPreferenceClickListener(preference -> {
      if (exportSubscription == null) {
        exportSubscription = bookmarkImportExportModel.exportBookmarksObservable()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeWith(new DisposableSingleObserver<Uri>() {
              @Override
              public void onSuccess(@NonNull Uri uri) {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("application/json");
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                List<ResolveInfo> intents = appContext.getPackageManager()
                    .queryIntentActivities(shareIntent, 0);
                if (intents.size() > 1) {
                  // if only one, then that is likely Quran for Android itself, so don't show
                  // the chooser since it doesn't really make sense.
                  context.startActivity(Intent.createChooser(shareIntent,
                      context.getString(R.string.prefs_export_title)));
                } else {
                  File exportedPath = new File(appContext.getExternalFilesDir(null), "backups");
                  String exported = appContext.getString(
                      R.string.exported_data, exportedPath.toString());
                  ToastCompat.makeText(appContext, exported, Toast.LENGTH_LONG).show();
                }
              }

              @Override
              public void onError(@NonNull Throwable e) {
                exportSubscription = null;
                if (isAdded()) {
                  ToastCompat.makeText(context, R.string.export_data_error, Toast.LENGTH_LONG).show();
                }
              }
            });
      }
      return true;
    });

    internalSdcardLocation = Environment.getExternalStorageDirectory().getAbsolutePath();

    listStoragePref = findPreference(getString(R.string.prefs_app_location));
    listStoragePref.setEnabled(false);

    try {
      storageList = StorageUtils.getAllStorageLocations(context.getApplicationContext());
    } catch (Exception e) {
      Timber.d(e, "Exception while trying to get storage locations");
      storageList = new ArrayList<>();
    }

    // Hide app location pref if there is no storage option
    // except for the normal Environment.getExternalStorageDirectory
    if (storageList.size() <= 1) {
      Timber.d("removing advanced settings from preferences");
      hideStorageListPref();
    } else {
      loadStorageOptionsTask = new LoadStorageOptionsTask(context, quranFileUtils);
      loadStorageOptionsTask.execute();
    }
  }

  @Override
  public void onDestroy() {
    if (exportSubscription != null) {
      exportSubscription.dispose();
    }

    if (logsSubscription != null) {
      logsSubscription.dispose();
    }

    if (dialog != null) {
      dialog.dismiss();
    }
    super.onDestroy();
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == REQUEST_CODE_IMPORT && resultCode == Activity.RESULT_OK) {
      Activity activity = getActivity();
      if (activity != null) {
        Intent intent = new Intent(activity, QuranImportActivity.class);
        intent.setData(data.getData());
        startActivity(intent);
      }
    }
  }

  private void removeAdvancePreference(Preference preference) {
    // these null checks are to fix a crash due to an NPE on 4.4.4
    if (preference != null) {
      PreferenceGroup group = findPreference(Constants.PREF_ADVANCED_QURAN_SETTINGS);
      if (group != null) {
        group.removePreference(preference);
      }
    }
  }

  private void hideStorageListPref() {
    removeAdvancePreference(listStoragePref);
  }

  private void loadStorageOptions(Context context) {
    try {
      if (appSize == -1) {
        // sdcard is not mounted...
        hideStorageListPref();
        return;
      }

      listStoragePref.setLabelsAndSummaries(context, appSize, storageList);
      final HashMap<String, StorageUtils.Storage> storageMap =
          new HashMap<>(storageList.size());
      for (StorageUtils.Storage storage : storageList) {
        storageMap.put(storage.getMountPoint(), storage);
      }

      listStoragePref
          .setOnPreferenceChangeListener((preference, newValue) -> {
            final Context context1 = requireActivity();
            final QuranSettings settings = QuranSettings.getInstance(context1);

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
            if (appSize < destStorage.getFreeSpace()) {
              if (current == null || !current.equals(newLocation)) {
                handleMove(newLocation, destStorage);
              }
            } else {
              ToastCompat.makeText(context1,
                  getString(
                      R.string.prefs_no_enough_space_to_move_files),
                  Toast.LENGTH_LONG).show();
            }
            // this says, "don't write the preference"
            return false;
          });
      listStoragePref.setEnabled(true);
    } catch (Exception e) {
      Timber.e(e, "error loading storage options");
      hideStorageListPref();
    }
  }

  private void requestExternalStoragePermission(String newLocation) {
    Activity activity = getActivity();
    if (activity instanceof QuranAdvancedPreferenceActivity) {
      ((QuranAdvancedPreferenceActivity) activity)
          .requestWriteExternalSdcardPermission(newLocation);
    }
  }


  private void handleMove(String newLocation, StorageUtils.Storage storageLocation) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT ||
        newLocation.equals(internalSdcardLocation)) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // on Android Q (not really "above" since we don't show the option above),
        // warn if the person tries to use the /sdcard path.
        showScopedStorageConfirmation(newLocation, storageLocation);
      } else {
        // otherwise just copy
        moveFiles(newLocation, storageLocation);
      }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      // don't warn for using Android app directories on Q and above
      moveFiles(newLocation, storageLocation);
    } else {
      // but on older versions, warn
      showKitKatConfirmation(newLocation, storageLocation);
    }
  }

  private void showScopedStorageConfirmation(final String newLocation,
                                             final StorageUtils.Storage storageLocation) {
    showConfirmation(newLocation, storageLocation, R.string.scoped_storage_message);
  }

  private void showKitKatConfirmation(final String newLocation,
                                      final StorageUtils.Storage storageLocation) {
    showConfirmation(newLocation, storageLocation, R.string.kitkat_external_message);
  }

  private void showConfirmation(final String newLocation,
                                final StorageUtils.Storage storageLocation,
                                @StringRes int message) {
    final Context context = getActivity();
    if (context != null) {
      final AlertDialog.Builder b = new AlertDialog.Builder(context)
          .setTitle(R.string.warning)
          .setMessage(message)
          .setPositiveButton(R.string.dialog_ok, (currentDialog, which) -> {
            moveFiles(newLocation, storageLocation);
            currentDialog.dismiss();
            QuranAdvancedSettingsFragment.this.dialog = null;
          })
          .setNegativeButton(R.string.cancel, (currentDialog, which) -> {
            currentDialog.dismiss();
            QuranAdvancedSettingsFragment.this.dialog = null;
          });
      dialog = b.create();
      dialog.show();
    }
  }

  private void moveFiles(String newLocation, StorageUtils.Storage storageLocation) {
    final Context context = getContext();
    if (context != null) {
      if (storageLocation.doesRequirePermission() &&
          !PermissionUtil.haveWriteExternalStoragePermission(context)) {
        requestExternalStoragePermission(newLocation);
      } else {
        moveFiles(newLocation);
      }
    }
  }

  public void moveFiles(String newLocation) {
    final Context context = getContext();
    if (context != null) {
      moveFilesAsyncTask = new MoveFilesAsyncTask(context, newLocation, quranFileUtils);
      moveFilesAsyncTask.execute();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    isPaused = false;
  }

  @Override
  public void onPause() {
    isPaused = true;
    super.onPause();
  }


  private class MoveFilesAsyncTask extends AsyncTask<Void, Void, Boolean> {

    private final String newLocation;
    private ProgressDialog dialog;
    private final Context appContext;
    private final QuranFileUtils quranFileUtils;

    private MoveFilesAsyncTask(Context context, String newLocation, QuranFileUtils quranFileUtils) {
      this.newLocation = newLocation;
      this.appContext = context.getApplicationContext();
      this.quranFileUtils = quranFileUtils;
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
      return quranFileUtils.moveAppFiles(appContext, newLocation);
    }

    @Override
    protected void onPostExecute(Boolean result) {
      if (!isPaused) {
        dialog.dismiss();
        if (result) {
          final QuranSettings quranSettings = QuranSettings.getInstance(appContext);
          quranSettings.setAppCustomLocation(newLocation);
          quranSettings.removeDidDownloadPages();
          if (listStoragePref != null) {
            listStoragePref.setValue(newLocation);
          }
        } else {
          ToastCompat.makeText(appContext,
              getString(R.string.prefs_err_moving_app_files),
              Toast.LENGTH_LONG).show();
        }
        dialog = null;
        moveFilesAsyncTask = null;
      }
    }
  }

  private class LoadStorageOptionsTask extends AsyncTask<Void, Void, Void> {

    private final Context appContext;
    private final QuranFileUtils quranFileUtils;

    LoadStorageOptionsTask(Context context, QuranFileUtils quranFileUtils) {
      this.appContext = context.getApplicationContext();
      this.quranFileUtils = quranFileUtils;
    }

    @Override
    protected void onPreExecute() {
      listStoragePref.setSummary(R.string.prefs_calculating_app_size);
    }

    @Override
    protected Void doInBackground(Void... voids) {
      appSize = quranFileUtils.getAppUsedSpace(appContext);
      return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
      if (!isPaused) {
        loadStorageOptions(appContext);
        loadStorageOptionsTask = null;
        listStoragePref.setSummary(R.string.prefs_app_location_summary);
      }
    }
  }
}
