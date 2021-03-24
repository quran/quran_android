package com.quran.labs.androidquran.presenter.quran;


import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.Response;
import com.quran.data.model.bookmark.Bookmark;
import com.quran.labs.androidquran.di.QuranPageScope;
import com.quran.labs.androidquran.model.bookmark.BookmarkModel;
import com.quran.labs.androidquran.model.quran.CoordinatesModel;
import com.quran.labs.androidquran.presenter.Presenter;
import com.quran.labs.androidquran.ui.helpers.QuranPageLoader;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.page.common.data.AyahCoordinates;
import com.quran.page.common.data.PageCoordinates;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.observers.DisposableObserver;

@QuranPageScope
public class QuranPagePresenter implements Presenter<QuranPageScreen> {

  private final BookmarkModel bookmarkModel;
  private final CoordinatesModel coordinatesModel;
  private final CompositeDisposable compositeDisposable;
  private final QuranSettings quranSettings;
  private final QuranPageLoader quranPageLoader;
  private final Integer[] pages;

  private QuranPageScreen screen;
  private boolean encounteredError;
  private boolean didDownloadImages;

  @Inject
  QuranPagePresenter(BookmarkModel bookmarkModel,
                     CoordinatesModel coordinatesModel,
                     QuranSettings quranSettings,
                     QuranPageLoader quranPageLoader,
                     Integer... pages) {
    this.bookmarkModel = bookmarkModel;
    this.quranSettings = quranSettings;
    this.coordinatesModel = coordinatesModel;
    this.quranPageLoader = quranPageLoader;
    this.compositeDisposable = new CompositeDisposable();
    this.pages = pages;
  }

  private void getPageCoordinates(Integer... pages) {
    compositeDisposable.add(
        coordinatesModel.getPageCoordinates(quranSettings.shouldOverlayPageInfo(), pages)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeWith(new DisposableObserver<PageCoordinates>() {
              @Override
              public void onNext(@NonNull PageCoordinates pageCoordinates) {
                if (screen != null) {
                  screen.setPageCoordinates(pageCoordinates);
                }
              }

              @Override
              public void onError(@NonNull Throwable e) {
                encounteredError = true;
                if (screen != null) {
                  screen.setAyahCoordinatesError();
                }
              }

              @Override
              public void onComplete() {
                getAyahCoordinates(pages);
              }
            }));
  }

  private void getBookmarkedAyahs(Integer... pages) {
    compositeDisposable.add(
        bookmarkModel.getBookmarkedAyahsOnPageObservable(pages)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeWith(new DisposableObserver<List<Bookmark>>() {

              @Override
              public void onNext(@NonNull List<Bookmark> bookmarks) {
                if (screen != null) {
                  screen.setBookmarksOnPage(bookmarks);
                }
              }

              @Override
              public void onError(@NonNull Throwable e) {
              }

              @Override
              public void onComplete() {
              }
            }));
  }

  private void getAyahCoordinates(Integer... pages) {
    compositeDisposable.add(
        Completable.timer(500, TimeUnit.MILLISECONDS)
            .andThen(Observable.fromArray(pages))
            .flatMap(coordinatesModel::getAyahCoordinates)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeWith(new DisposableObserver<AyahCoordinates>() {
              @Override
              public void onNext(@NonNull AyahCoordinates coordinates) {
                if (screen != null) {
                  screen.setAyahCoordinatesData(coordinates);
                }
              }

              @Override
              public void onError(@NonNull Throwable e) {
              }

              @Override
              public void onComplete() {
                if (quranSettings.shouldHighlightBookmarks()) {
                  getBookmarkedAyahs(pages);
                }
              }
            })
    );
  }

  public void downloadImages() {
    screen.hidePageDownloadError();
    compositeDisposable.add(
        quranPageLoader.loadPages(pages)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeWith(new DisposableObserver<Response>() {
          @Override
          public void onNext(@NonNull Response response) {
            if (screen != null) {
              Bitmap bitmap = response.getBitmap();
              if (bitmap != null) {
                didDownloadImages = true;
                screen.setPageBitmap(response.getPageNumber(), bitmap);
              } else {
                didDownloadImages = false;
                final int errorCode = response.getErrorCode();
                final int errorRes;
                switch (errorCode) {
                  case Response.ERROR_SD_CARD_NOT_FOUND:
                    errorRes = R.string.sdcard_error;
                    break;
                  case Response.ERROR_NO_INTERNET:
                  case Response.ERROR_DOWNLOADING_ERROR:
                    errorRes = R.string.download_error_network;
                    break;
                  default:
                    errorRes = R.string.download_error_general;
                }
                screen.setPageDownloadError(errorRes);
              }
            }
          }

          @Override
          public void onError(@NonNull Throwable e) {
          }

          @Override
          public void onComplete() {
          }
        }));
  }

  public void refresh() {
    if (encounteredError) {
      encounteredError = false;
      getPageCoordinates(pages);
    }
  }

  @Override
  public void bind(QuranPageScreen screen) {
    this.screen = screen;
    if (!didDownloadImages) {
      downloadImages();
    }
    getPageCoordinates(pages);
  }

  @Override
  public void unbind(QuranPageScreen screen) {
    this.screen = null;
    compositeDisposable.clear();
  }
}
