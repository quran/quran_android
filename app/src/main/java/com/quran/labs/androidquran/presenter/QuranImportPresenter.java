package com.quran.labs.androidquran.presenter;

import com.quran.labs.androidquran.QuranImportActivity;
import com.quran.labs.androidquran.dao.BookmarkData;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.model.bookmark.BookmarkImportExportModel;
import com.quran.labs.androidquran.service.util.PermissionUtil;
import com.quran.labs.androidquran.util.QuranSettings;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.support.v4.app.ActivityCompat;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;

import okio.BufferedSource;
import okio.Okio;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class QuranImportPresenter implements Presenter<QuranImportActivity> {
  private static final int REQUEST_WRITE_TO_SDCARD_PERMISSIONS = 1;

  private static QuranImportPresenter sInstance;

  private final Context mAppContext;
  private final BookmarkImportExportModel mBookmarkImportExportModel;

  private boolean mRequestingPermissions;
  private Observable<Boolean> mImportObservable;
  private QuranImportActivity mCurrentActivity;

  public static synchronized QuranImportPresenter getInstance(Context context) {
    if (sInstance == null) {
      sInstance = new QuranImportPresenter(context);
    }
    return sInstance;
  }

  private QuranImportPresenter(Context context) {
    mAppContext = context.getApplicationContext();
    mBookmarkImportExportModel = new BookmarkImportExportModel(mAppContext);
  }

  @VisibleForTesting
  QuranImportPresenter(Context appContext, BookmarkImportExportModel bookmarkImportExportModel) {
    mAppContext = appContext;
    mBookmarkImportExportModel = bookmarkImportExportModel;
  }

  public void handleIntent(Intent intent) {
    mRequestingPermissions = false;
    if (mImportObservable == null) {
      Uri uri = intent.getData();
      if (uri == null) {
        uri = (Uri) intent.getExtras().get(Intent.EXTRA_STREAM);
      }

      if (uri != null) {
        parseIntentUri(uri);
      } else if (mCurrentActivity != null) {
        mCurrentActivity.showError();
      }
    } else {
      subscribeToImportData();
    }
  }

  public void importData(final BookmarkData data) {
    mImportObservable =
        Observable.fromCallable(new Callable<Boolean>() {
          @Override
          public Boolean call() throws Exception {
            BookmarksDBAdapter adapter = new BookmarksDBAdapter(mAppContext);
            return adapter.importBookmarks(data);
          }
        })
        .subscribeOn(Schedulers.io())
        .cache();
    subscribeToImportData();
  }

  public void onPermissionsResult(int requestCode, @NonNull int[] grantResults) {
    if (requestCode == REQUEST_WRITE_TO_SDCARD_PERMISSIONS) {
      mRequestingPermissions = false;
      if (mCurrentActivity != null) {
        if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
          handleIntent(mCurrentActivity.getIntent());
        } else {
          mCurrentActivity.showPermissionsError();
        }
      }
    }
  }

  private void subscribeToImportData() {
    mImportObservable
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Action1<Boolean>() {
          @Override
          public void call(Boolean aBoolean) {
            if (mCurrentActivity != null) {
              mCurrentActivity.showImportComplete();
              mImportObservable = null;
            }
          }
        });
  }

  private void parseIntentUri(final Uri uri) {
    getBookmarkDataObservable(parseUri(uri))
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Subscriber<BookmarkData>() {
          @Override
          public void onCompleted() {
          }

          @Override
          public void onError(Throwable e) {
            if (mCurrentActivity != null) {
              handleExternalStorageFile(uri);
            }
          }

          @Override
          public void onNext(BookmarkData bookmarkData) {
            if (mCurrentActivity != null) {
              mCurrentActivity.showImportConfirmationDialog(bookmarkData);
            }
          }
        });
  }

  private void handleExternalStorageFile(Uri uri) {
    if (PermissionUtil.haveWriteExternalStoragePermission(mAppContext)) {
      handleExternalStorageFileInternal(uri);
    } else if (mCurrentActivity != null) {
      mRequestingPermissions = true;
      if (PermissionUtil.canRequestWriteExternalStoragePermission(mCurrentActivity)) {
        QuranSettings.getInstance(mAppContext).setSdcardPermissionsDialogPresented();
        ActivityCompat.requestPermissions(mCurrentActivity,
            new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE },
            REQUEST_WRITE_TO_SDCARD_PERMISSIONS);
      } else {
        mCurrentActivity.showPermissionsError();
      }
    }
  }

  private void handleExternalStorageFileInternal(Uri uri) {
    getBookmarkDataObservable(parseExternalFile(uri))
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Subscriber<BookmarkData>() {
          @Override
          public void onCompleted() {
          }

          @Override
          public void onError(Throwable e) {
            if (mCurrentActivity != null) {
              mCurrentActivity.showError();
            }
          }

          @Override
          public void onNext(BookmarkData bookmarkData) {
            if (mCurrentActivity != null) {
              mCurrentActivity.showImportConfirmationDialog(bookmarkData);
            }
          }
        });
  }

  private Observable<BookmarkData> getBookmarkDataObservable(Observable<BufferedSource> source) {
    return source
        .flatMap(new Func1<BufferedSource, Observable<BookmarkData>>() {
          @Override
          public Observable<BookmarkData> call(BufferedSource bufferedSource) {
            return bufferedSource == null ? Observable.<BookmarkData>just(null) :
                mBookmarkImportExportModel.readBookmarks(bufferedSource);
          }
        })
        .subscribeOn(Schedulers.io());
  }

  @NonNull @VisibleForTesting
  Observable<BufferedSource> parseUri(final Uri uri) {
    return Observable.defer(new Func0<Observable<BufferedSource>>() {
      @Override
      public Observable<BufferedSource> call() {
        try {
          ParcelFileDescriptor pfd = mAppContext.getContentResolver().openFileDescriptor(uri, "r");
          if (pfd != null) {
            FileDescriptor fd = pfd.getFileDescriptor();
            return Observable.just(Okio.buffer(Okio.source(new FileInputStream(fd))));
          }
          return Observable.just(null);
        } catch (IOException ioe) {
          return Observable.error(ioe);
        } catch (NullPointerException npe) {
          return Observable.error(npe);
        }
      }
    });
  }

  @NonNull @VisibleForTesting
  Observable<BufferedSource> parseExternalFile(final Uri uri) {
    return Observable.defer(new Func0<Observable<BufferedSource>>() {
      @Override
      public Observable<BufferedSource> call() {
        try {
          InputStream stream = mAppContext.getContentResolver().openInputStream(uri);
          if (stream != null) {
            return Observable.just(Okio.buffer(Okio.source(stream)));
          }
        } catch (IOException ioe) {
          return Observable.error(ioe);
        }
        return Observable.just(null);
      }
    });
  }

  @Override
  public void bind(QuranImportActivity activity) {
    mCurrentActivity = activity;
    if (!activity.isShowingDialog() && !mRequestingPermissions) {
      handleIntent(activity.getIntent());
    }
  }

  @Override
  public void unbind(QuranImportActivity activity) {
    if (activity == mCurrentActivity) {
      mCurrentActivity = null;
    }
  }
}
