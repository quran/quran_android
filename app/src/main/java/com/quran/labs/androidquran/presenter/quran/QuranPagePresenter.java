package com.quran.labs.androidquran.presenter.quran;


import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.support.v4.util.Pair;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.common.Response;
import com.quran.labs.androidquran.dao.Bookmark;
import com.quran.labs.androidquran.model.bookmark.BookmarkModel;
import com.quran.labs.androidquran.model.quran.CoordinatesModel;
import com.quran.labs.androidquran.presenter.Presenter;
import com.quran.labs.androidquran.ui.helpers.PageDownloadListener;
import com.quran.labs.androidquran.ui.helpers.QuranPageWorker;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranSettings;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.observers.DisposableObserver;

public class QuranPagePresenter implements Presenter<QuranPageScreen>, PageDownloadListener {

  private final boolean isTabletMode;
  private final BookmarkModel bookmarkModel;
  private final CoordinatesModel coordinatesModel;
  private final CompositeDisposable compositeDisposable;
  private final QuranSettings quranSettings;
  private final QuranPageWorker quranPageWorker;
  private final String widthParameter;
  private final Integer[] pages;
  private final Future[] pageTasks;

  private QuranPageScreen screen;
  private boolean encounteredError;

  public QuranPagePresenter(BookmarkModel bookmarkModel,
                            CoordinatesModel coordinatesModel,
                            QuranSettings quranSettings,
                            QuranScreenInfo quranScreenInfo,
                            QuranPageWorker quranPageWorker,
                            boolean isTabletMode,
                            Integer... pages) {
    this.isTabletMode = isTabletMode;
    this.bookmarkModel = bookmarkModel;
    this.quranSettings = quranSettings;
    this.coordinatesModel = coordinatesModel;
    this.widthParameter = isTabletMode ?
        quranScreenInfo.getTabletWidthParam() : quranScreenInfo.getWidthParam();
    this.quranPageWorker = quranPageWorker;
    this.compositeDisposable = new CompositeDisposable();
    this.pages = pages;
    this.pageTasks = new Future[pages.length];
  }

  private void getPageCoordinates(Integer... pages) {
    compositeDisposable.add(
        Completable.timer(500, TimeUnit.MILLISECONDS)
            .andThen(coordinatesModel.getPageCoordinates(isTabletMode, pages))
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeWith(new DisposableObserver<Pair<Integer, RectF>>() {
              @Override
              public void onNext(Pair<Integer, RectF> pageBounds) {
                if (screen != null) {
                  screen.setPageCoordinates(pageBounds.first, pageBounds.second);
                }
              }

              @Override
              public void onError(Throwable e) {
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
              public void onNext(List<Bookmark> bookmarks) {
                if (screen != null) {
                  screen.setBookmarksOnPage(bookmarks);
                }
              }

              @Override
              public void onError(Throwable e) {
              }

              @Override
              public void onComplete() {
              }
            }));
  }

  private void getAyahCoordinates(Integer... pages) {
    compositeDisposable.add(
        Observable.fromArray(pages)
            .flatMap(p -> coordinatesModel.getAyahCoordinates(isTabletMode, p))
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeWith(new DisposableObserver<Pair<Integer, Map<String, List<AyahBounds>>>>() {
              @Override
              public void onNext(Pair<Integer, Map<String, List<AyahBounds>>> coordinates) {
                if (screen != null) {
                  screen.setAyahCoordinatesData(coordinates.first, coordinates.second);
                }
              }

              @Override
              public void onError(Throwable e) {
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
    for (int i = 0; i < pages.length; i++) {
      if (pageTasks[i] != null && !pageTasks[i].isDone()) {
        pageTasks[i].cancel(true);
      }
      pageTasks[i] = quranPageWorker.loadPage(widthParameter, pages[i], this);
    }
  }

  @Override
  public void onLoadImageResponse(BitmapDrawable drawable, Response response) {
    if (this.screen != null) {
      if (drawable != null) {
        screen.setPageImage(response.getPageNumber(), drawable);
      } else {
        final int errorCode = response.getErrorCode();
        final int errorRes;
        switch (errorCode) {
          case Response.ERROR_SD_CARD_NOT_FOUND:
            errorRes = R.string.sdcard_error;
            break;
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

  public void refresh() {
    if (encounteredError) {
      encounteredError = false;
      getPageCoordinates(pages);
    }
  }

  @Override
  public void bind(QuranPageScreen screen) {
    this.screen = screen;
    downloadImages();
    getPageCoordinates(pages);
  }

  @Override
  public void unbind(QuranPageScreen screen) {
    this.screen = null;
    compositeDisposable.dispose();
    for (Future future : pageTasks) {
      if (future != null && !future.isDone()) {
        future.cancel(true);
      }
    }
  }
}
