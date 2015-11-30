package com.quran.labs.androidquran;

import com.quran.labs.androidquran.dao.BookmarkData;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.model.BookmarkImportExportModel;
import com.quran.labs.androidquran.service.util.PermissionUtil;
import com.quran.labs.androidquran.util.QuranSettings;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;

import okio.BufferedSource;
import okio.Okio;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class QuranImportActivity extends AppCompatActivity {
  private final int REQUEST_WRITE_TO_SDCARD_PERMISSIONS = 1;

  private static Observable<Boolean> sImportObservable;

  private AlertDialog mDialog;
  private Subscription mSubscription;
  private BookmarkImportExportModel mBookmarkImportExportModel;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBookmarkImportExportModel = new BookmarkImportExportModel(this);

    if (sImportObservable != null) {
      mSubscription = subscribeToImport();
    } else {
      handleIntent(getIntent());
    }
  }

  private void handleIntent(Intent intent) {
    Uri uri = intent.getData();
    if (uri == null) {
      uri = (Uri) intent.getExtras().get(Intent.EXTRA_STREAM);
    }

    if (uri != null) {
      parseIntentUri(uri);
    } else {
      showErrorInternal();
    }
  }

  @Override
  protected void onDestroy() {
    if (mSubscription != null) {
      mSubscription.unsubscribe();
    }

    if (mDialog != null) {
      mDialog.dismiss();
    }
    super.onDestroy();
  }

  private void handleFileParsed(final BookmarkData bookmarkData) {
    String message = getString(R.string.import_data_and_override,
        bookmarkData.getBookmarks().size(),
        bookmarkData.getTags().size());
    AlertDialog.Builder builder = new AlertDialog.Builder(this)
        .setMessage(message)
        .setPositiveButton(R.string.import_data, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            // import and finish
            sImportObservable =
                Observable.fromCallable(new Callable<Boolean>() {
                  @Override
                  public Boolean call() throws Exception {
                    BookmarksDBAdapter adapter = new BookmarksDBAdapter(getApplicationContext());
                    return adapter.importBookmarks(bookmarkData);
                  }
                })
                .subscribeOn(Schedulers.io())
                .cache();
            mSubscription = subscribeToImport();
          }
        })
        .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            finish();
          }
        });
    mDialog = builder.show();
  }

  private Subscription subscribeToImport() {
    return sImportObservable
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Action1<Boolean>() {
      @Override
      public void call(Boolean aBoolean) {
        Toast.makeText(QuranImportActivity.this,
            R.string.import_successful, Toast.LENGTH_LONG).show();
        sImportObservable = null;
        finish();
      }
    });
  }

  private void showErrorInternal() {
    showErrorInternal(R.string.import_data_error);
  }

  private void showPermissionsError() {
    showErrorInternal(R.string.import_data_permissions_error);
  }

  private void showErrorInternal(int messageId) {
    AlertDialog.Builder builder = new AlertDialog.Builder(this)
        .setMessage(messageId)
        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            finish();
          }
        });
    mDialog = builder.show();
  }

  private void parseIntentUri(final Uri uri) {
    mSubscription = getBookmarkDataObservable(parseUri(uri))
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Action1<BookmarkData>() {
          @Override
          public void call(BookmarkData bookmarkData) {
            if (bookmarkData != null) {
              handleFileParsed(bookmarkData);
            } else {
              handleExternalStorageFile(uri);
            }
          }
        });
  }

  private void handleExternalStorageFile(Uri uri) {
    if (PermissionUtil.haveWriteExternalStoragePermission(this)) {
      handleExternalStorageFileInternal(uri);
    } else if (PermissionUtil.canRequestWriteExternalStoragePermission(this)) {
      ActivityCompat.requestPermissions(this,
          new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE },
          REQUEST_WRITE_TO_SDCARD_PERMISSIONS);
      QuranSettings.getInstance(this).setSdcardPermissionsDialogPresented();
    } else {
      showPermissionsError();
    }
  }

  private void handleExternalStorageFileInternal(Uri uri) {
    mSubscription = getBookmarkDataObservable(parseExternalFile(uri))
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Action1<BookmarkData>() {
          @Override
          public void call(BookmarkData bookmarkData) {
            if (bookmarkData != null) {
              handleFileParsed(bookmarkData);
            } else {
              showErrorInternal();
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

  @NonNull
  private Observable<BufferedSource> parseUri(final Uri uri) {
    return Observable.fromCallable(new Callable<BufferedSource>() {
      @Override
      public BufferedSource call() throws Exception {
        try {
          ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
          if (pfd != null) {
            FileDescriptor fd = pfd.getFileDescriptor();
            return Okio.buffer(Okio.source(new FileInputStream(fd)));
          }
        } catch (IOException ioe) {
          // log this at some point
        }
        return null;
      }
    });
  }

  @NonNull
  private Observable<BufferedSource> parseExternalFile(final Uri uri) {
    return Observable.fromCallable(new Callable<BufferedSource>() {
      @Override
      public BufferedSource call() throws Exception {
        try {
          InputStream stream = getContentResolver().openInputStream(uri);
          if (stream != null) {
            return Okio.buffer(Okio.source(stream));
          }
        } catch (IOException ioe) {
          // log at some point
        }
        return null;
      }
    });
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
      @NonNull int[] grantResults) {
    if (requestCode == REQUEST_WRITE_TO_SDCARD_PERMISSIONS) {
      if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        handleIntent(getIntent());
      } else {
        showPermissionsError();
      }
    }
  }
}
