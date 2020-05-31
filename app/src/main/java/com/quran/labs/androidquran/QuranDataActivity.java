package com.quran.labs.androidquran;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.work.WorkManager;
import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.quran.data.source.PageProvider;
import com.quran.labs.androidquran.presenter.data.QuranDataPresenter;
import com.quran.labs.androidquran.service.QuranDownloadService;
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver;
import com.quran.labs.androidquran.service.util.PermissionUtil;
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier;
import com.quran.labs.androidquran.service.util.ServiceIntentHelper;
import com.quran.labs.androidquran.ui.QuranActivity;
import com.quran.labs.androidquran.ui.util.ToastCompat;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranSettings;

import com.quran.labs.androidquran.worker.WorkerConstants;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import timber.log.Timber;

/**
 * Launch {@link QuranActivity} after performing the following checks:
 * <ul>
 *   <li>Check that we have permission to write to external storage (if we need this permission)
 *   and if not, ask the user for permission</li>
 *   <li>Verify that we have the necessary Quran data downloaded on the device</li>
 * </ul>
 * The logic is split between {@link QuranDataActivity} and {@link QuranDataPresenter},
 * and {@link QuranDownloadService} is (mostly) used to perform the actual downloading of
 * any Quran data.
 */
public class QuranDataActivity extends Activity implements
    DefaultDownloadReceiver.SimpleDownloadListener,
    ActivityCompat.OnRequestPermissionsResultCallback {

  public static final String PAGES_DOWNLOAD_KEY = "PAGES_DOWNLOAD_KEY";
  private static final int REQUEST_WRITE_TO_SDCARD_PERMISSIONS = 1;
  private static final String QURAN_DIRECTORY_MARKER_FILE = "q4a";
  private static final String QURAN_HIDDEN_DIRECTORY_MARKER_FILE = ".q4a";

  private QuranSettings quranSettings;
  private AlertDialog errorDialog = null;
  private AlertDialog promptForDownloadDialog = null;
  private AlertDialog permissionsDialog;
  private DefaultDownloadReceiver downloadReceiver = null;
  private boolean havePermission = false;
  private QuranDataPresenter.QuranDataStatus quranDataStatus;
  private Disposable disposable;

  @Inject QuranFileUtils quranFileUtils;
  @Inject QuranScreenInfo quranScreenInfo;
  @Inject PageProvider quranPageProvider;
  @Inject QuranDataPresenter quranDataPresenter;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    QuranApplication quranApp = (QuranApplication) getApplication();
    quranApp.getApplicationComponent().inject(this);

    quranSettings = QuranSettings.getInstance(this);
    quranSettings.upgradePreferences();

    // replace null app locations (especially those set to null due to failures
    // of finding a suitable data directory) with the default value to allow
    // retrying to find a suitable location on app startup.
    if (!quranSettings.isAppLocationSet()) {
      quranSettings.setAppCustomLocation(quranSettings.getDefaultLocation());
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    quranDataPresenter.bind(this);

    downloadReceiver = new DefaultDownloadReceiver(this,
        QuranDownloadService.DOWNLOAD_TYPE_PAGES);
    downloadReceiver.setCanCancelDownload(true);
    String action = QuranDownloadNotifier.ProgressIntent.INTENT_NAME;
    LocalBroadcastManager.getInstance(this).registerReceiver(
        downloadReceiver,
        new IntentFilter(action));
    downloadReceiver.setListener(this);

    disposable = Single.timer(100, TimeUnit.MILLISECONDS)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(ignore -> {
          // try to reconnect to the service - delayed since some devices consider
          // onResume as part of the app being in the background for starting-service
          // purposes.
          final Intent reconnectIntent =
              new Intent(QuranDataActivity.this, QuranDownloadService.class);
          reconnectIntent.setAction(QuranDownloadService.ACTION_RECONNECT);
          reconnectIntent.putExtra(QuranDownloadService.EXTRA_DOWNLOAD_TYPE,
              QuranDownloadService.DOWNLOAD_TYPE_PAGES);
          try {
            // this should be perfectly valid - on some devices though, despite the 100ms
            // delay (or a longer one), some devices still complain about not being in the
            // foreground.
            //
            // since this isn't strictly necessary (since the download service should emit
            // periodically on its own anyway, and should handle multiple download requests,
            // etc), let's just drop it if it fails since this is expected to rarely occur.
            startService(reconnectIntent);
          } catch (IllegalStateException ise) {
            Crashlytics.logException(ise);
          }
        });

    checkPermissions();
  }

  @Override
  protected void onPause() {
    disposable.dispose();

    quranDataPresenter.unbind(this);
    if (downloadReceiver != null) {
      downloadReceiver.setListener(null);
      LocalBroadcastManager.getInstance(this).
          unregisterReceiver(downloadReceiver);
      downloadReceiver = null;
    }

    if (promptForDownloadDialog != null) {
      promptForDownloadDialog.dismiss();
      promptForDownloadDialog = null;
    }

    if (errorDialog != null) {
      errorDialog.dismiss();
      errorDialog = null;
    }

    super.onPause();
  }

  private void checkPermissions() {
    final String path = quranSettings.getAppCustomLocation();
    final File fallbackFile = getExternalFilesDir(null);

    boolean usesExternalFileDir = path != null && path.contains("com.quran");
    if (path == null || (usesExternalFileDir && fallbackFile == null)) {
      // suggests that we're on m+ and getExternalFilesDir returned null at some point
      runListView();
      return;
    }

    boolean needsPermission = !usesExternalFileDir || !path.equals(fallbackFile.getAbsolutePath());
    if (needsPermission && !PermissionUtil.haveWriteExternalStoragePermission(this)) {
      // request permission
      if (PermissionUtil.canRequestWriteExternalStoragePermission(this)) {
        Answers.getInstance().logCustom(new CustomEvent("storagePermissionRationaleShown"));
        //show permission rationale dialog
        permissionsDialog = new AlertDialog.Builder(this)
            .setMessage(R.string.storage_permission_rationale)
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
              dialog.dismiss();
              permissionsDialog = null;

              Answers.getInstance().logCustom(
                  new CustomEvent("storagePermissionRationaleAccepted"));
              // request permissions
              requestExternalSdcardPermission();
            })
            .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
              // dismiss the dialog
              dialog.dismiss();
              permissionsDialog = null;

              Answers.getInstance().logCustom(
                  new CustomEvent("storagePermissionRationaleDenied"));
              // fall back if we can
              if (fallbackFile != null) {
                quranSettings.setAppCustomLocation(fallbackFile.getAbsolutePath());
                checkPages();
              } else {
                // set to null so we can try again next launch
                quranSettings.setAppCustomLocation(null);
                runListView();
              }
            })
            .create();
        permissionsDialog.show();
      } else {
        // fall back if we can
        if (fallbackFile != null) {
          quranSettings.setAppCustomLocation(fallbackFile.getAbsolutePath());
          checkPages();
        } else {
          // set to null so we can try again next launch
          quranSettings.setAppCustomLocation(null);
          runListView();
        }
      }
    } else {
      havePermission = true;
      checkPages();
    }
  }

  private void checkPages() {
    quranDataPresenter.checkPages();
  }

  private void requestExternalSdcardPermission() {
    ActivityCompat.requestPermissions(this,
        new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE },
        REQUEST_WRITE_TO_SDCARD_PERMISSIONS);
    quranSettings.setSdcardPermissionsDialogPresented();
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
      @NonNull int[] grantResults) {
    if (requestCode == REQUEST_WRITE_TO_SDCARD_PERMISSIONS) {
      if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        /*
         * taking a risk here. on nexus 6, the permission is granted automatically. on the emulator,
         * a restart is required. on reddit, someone with a nexus 9 said they also didn't need to
         * restart for the permission to take effect.
         *
         * going to assume that it just works to avoid meh code (a check to see if i can actually
         * write, and a PendingIntent plus System.exit to restart the app otherwise). logging to
         * know if we should actually have that code or not.
         *
         * also see:
         * http://stackoverflow.com/questions/32471888/
         */
        havePermission = true;
        Answers.getInstance().logCustom(new CustomEvent("storagePermissionGranted"));
        if (!canWriteSdcardAfterPermissions()) {
          ToastCompat.makeText(this,
              R.string.storage_permission_please_restart, Toast.LENGTH_LONG).show();
        }
        checkPages();
      } else {
        Answers.getInstance().logCustom(new CustomEvent("storagePermissionDenied"));
        final File fallbackFile = getExternalFilesDir(null);
        if (fallbackFile != null) {
          quranSettings.setAppCustomLocation(fallbackFile.getAbsolutePath());
          checkPages();
        } else {
          // set to null so we can try again next launch
          quranSettings.setAppCustomLocation(null);
          runListView();
        }
      }
    }
  }

  private boolean canWriteSdcardAfterPermissions() {
    String location = quranFileUtils.getQuranBaseDirectory(this);
    if (location != null) {
      try {
        if (new File(location).exists() ||
            quranFileUtils.makeQuranDirectory(this, quranScreenInfo.getWidthParam())) {
          File f = new File(location, "" + System.currentTimeMillis());
          if (f.createNewFile()) {
            f.delete();
            return true;
          }
        }
      } catch (Exception e) {
        // no op
      }
    }
    return false;
  }

  @Override
  public void handleDownloadSuccess() {
    if (quranDataStatus != null && !quranDataStatus.havePages()) {
      // didn't have pages before and the download succeeded, which means
      // full zip download was done - let's mark partial pages checker as
      // not needed since we already checked it.
      final String pageType = quranSettings.getPageType();
      quranSettings.setCheckedPartialImages(pageType);

      // cancel any pending work
      WorkManager.getInstance(getApplicationContext())
          .cancelUniqueWork(WorkerConstants.CLEANUP_PREFIX + pageType);
    }
    quranSettings.removeShouldFetchPages();
    runListView();
  }

  @Override
  public void handleDownloadFailure(int errId) {
    if (errorDialog != null && errorDialog.isShowing()) {
      return;
    }

    showFatalErrorDialog(errId);
  }

  private void showFatalErrorDialog(int errorId) {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setMessage(errorId);
    builder.setCancelable(false);
    builder.setPositiveButton(R.string.download_retry,
        (dialog, id) -> {
          dialog.dismiss();
          errorDialog = null;
          removeErrorPreferences();
          downloadQuranImages(true);
        });

    builder.setNegativeButton(R.string.download_cancel,
        (dialog, which) -> {
          dialog.dismiss();
          errorDialog = null;
          removeErrorPreferences();
          quranSettings.setShouldFetchPages(false);
          runListView();
        });

    errorDialog = builder.create();
    errorDialog.show();
  }

  private void removeErrorPreferences() {
    quranSettings.clearLastDownloadError();
  }

  public void onStorageNotAvailable() {
    // no storage mounted, nothing we can do...
    runListView();
  }

  public void onPagesChecked(QuranDataPresenter.QuranDataStatus quranDataStatus) {
    this.quranDataStatus = quranDataStatus;

    if (!quranDataStatus.havePages()) {
      if (quranSettings.didDownloadPages()) {
        // log if we downloaded pages once before
        try {
          onPagesLost();
        } catch (Exception e) {
          Crashlytics.logException(e);
        }
        // clear the "pages downloaded" flag
        quranSettings.removeDidDownloadPages();
      }

      String lastErrorItem = quranSettings.getLastDownloadItemWithError();
      Timber.d("checkPages: need to download pages... lastError: %s", lastErrorItem);
      if (PAGES_DOWNLOAD_KEY.equals(lastErrorItem)) {
        int lastError = quranSettings.getLastDownloadErrorCode();
        int errorId = ServiceIntentHelper
            .getErrorResourceFromErrorCode(lastError, false);
        showFatalErrorDialog(errorId);
      } else if (quranSettings.shouldFetchPages()) {
        downloadQuranImages(false);
      } else {
        promptForDownload();
      }
    } else {
      final String appLocation = quranSettings.getAppCustomLocation();
      final String baseDirectory = quranFileUtils.getQuranBaseDirectory();
      try {
        // try to write a directory to distinguish between the entire Quran directory
        // being removed versus just the images being somehow removed.

        //noinspection ResultOfMethodCallIgnored
        new File(baseDirectory, QURAN_DIRECTORY_MARKER_FILE).createNewFile();
        //noinspection ResultOfMethodCallIgnored
        new File(baseDirectory, QURAN_HIDDEN_DIRECTORY_MARKER_FILE).createNewFile();

        // try writing a file to the app's internal no_backup directory
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          //noinspection ResultOfMethodCallIgnored
          new File(getNoBackupFilesDir(), QURAN_HIDDEN_DIRECTORY_MARKER_FILE).createNewFile();
        }

        quranSettings.setDownloadedPages(System.currentTimeMillis(), appLocation,
            quranDataStatus.getPortraitWidth() + "_" + quranDataStatus.getLandscapeWidth());
      } catch (IOException ioe) {
        Crashlytics.logException(ioe);
      }

      final String patchParam = quranDataStatus.getPatchParam();
      if (!TextUtils.isEmpty(patchParam)) {
        Timber.d("checkPages: have pages, but need patch %s", patchParam);
        promptForDownload();
      } else {
        runListView();
      }
    }
  }

  private void onPagesLost() {
    final String appLocation = quranSettings.getAppCustomLocation();
    // check if appLocation matches either external files dir or the sdcard
    final File appDir = getExternalFilesDir(null);
    final File sdcard = Environment.getExternalStorageDirectory();
    final boolean hasMatch =
        appLocation.equals(appDir == null ? "" : appDir.getAbsolutePath())
            || appLocation.equals(sdcard == null ? "" : sdcard.getAbsolutePath());

    // see if the page path at the time of the first download hasn't changed
    final String lastDownloadedPagePath = quranSettings.getPreviouslyDownloadedPath();
    final boolean isPagePathTheSame = appLocation.equals(lastDownloadedPagePath);

    // see if the last downloaded page types are the same as what we want to download now
    final String lastDownloadedPages = quranSettings.getPreviouslyDownloadedPageTypes();
    final String currentPagesToDownload = quranDataStatus.getPortraitWidth() + "_" +
        quranDataStatus.getLandscapeWidth();
    final boolean arePageSetsEquivalent = lastDownloadedPages.equals(currentPagesToDownload);

    // check for the existence of a .q4a file in the base directory to distinguish
    // the case in which the whole directory was wiped (nothing we can really do here?)
    // and the case when just the images disappeared.
    boolean didHiddenFileSurvive = false;
    final String baseDirectory = quranFileUtils.getQuranBaseDirectory();
    if (baseDirectory != null) {
      try {
        didHiddenFileSurvive =
            new File(baseDirectory, QURAN_HIDDEN_DIRECTORY_MARKER_FILE).exists();
      } catch (Exception e) {
        Crashlytics.logException(e);
      }
    }

    // check for the existence of a q4a file in the base directory - this is the same as
    // above, but is there to see if, perhaps, hidden files survive but non-hidden ones don't.
    boolean didNormalFileSurvive = false;
    if (baseDirectory != null) {
      try {
        didNormalFileSurvive =
            new File(baseDirectory, QURAN_DIRECTORY_MARKER_FILE).exists();
      } catch (Exception e) {
        Crashlytics.logException(e);
      }
    }

    // check for the existence of a .q4a file in the internal no_backup directory.
    boolean didInternalFileSurvive = false;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      try {
        didInternalFileSurvive =
            new File(getNoBackupFilesDir(), QURAN_HIDDEN_DIRECTORY_MARKER_FILE).exists();
      } catch (Exception e) {
        Crashlytics.logException(e);
      }
    }


    // how recently did the files disappear?
    final long downloadTime = quranSettings.getPreviouslyDownloadedTime();
    String recencyOfRemoval;
    if (downloadTime == 0) {
      recencyOfRemoval = "no timestamp";
    } else {
      final long deltaInSeconds = (System.currentTimeMillis() - downloadTime) / 1000;
      if (deltaInSeconds < (5 * 60)) {
        recencyOfRemoval = "within 5 minutes";
      } else if (deltaInSeconds < (10 * 60)) {
        recencyOfRemoval = "within 10 minutes";
      } else if (deltaInSeconds < (60 * 60)) {
        recencyOfRemoval = "within an hour";
      } else if (deltaInSeconds < (24 * 60 * 60)) {
        recencyOfRemoval = "within a day";
      } else {
        recencyOfRemoval = "more than a day";
      }
    }

    // log an event to Answers - this should help figure out why people are complaining that
    // they are always prompted to re-download images, even after they did.
    Answers.getInstance()
        .logCustom(new CustomEvent("debugImagesDisappeared")
            .putCustomAttribute("permissionGranted", havePermission ? "true" : "false")
            .putCustomAttribute("storagePath", appLocation)
            .putCustomAttribute("hasAndroidMatch", hasMatch ? "yes" : "no")
            .putCustomAttribute("isPagePathTheSame", isPagePathTheSame ? "yes" : "no")
            .putCustomAttribute("didNormalFileSurvive", didNormalFileSurvive ? "yes" : "no")
            .putCustomAttribute("didHiddenFileSurvive", didHiddenFileSurvive ? "yes" : "no")
            .putCustomAttribute("didInternalFileSurvive", didInternalFileSurvive ? "yes" : "no")
            .putCustomAttribute("recencyOfRemoval", recencyOfRemoval)
            .putCustomAttribute("arePagesToDownloadTheSame",
                arePageSetsEquivalent ? "yes" : "no"));

    // log an exception
    Timber.w(quranDataStatus.toString());
    Timber.w(quranDataPresenter.getDebugLog());
    Timber.w("appLocation: %s", appLocation);
    Timber.w("sdcard: %s, app dir: %s", sdcard, appDir);
    Timber.w("didNormalFileSurvive: %s", didNormalFileSurvive);
    Timber.w("didInternalFileSurvive: %s", didInternalFileSurvive);
    Timber.w("didHiddenFileSurvive: %s", didHiddenFileSurvive);
    Timber.w("seconds passed: %d, recency: %s",
        (System.currentTimeMillis() - downloadTime) / 1000, recencyOfRemoval);
    Timber.w("isPagePathTheSame: %s", isPagePathTheSame);
    Timber.w("arePagesToDownloadTheSame: %s", arePageSetsEquivalent);
    Timber.e(new IllegalStateException("Deleted Data"), "Unable to Download Pages");
  }

  /**
   * this method asks the service to download quran images.
   *
   * there are two possible cases - the first is one in which we are not
   * sure if a download is going on or not (ie we just came in the app,
   * the files aren't all there, so we want to start downloading).  in
   * this case, we start the download only if we didn't receive any
   * broadcasts before starting it.
   *
   * in the second case, we know what we are doing (either because the user
   * just clicked "download" for the first time or the user asked to retry
   * after an error), then we pass the force parameter, which asks the
   * service to just restart the download irrespective of anything else.
   *
   * @param force whether to force the download to restart or not
   */
  private void downloadQuranImages(boolean force) {
    // if any broadcasts were received, then we are already downloading
    // so unless we know what we are doing (via force), don't ask the
    // service to restart the download
    if (downloadReceiver != null && downloadReceiver.didReceiveBroadcast() && !force) {
      return;
    }

    final QuranDataPresenter.QuranDataStatus dataStatus = quranDataStatus;

    String url;
    if (dataStatus.needPortrait() && !dataStatus.needLandscape()) {
      // phone (and tablet when upgrading on some devices, ex n10)
      url = quranFileUtils.getZipFileUrl();
    } else if (dataStatus.needLandscape() && !dataStatus.needPortrait()) {
      // tablet (when upgrading from pre-tablet on some devices, ex n7).
      url = quranFileUtils.getZipFileUrl(quranScreenInfo.getTabletWidthParam());
    } else {
      // new tablet installation - if both image sets are the same
      // size, then just get the correct one only
      if (quranScreenInfo.getTabletWidthParam().equals(quranScreenInfo.getWidthParam())) {
        url = quranFileUtils.getZipFileUrl();
      } else {
        // otherwise download one zip with both image sets
        String widthParam = quranScreenInfo.getWidthParam() +
            quranScreenInfo.getTabletWidthParam();
        url = quranFileUtils.getZipFileUrl(widthParam);
      }
    }

    // if we have a patch url, just use that
    final String patchParam = dataStatus.getPatchParam();
    if (!TextUtils.isEmpty(patchParam)) {
      url = quranFileUtils.getPatchFileUrl(patchParam, quranPageProvider.getImageVersion());
    }

    String destination = quranFileUtils.getQuranImagesBaseDirectory(QuranDataActivity.this);

    // start service
    Intent intent = ServiceIntentHelper.getDownloadIntent(this, url,
        destination, getString(R.string.app_name), PAGES_DOWNLOAD_KEY,
        QuranDownloadService.DOWNLOAD_TYPE_PAGES);

    if (!force) {
      // handle race condition in which we missed the error preference and
      // the broadcast - if so, just rebroadcast errors so we handle them
      intent.putExtra(QuranDownloadService.EXTRA_REPEAT_LAST_ERROR, true);
    }

    startService(intent);
  }

  private void promptForDownload() {
    final QuranDataPresenter.QuranDataStatus dataStatus = quranDataStatus;
    int message = R.string.downloadPrompt;
    if (quranScreenInfo.isDualPageMode() && dataStatus.needLandscape()) {
      message = R.string.downloadTabletPrompt;
    }

    if (!TextUtils.isEmpty(dataStatus.getPatchParam())) {
      // patch message if applicable
      message = R.string.downloadImportantPrompt;
    }

    AlertDialog.Builder dialog = new AlertDialog.Builder(this);
    dialog.setMessage(message);
    dialog.setCancelable(false);
    dialog.setPositiveButton(R.string.downloadPrompt_ok,
        (dialog1, id) -> {
          dialog1.dismiss();
          promptForDownloadDialog = null;
          quranSettings.setShouldFetchPages(true);
          downloadQuranImages(true);
        });

    dialog.setNegativeButton(R.string.downloadPrompt_no,
        (dialog12, id) -> {
          dialog12.dismiss();
          promptForDownloadDialog = null;
          runListView();
        });

    promptForDownloadDialog = dialog.create();
    promptForDownloadDialog.setTitle(R.string.downloadPrompt_title);
    promptForDownloadDialog.show();
  }

  protected void runListView() {
    Intent i = new Intent(this, QuranActivity.class);
    i.putExtra(QuranActivity.EXTRA_SHOW_TRANSLATION_UPGRADE, quranSettings.haveUpdatedTranslations());
    startActivity(i);
    finish();
  }
}
