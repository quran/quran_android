package com.quran.labs.androidquran;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.quran.data.source.PageProvider;
import com.quran.labs.androidquran.data.QuranDataProvider;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.presenter.translation.TranslationManagerPresenter;
import com.quran.labs.androidquran.service.QuranDownloadService;
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver;
import com.quran.labs.androidquran.service.util.PermissionUtil;
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier;
import com.quran.labs.androidquran.service.util.ServiceIntentHelper;
import com.quran.labs.androidquran.ui.QuranActivity;
import com.quran.labs.androidquran.util.CopyDatabaseUtil;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranPartialPageChecker;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranSettings;

import java.io.File;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import timber.log.Timber;

public class QuranDataActivity extends Activity implements
    DefaultDownloadReceiver.SimpleDownloadListener,
    ActivityCompat.OnRequestPermissionsResultCallback {

  public static final String PAGES_DOWNLOAD_KEY = "PAGES_DOWNLOAD_KEY";
  private static final int REQUEST_WRITE_TO_SDCARD_PERMISSIONS = 1;

  private boolean isPaused = false;
  private AsyncTask<Void, Void, Boolean> checkPagesTask;
  private QuranSettings quranSettings;
  private AlertDialog errorDialog = null;
  private AlertDialog promptForDownloadDialog = null;
  private AlertDialog permissionsDialog;
  private DefaultDownloadReceiver downloadReceiver = null;
  private boolean needPortraitImages = false;
  private boolean needLandscapeImages = false;
  private boolean havePermission = false;
  private boolean taskIsRunning;
  private String patchUrl;
  private int fromVersion;

  @Inject QuranInfo quranInfo;
  @Inject QuranFileUtils quranFileUtils;
  @Inject QuranScreenInfo quranScreenInfo;
  @Inject PageProvider quranPageProvider;
  @Inject CopyDatabaseUtil copyDatabaseUtil;
  @Inject QuranPartialPageChecker quranPartialPageChecker;
  @Inject TranslationManagerPresenter translationManagerPresenter;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    QuranApplication quranApp = (QuranApplication) getApplication();
    quranApp.getApplicationComponent().inject(this);

    quranSettings = QuranSettings.getInstance(this);
    fromVersion = quranSettings.getVersion();
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

    isPaused = false;
    downloadReceiver = new DefaultDownloadReceiver(this,
        QuranDownloadService.DOWNLOAD_TYPE_PAGES);
    downloadReceiver.setCanCancelDownload(true);
    String action = QuranDownloadNotifier.ProgressIntent.INTENT_NAME;
    LocalBroadcastManager.getInstance(this).registerReceiver(
        downloadReceiver,
        new IntentFilter(action));
    downloadReceiver.setListener(this);
    checkPermissions();
  }

  @Override
  protected void onPause() {
    isPaused = true;
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
    if (taskIsRunning) {
      return;
    }

    taskIsRunning = true;

    // check whether or not we need to download
    checkPagesTask = new CheckPagesAsyncTask(this);
    checkPagesTask.execute();
  }

  private void requestExternalSdcardPermission() {
    ActivityCompat.requestPermissions(this,
        new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE },
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
          Answers.getInstance().logCustom(new CustomEvent("storagePermissionNeedsRestart"));
          Toast.makeText(this,
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

  class CheckPagesAsyncTask extends AsyncTask<Void, Void, Boolean> {

    private final Context appContext;
    private String patchParam;
    private boolean storageNotAvailable;

    CheckPagesAsyncTask(Context context) {
      appContext = context.getApplicationContext();
    }

    @Override
    protected Boolean doInBackground(Void... params) {
      final String baseDir = quranFileUtils.getQuranBaseDirectory(appContext);
      if (baseDir == null) {
        storageNotAvailable = true;
        return false;
      }

      // there was a bug in 2.9.2 through 2.9.2-p2 where an upgraded tafseer could be
      // downloaded while not retaining the record in the translations database. sync
      // with the database to fix this in these cases.
      if (fromVersion > 2920 && fromVersion < 2923) {
        // subscribe on the current thread which is a background thread
        translationManagerPresenter.syncTranslationsWithCache()
            .subscribe();
      }

      final File pageType = new File(baseDir, "pageType");
      if (pageType.exists()) {
        quranFileUtils.deleteFileOrDirectory(pageType);
      }

      final int totalPages = quranInfo.getNumberOfPages();
      if (!quranSettings.haveDefaultImagesDirectory()) {
           /* previously, we would send any screen widths greater than 1280
            * to get 1920 images. this was problematic for various reasons,
            * including:
            * a. a texture limit for the maximum size of a bitmap that could
            *    be loaded, which the 1920x3106 images exceeded on devices
            *    with the minimum 2048 height capacity.
            * b. slow to switch pages due to the massive size of the gl
            *    texture loaded by android.
            *
            * consequently, in this new version, we make anything above 1024
            * fallback to a 1260 bucket (height of 2038). this works around
            * both problems (much faster page flipping now too) with a very
            * minor loss in quality.
            *
            * this code checks and sees, if the user already has a complete
            * folder of images - 1920, then 1280, then 1024 - and in any of
            * those cases, sets that in the pref so we load those instead of
            * the new 1260 images.
            */
        final String fallback =
            quranFileUtils.getPotentialFallbackDirectory(appContext, totalPages);
        if (fallback != null) {
          Timber.d("setting fallback pages to %s", fallback);
          quranSettings.setDefaultImagesDirectory(fallback);
          quranScreenInfo.setOverrideParam(fallback);
        }
      }

      final int latestImagesVersion = quranPageProvider.getImageVersion();
      final String width = quranScreenInfo.getWidthParam();
      if (quranScreenInfo.isDualPageMode()) {
        final String tabletWidth = quranScreenInfo.getTabletWidthParam();
        quranPartialPageChecker.checkPages(latestImagesVersion, totalPages, width, tabletWidth);
        boolean haveLandscape = quranFileUtils.haveAllImages(appContext, tabletWidth, totalPages, true);
        boolean havePortrait = tabletWidth.equals(width) ? haveLandscape :
            quranFileUtils.haveAllImages(appContext, width, totalPages, true);
        needPortraitImages = !havePortrait;
        needLandscapeImages = !haveLandscape;
        Timber.d("checkPages: have portrait images: %s, have landscape images: %s",
            havePortrait ? "yes" : "no", haveLandscape ? "yes" : "no");
        if (haveLandscape && havePortrait) {
          // if we have the images, see if we need a patch set or not
          if (!quranFileUtils.isVersion(appContext, width, latestImagesVersion) ||
              !quranFileUtils.isVersion(appContext, tabletWidth, latestImagesVersion)) {
            if (!width.equals(tabletWidth)) {
              patchParam = width + tabletWidth;
            } else {
              patchParam = width;
            }
          }
        }

        final boolean haveAll = haveLandscape && havePortrait;
        if (haveAll) {
          copyArabicDatabaseIfNecessary();
        }
        return haveAll;
      } else {
        quranPartialPageChecker.checkPages(latestImagesVersion, totalPages, width, width);
        boolean haveAll = quranFileUtils.haveAllImages(appContext,
            quranScreenInfo.getWidthParam(), totalPages, true);
        Timber.d("checkPages: have all images: %s", haveAll ? "yes" : "no");
        needPortraitImages = !haveAll;
        needLandscapeImages = false;
        if (haveAll && !quranFileUtils.isVersion(appContext, width, latestImagesVersion)) {
          patchParam = width;
        }

        if (haveAll) {
          copyArabicDatabaseIfNecessary();
        }
        return haveAll;
      }
    }

    @Override
    protected void onPostExecute(@NonNull Boolean result) {
      checkPagesTask = null;
      patchUrl = null;
      taskIsRunning = false;

      if (isPaused) {
        return;
      }

      if (!result) {
        // we need to download pages in this case

        // if we downloaded pages once before
        if (quranSettings.didDownloadPages()) {
          // log an event to Answers - this should help figure out why people are complaining that
          // they are always prompted to re-download images, even after they did.
          Answers.getInstance()
              .logCustom(new CustomEvent("imagesDisappeared")
                .putCustomAttribute("permissionGranted", havePermission ? "true" : "false")
                .putCustomAttribute("osVersion", Build.VERSION.SDK_INT)
                .putCustomAttribute("storagePath", quranSettings.getAppCustomLocation())
                .putCustomAttribute("storageNotAvailable", storageNotAvailable? "true" : "false"));
          Timber.e(new IllegalStateException("Deleted Data"), "Unable to Download Pages");
          quranSettings.setDownloadedPages(false);
        }

        if (storageNotAvailable) {
          // no storage mounted, nothing we can do...
          runListView();
          return;
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
        quranSettings.setDownloadedPages(true);
        if (!TextUtils.isEmpty(patchParam)) {
          Timber.d("checkPages: have pages, but need patch %s", patchParam);
          patchUrl = quranFileUtils.getPatchFileUrl(patchParam,
              quranPageProvider.getImageVersion());
          promptForDownload();
          return;
        }
        runListView();
      }
    }
  }

  @WorkerThread
  private void copyArabicDatabaseIfNecessary() {
    // in 2.9.1 and above, the Arabic databases were renamed. To make it easier for
    // people to get them, the app will bundle them with the apk for a few releases.
    // if the database doesn't exist, let's try to copy it if we can. Only check this
    // if we have all the files, since if not, they come bundled with the full pages
    // zip file anyway. Note that, for now, this only applies for the madani app.

    //noinspection ConstantConditions
    if ("madani".equals(BuildConfig.FLAVOR) &&
        !quranFileUtils.hasArabicSearchDatabase(getApplicationContext())) {
      final boolean success =
          copyDatabaseUtil.copyArabicDatabaseFromAssets(QuranDataProvider.QURAN_ARABIC_DATABASE)
              .blockingGet();
      if (success) {
        Answers.getInstance()
            .logCustom(new CustomEvent("arabicDatabaseCopied")
                .putCustomAttribute("database", QuranDataProvider.QURAN_ARABIC_DATABASE));
      } else {
        Answers.getInstance()
            .logCustom(new CustomEvent("arabicDatabaseCopyFailed")
                .putCustomAttribute("database", QuranDataProvider.QURAN_ARABIC_DATABASE));
      }
    }
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
    if (downloadReceiver != null &&
        downloadReceiver.didReceiveBroadcast() && !force) {
      return;
    }
    if (isPaused) {
      return;
    }

    String url;
    if (needPortraitImages && !needLandscapeImages) {
      // phone (and tablet when upgrading on some devices, ex n10)
      url = quranFileUtils.getZipFileUrl();
    } else if (needLandscapeImages && !needPortraitImages) {
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
    if (!TextUtils.isEmpty(patchUrl)) {
      url = patchUrl;
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
    int message = R.string.downloadPrompt;
    if (quranScreenInfo.isDualPageMode() &&
        (needPortraitImages != needLandscapeImages)) {
      message = R.string.downloadTabletPrompt;
    }

    if (!TextUtils.isEmpty(patchUrl)) {
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
