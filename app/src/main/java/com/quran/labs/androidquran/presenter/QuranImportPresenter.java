package com.quran.labs.androidquran.presenter;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.support.v4.app.ActivityCompat;

import com.quran.labs.androidquran.QuranImportActivity;
import com.quran.labs.androidquran.dao.BookmarkData;
import com.quran.labs.androidquran.model.bookmark.BookmarkImportExportModel;
import com.quran.labs.androidquran.model.bookmark.BookmarkModel;
import com.quran.labs.androidquran.service.util.PermissionUtil;
import com.quran.labs.androidquran.util.QuranSettings;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.concurrent.Callable;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.Maybe;
import io.reactivex.MaybeSource;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Function;
import io.reactivex.observers.DisposableMaybeObserver;
import io.reactivex.schedulers.Schedulers;
import okio.BufferedSource;
import okio.Okio;


@Singleton
public class QuranImportPresenter implements Presenter<QuranImportActivity> {
  private static final int REQUEST_WRITE_TO_SDCARD_PERMISSIONS = 1;

  private final Context mAppContext;
  private final BookmarkModel mBookmarkModel;
  private final BookmarkImportExportModel mBookmarkImportExportModel;

  private boolean mRequestingPermissions;
  private Observable<Boolean> mImportObservable;
  private QuranImportActivity mCurrentActivity;

  @Inject
  QuranImportPresenter(Context appContext,
                       BookmarkImportExportModel model,
                       BookmarkModel bookmarkModel) {
    mAppContext = appContext;
    mBookmarkModel = bookmarkModel;
    mBookmarkImportExportModel = model;
  }

  private void handleIntent(Intent intent) {
    mRequestingPermissions = false;
    if (mImportObservable == null) {
      Uri uri = intent.getData();
      if (uri == null && intent.getExtras() != null) {
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
    mImportObservable = mBookmarkModel.importBookmarksObservable(data);
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
        .subscribe(aBoolean -> {
          if (mCurrentActivity != null) {
            mCurrentActivity.showImportComplete();
            mImportObservable = null;
          }
        });
  }

  private void parseIntentUri(final Uri uri) {
    getBookmarkDataObservable(parseUri(uri))
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new DisposableMaybeObserver<BookmarkData>() {
          @Override
          public void onSuccess(BookmarkData bookmarkData) {
            if (mCurrentActivity != null) {
              mCurrentActivity.showImportConfirmationDialog(bookmarkData);
            }
          }

          @Override
          public void onError(Throwable e) {
            if (mCurrentActivity != null) {
              handleExternalStorageFile(uri);
            }
          }

          @Override
          public void onComplete() {
            if (mCurrentActivity != null) {
              handleExternalStorageFile(uri);
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
        .subscribe(new DisposableMaybeObserver<BookmarkData>() {
          @Override
          public void onSuccess(BookmarkData bookmarkData) {
            if (mCurrentActivity != null) {
              mCurrentActivity.showImportConfirmationDialog(bookmarkData);
            }
          }

          @Override
          public void onError(Throwable e) {
            if (mCurrentActivity != null) {
              mCurrentActivity.showError();
            }
          }

          @Override
          public void onComplete() {
            if (mCurrentActivity != null) {
              mCurrentActivity.showError();
            }
          }
        });
  }

  private Maybe<BookmarkData> getBookmarkDataObservable(Maybe<BufferedSource> source) {
    return source
        .flatMap(new Function<BufferedSource, MaybeSource<BookmarkData>>() {
          @Override
          public MaybeSource<BookmarkData> apply(BufferedSource bufferedSource) throws Exception {
            return mBookmarkImportExportModel.readBookmarks(bufferedSource).toMaybe();
          }
        })
        .subscribeOn(Schedulers.io());
  }

  @NonNull
  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  Maybe<BufferedSource> parseUri(final Uri uri) {
    return Maybe.defer(new Callable<MaybeSource<BufferedSource>>() {
      @Override
      public MaybeSource<BufferedSource> call() throws Exception {
        ParcelFileDescriptor pfd = mAppContext.getContentResolver().openFileDescriptor(uri, "r");
        if (pfd != null) {
          FileDescriptor fd = pfd.getFileDescriptor();
          return Maybe.just(Okio.buffer(Okio.source(new FileInputStream(fd))));
        }
        return Maybe.empty();
      }
    });
  }

  @NonNull
  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  Maybe<BufferedSource> parseExternalFile(final Uri uri) {
    return Maybe.defer(new Callable<MaybeSource<BufferedSource>>() {
      @Override
      public MaybeSource<BufferedSource> call() throws Exception {
        InputStream stream = mAppContext.getContentResolver().openInputStream(uri);
        if (stream != null) {
          return Maybe.just(Okio.buffer(Okio.source(stream)));
        }
        return Maybe.empty();
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
