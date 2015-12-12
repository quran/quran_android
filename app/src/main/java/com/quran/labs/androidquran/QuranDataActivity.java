package com.quran.labs.androidquran;

import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.quran.labs.androidquran.data.QuranFileConstants;
import com.quran.labs.androidquran.service.QuranDownloadService;
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver;
import com.quran.labs.androidquran.service.util.PermissionUtil;
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier;
import com.quran.labs.androidquran.service.util.ServiceIntentHelper;
import com.quran.labs.androidquran.ui.QuranActivity;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranSettings;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.widget.Toast;

import java.io.File;

public class QuranDataActivity extends Activity implements
    DefaultDownloadReceiver.SimpleDownloadListener,
    ActivityCompat.OnRequestPermissionsResultCallback {

  public static final String PAGES_DOWNLOAD_KEY = "PAGES_DOWNLOAD_KEY";

  private static final int LATEST_IMAGE_VERSION = QuranFileConstants.IMAGES_VERSION;
  private static final int REQUEST_WRITE_TO_SDCARD_PERMISSIONS = 1;

  private boolean mIsPaused = false;
  private AsyncTask<Void, Void, Boolean> mCheckPagesTask;
  private QuranSettings mQuranSettings;
  private AlertDialog mErrorDialog = null;
  private AlertDialog mPromptForDownloadDialog = null;
  private AlertDialog mPermissionsDialog;
  private DefaultDownloadReceiver mDownloadReceiver = null;
  private boolean mNeedPortraitImages = false;
  private boolean mNeedLandscapeImages = false;
  private boolean mTaskIsRunning;
  private String mPatchUrl;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    QuranScreenInfo.getOrMakeInstance(this);
    mQuranSettings = QuranSettings.getInstance(this);
    mQuranSettings.upgradePreferences();
  }

  @Override
  protected void onResume() {
    super.onResume();

    mIsPaused = false;
    mDownloadReceiver = new DefaultDownloadReceiver(this,
        QuranDownloadService.DOWNLOAD_TYPE_PAGES);
    mDownloadReceiver.setCanCancelDownload(true);
    String action = QuranDownloadNotifier.ProgressIntent.INTENT_NAME;
    LocalBroadcastManager.getInstance(this).registerReceiver(
        mDownloadReceiver,
        new IntentFilter(action));
    mDownloadReceiver.setListener(this);
    checkPermissions();
  }

  @Override
  protected void onPause() {
    mIsPaused = true;
    if (mDownloadReceiver != null) {
      mDownloadReceiver.setListener(null);
      LocalBroadcastManager.getInstance(this).
          unregisterReceiver(mDownloadReceiver);
      mDownloadReceiver = null;
    }

    if (mPromptForDownloadDialog != null) {
      mPromptForDownloadDialog.dismiss();
      mPromptForDownloadDialog = null;
    }

    if (mErrorDialog != null) {
      mErrorDialog.dismiss();
      mErrorDialog = null;
    }

    super.onPause();
  }

  private void checkPermissions() {
    final String path = mQuranSettings.getAppCustomLocation();
    final File fallbackFile = getExternalFilesDir(null);

    boolean usesExternalFileDir = path != null && path.contains("com.quran");
    if (path == null || usesExternalFileDir && fallbackFile == null) {
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
        mPermissionsDialog = new AlertDialog.Builder(this)
            .setMessage(R.string.storage_permission_rationale)
            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                mPermissionsDialog = null;

                Answers.getInstance().logCustom(
                    new CustomEvent("storagePermissionRationaleAccepted"));
                // request permissions
                requestExternalSdcardPermission();
              }
            })
            .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                // dismiss the dialog
                dialog.dismiss();
                mPermissionsDialog = null;

                Answers.getInstance().logCustom(
                    new CustomEvent("storagePermissionRationaleDenied"));
                // fall back if we can
                if (fallbackFile != null) {
                  mQuranSettings.setAppCustomLocation(fallbackFile.getAbsolutePath());
                  checkPages();
                } else {
                  // set to null so we can try again next launch
                  mQuranSettings.setAppCustomLocation(null);
                  runListView();
                }
              }
            })
            .create();
        mPermissionsDialog.show();
      } else {
        // fall back if we can
        if (fallbackFile != null) {
          mQuranSettings.setAppCustomLocation(fallbackFile.getAbsolutePath());
          checkPages();
        } else {
          // set to null so we can try again next launch
          mQuranSettings.setAppCustomLocation(null);
          runListView();
        }
      }
    } else {
      checkPages();
    }
  }

  private void checkPages() {
    if (mTaskIsRunning) {
      return;
    }

    mTaskIsRunning = true;

    // check whether or not we need to download
    mCheckPagesTask = new CheckPagesAsyncTask(this);
    mCheckPagesTask.execute();
  }

  private void requestExternalSdcardPermission() {
    ActivityCompat.requestPermissions(this,
        new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE },
        REQUEST_WRITE_TO_SDCARD_PERMISSIONS);
    mQuranSettings.setSdcardPermissionsDialogPresented();
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
      @NonNull int[] grantResults) {
    if (requestCode == REQUEST_WRITE_TO_SDCARD_PERMISSIONS) {
      if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        /**
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
          mQuranSettings.setAppCustomLocation(fallbackFile.getAbsolutePath());
          checkPages();
        } else {
          // set to null so we can try again next launch
          mQuranSettings.setAppCustomLocation(null);
          runListView();
        }
      }
    }
  }

  private boolean canWriteSdcardAfterPermissions() {
    String location = QuranFileUtils.getQuranBaseDirectory(this);
    if (location != null) {
      try {
        if (new File(location).exists() || QuranFileUtils.makeQuranDirectory(this)) {
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
    mQuranSettings.removeShouldFetchPages();
    runListView();
  }

  @Override
  public void handleDownloadFailure(int errId) {
    if (mErrorDialog != null && mErrorDialog.isShowing()) {
      return;
    }

    showFatalErrorDialog(errId);
  }

  private void showFatalErrorDialog(int errorId) {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setMessage(errorId);
    builder.setCancelable(false);
    builder.setPositiveButton(R.string.download_retry,
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int id) {
            dialog.dismiss();
            mErrorDialog = null;
            removeErrorPreferences();
            downloadQuranImages(true);
          }
        });

    builder.setNegativeButton(R.string.download_cancel,
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
            mErrorDialog = null;
            removeErrorPreferences();
            mQuranSettings.setShouldFetchPages(false);
            runListView();
          }
        });

    mErrorDialog = builder.create();
    mErrorDialog.show();
  }

  private void removeErrorPreferences() {
    mQuranSettings.clearLastDownloadError();
  }

  class CheckPagesAsyncTask extends AsyncTask<Void, Void, Boolean> {

    private final Context mAppContext;
    private String mPatchParam;
    private boolean mStorageNotAvailable;

    public CheckPagesAsyncTask(Context context) {
      mAppContext = context.getApplicationContext();
    }

    @Override
    protected Boolean doInBackground(Void... params) {
      final String baseDir = QuranFileUtils.getQuranBaseDirectory(mAppContext);
      if (baseDir == null) {
        mStorageNotAvailable = true;
        return false;
      }

      final QuranScreenInfo qsi = QuranScreenInfo.getInstance();
      if (!mQuranSettings.haveDefaultImagesDirectory()) {
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
            QuranFileUtils.getPotentialFallbackDirectory(mAppContext);
        if (fallback != null) {
          mQuranSettings.setDefaultImagesDirectory(fallback);
          qsi.setOverrideParam(fallback);
        }
      }

      final String width = qsi.getWidthParam();
      if (qsi.isTablet(mAppContext)) {
        final String tabletWidth = qsi.getTabletWidthParam();
        boolean haveLandscape = QuranFileUtils.haveAllImages(mAppContext, tabletWidth);
        boolean havePortrait = QuranFileUtils.haveAllImages(mAppContext, width);
        mNeedPortraitImages = !havePortrait;
        mNeedLandscapeImages = !haveLandscape;
        if (haveLandscape && havePortrait) {
          // if we have the images, see if we need a patch set or not
          if (!QuranFileUtils.isVersion(mAppContext, width, LATEST_IMAGE_VERSION) ||
              !QuranFileUtils.isVersion(mAppContext, tabletWidth, LATEST_IMAGE_VERSION)) {
            if (!width.equals(tabletWidth)) {
              mPatchParam = width + tabletWidth;
            } else {
              mPatchParam = width;
            }
          }
        }
        return haveLandscape && havePortrait;
      } else {
        boolean haveAll = QuranFileUtils.haveAllImages(mAppContext,
            QuranScreenInfo.getInstance().getWidthParam());
        mNeedPortraitImages = !haveAll;
        mNeedLandscapeImages = false;
        if (haveAll && !QuranFileUtils.isVersion(mAppContext, width, LATEST_IMAGE_VERSION)) {
          mPatchParam = width;
        }
        return haveAll;
      }
    }

    @Override
    protected void onPostExecute(@NonNull Boolean result) {
      mCheckPagesTask = null;
      mPatchUrl = null;
      mTaskIsRunning = false;

      if (mIsPaused) {
        return;
      }

      if (!result) {
        if (mStorageNotAvailable) {
          // no storage mounted, nothing we can do...
          runListView();
          return;
        }
        
        String lastErrorItem = mQuranSettings.getLastDownloadItemWithError();
        if (PAGES_DOWNLOAD_KEY.equals(lastErrorItem)) {
          int lastError = mQuranSettings.getLastDownloadErrorCode();
          int errorId = ServiceIntentHelper
              .getErrorResourceFromErrorCode(lastError, false);
          showFatalErrorDialog(errorId);
        } else if (mQuranSettings.shouldFetchPages()) {
          downloadQuranImages(false);
        } else {
          promptForDownload();
        }
      } else {
        if (!TextUtils.isEmpty(mPatchParam)) {
          mPatchUrl = QuranFileUtils.getPatchFileUrl(mPatchParam, LATEST_IMAGE_VERSION);
          promptForDownload();
          return;
        }
        runListView();
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
    if (mDownloadReceiver != null &&
        mDownloadReceiver.didReceiveBroadcast() && !force) {
      return;
    }
    if (mIsPaused) {
      return;
    }

    QuranScreenInfo qsi = QuranScreenInfo.getInstance();

    String url;
    if (mNeedPortraitImages && !mNeedLandscapeImages) {
      // phone (and tablet when upgrading on some devices, ex n10)
      url = QuranFileUtils.getZipFileUrl();
    } else if (mNeedLandscapeImages && !mNeedPortraitImages) {
      // tablet (when upgrading from pre-tablet on some devices, ex n7).
      url = QuranFileUtils.getZipFileUrl(qsi.getTabletWidthParam());
    } else {
      // new tablet installation - if both image sets are the same
      // size, then just get the correct one only
      if (qsi.getTabletWidthParam().equals(qsi.getWidthParam())) {
        url = QuranFileUtils.getZipFileUrl();
      } else {
        // otherwise download one zip with both image sets
        String widthParam = qsi.getWidthParam() +
            qsi.getTabletWidthParam();
        url = QuranFileUtils.getZipFileUrl(widthParam);
      }
    }

    // if we have a patch url, just use that
    if (!TextUtils.isEmpty(mPatchUrl)) {
      url = mPatchUrl;
    }

    String destination = QuranFileUtils.getQuranImagesBaseDirectory(QuranDataActivity.this);

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
    if (QuranScreenInfo.getInstance().isTablet(this) &&
        (mNeedPortraitImages != mNeedLandscapeImages)) {
      message = R.string.downloadTabletPrompt;
    }

    if (!TextUtils.isEmpty(mPatchUrl)) {
      // patch message if applicable
      message = R.string.downloadImportantPrompt;
    }

    AlertDialog.Builder dialog = new AlertDialog.Builder(this);
    dialog.setMessage(message);
    dialog.setCancelable(false);
    dialog.setPositiveButton(R.string.downloadPrompt_ok,
        new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int id) {
            dialog.dismiss();
            mPromptForDownloadDialog = null;
            mQuranSettings.setShouldFetchPages(true);
            downloadQuranImages(true);
          }
        });

    dialog.setNegativeButton(R.string.downloadPrompt_no,
        new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int id) {
            dialog.dismiss();
            mPromptForDownloadDialog = null;
            runListView();
          }
        });

    mPromptForDownloadDialog = dialog.create();
    mPromptForDownloadDialog.setTitle(R.string.downloadPrompt_title);
    mPromptForDownloadDialog.show();
  }

  protected void runListView(boolean showTranslations) {
    Intent i = new Intent(this, QuranActivity.class);
    if (showTranslations) {
      i.putExtra(QuranActivity.EXTRA_SHOW_TRANSLATION_UPGRADE, true);
    }
    startActivity(i);
    finish();
  }

  protected void runListView() {
    runListView(mQuranSettings.haveUpdatedTranslations());
  }
}
