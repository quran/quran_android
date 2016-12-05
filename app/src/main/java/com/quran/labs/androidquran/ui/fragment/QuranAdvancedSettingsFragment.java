package com.quran.labs.androidquran.ui.fragment;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
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

import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.quran.labs.androidquran.BuildConfig;
import com.quran.labs.androidquran.QuranAdvancedPreferenceActivity;
import com.quran.labs.androidquran.QuranApplication;
import com.quran.labs.androidquran.QuranImportActivity;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.model.bookmark.BookmarkImportExportModel;
import com.quran.labs.androidquran.service.util.PermissionUtil;
import com.quran.labs.androidquran.ui.preference.DataListPreference;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.util.RecordingLogTree;
import com.quran.labs.androidquran.util.StorageUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.observers.DisposableMaybeObserver;
import io.reactivex.observers.DisposableSingleObserver;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;

public class QuranAdvancedSettingsFragment extends PreferenceFragment{
  private static final int REQUEST_CODE_IMPORT = 1;

  private DataListPreference mListStoragePref;
  private MoveFilesAsyncTask mMoveFilesTask;
  private List<StorageUtils.Storage> mStorageList;
  private LoadStorageOptionsTask mLoadStorageOptionsTask;
  private int mAppSize;
  private boolean mIsPaused;
  private String mInternalSdcardLocation;
  private AlertDialog mDialog;
  private Context mAppContext;
  private Disposable mExportSubscription = null;
  private Disposable mLogsSubscription;

  @Inject BookmarkImportExportModel bookmarkImportExportModel;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    addPreferencesFromResource(R.xml.quran_advanced_preferences);

    final Context context = getActivity();
    mAppContext = context.getApplicationContext();

    // field injection
    ((QuranApplication) mAppContext).getApplicationComponent().inject(this);


    final Preference logsPref = findPreference(Constants.PREF_LOGS);
    if (BuildConfig.DEBUG || "beta".equals(BuildConfig.BUILD_TYPE)) {
      logsPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
        @Override
        public boolean onPreferenceClick(Preference preference) {
          if (mLogsSubscription == null) {
            mLogsSubscription = Observable.fromIterable(Timber.forest())
                .filter(new Predicate<Timber.Tree>() {
                  @Override
                  public boolean test(Timber.Tree tree) throws Exception {
                    return tree instanceof RecordingLogTree;
                  }
                })
                .firstElement()
                .map(new Function<Timber.Tree, String>() {
                  @Override
                  public String apply(Timber.Tree tree) {
                    return ((RecordingLogTree) tree).getLogs();
                  }
                })
                .map(new Function<String, String>() {
                  @Override
                  public String apply(String logs) {
                    return QuranUtils.getDebugInfo(mAppContext) + "\n\n" + logs;
                  }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new DisposableMaybeObserver<String>() {
                  @Override
                  public void onSuccess(String logs) {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("message/rfc822");
                    intent.putExtra(Intent.EXTRA_EMAIL,
                        new String[]{mAppContext.getString(R.string.logs_email)});
                    intent.putExtra(Intent.EXTRA_TEXT, logs);
                    intent.putExtra(Intent.EXTRA_SUBJECT, "Logs");
                    startActivity(Intent.createChooser(intent,
                        mAppContext.getString(R.string.prefs_send_logs_title)));
                    mLogsSubscription = null;
                  }

                  @Override
                  public void onError(Throwable e) {
                  }

                  @Override
                  public void onComplete() {
                  }
                });
          }
          return true;
        }
      });
    } else {
      PreferenceCategory category =
          (PreferenceCategory) findPreference(Constants.PREF_ADVANCED_CATEGORY);
      category.removePreference(logsPref);
    }

    final Preference importPref = findPreference(Constants.PREF_IMPORT);
    importPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
      @Override
      public boolean onPreferenceClick(Preference preference) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
          String[] mimeTypes = new String[]{ "application/*", "text/*" };
          intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        }
        startActivityForResult(intent, REQUEST_CODE_IMPORT);
        return true;
      }
    });

    final Preference exportPref = findPreference(Constants.PREF_EXPORT);
    exportPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
      @Override
      public boolean onPreferenceClick(Preference preference) {
        if (mExportSubscription == null) {
          mExportSubscription = bookmarkImportExportModel.exportBookmarksObservable()
              .observeOn(AndroidSchedulers.mainThread())
              .subscribeWith(new DisposableSingleObserver<Uri>() {
                @Override
                public void onSuccess(Uri uri) {
                  Answers.getInstance().logCustom(new CustomEvent("exportData"));
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

                @Override
                public void onError(Throwable e) {
                  mExportSubscription = null;
                  if (isAdded()) {
                    Toast.makeText(context, R.string.export_data_error, Toast.LENGTH_LONG).show();
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
      Timber.d(e, "Exception while trying to get storage locations");
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
      mExportSubscription.dispose();
    }

    if (mLogsSubscription != null) {
      mLogsSubscription.dispose();
    }

    if (mDialog != null) {
      mDialog.dismiss();
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
      Timber.e(e, "error loading storage options");
      hideStorageListPref();
    }
  }

  private void requestExternalStoragePermission(String newLocation) {
    Activity activity = getActivity();
    if (activity instanceof QuranAdvancedPreferenceActivity) {
      ((QuranAdvancedPreferenceActivity) activity).requestWriteExternalSdcardPermission(newLocation);
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
  }

  @Override
  public void onPause() {
    mIsPaused = true;
    super.onPause();
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
