package com.quran.labs.androidquran.presenter.quran;


import android.graphics.RectF;
import android.support.v4.util.Pair;

import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.dao.Bookmark;
import com.quran.labs.androidquran.model.bookmark.BookmarkModel;
import com.quran.labs.androidquran.model.quran.CoordinatesModel;
import com.quran.labs.androidquran.presenter.Presenter;
import com.quran.labs.androidquran.util.QuranSettings;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.observers.DisposableObserver;

public class QuranPagePresenter implements Presenter<QuranPageScreen> {

  private final boolean isTabletMode;
  private final BookmarkModel bookmarkModel;
  private final CoordinatesModel coordinatesModel;
  private final CompositeDisposable compositeDisposable;
  private final QuranSettings quranSettings;
  private final Integer[] pages;

  private QuranPageScreen screen;

  public QuranPagePresenter(BookmarkModel bookmarkModel,
                            CoordinatesModel coordinatesModel,
                            QuranSettings quranSettings,
                            boolean isTabletMode,
                            Integer... pages) {
    this.isTabletMode = isTabletMode;
    this.bookmarkModel = bookmarkModel;
    this.quranSettings = quranSettings;
    this.coordinatesModel = coordinatesModel;
    this.compositeDisposable = new CompositeDisposable();
    this.pages = pages;
  }

  private void getPageCoordinates(Integer... pages) {
    compositeDisposable.add(
        Completable.timer(1, TimeUnit.SECONDS)
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
              }

              @Override
              public void onComplete() {
              }
            }));
  }

  private void getBookmarkedAyahs(Integer... pages) {
    compositeDisposable.add(
        Completable.timer(250, TimeUnit.MILLISECONDS)
            .andThen(bookmarkModel.getBookmarkedAyahsOnPageObservable(pages)
                .observeOn(AndroidSchedulers.mainThread()))
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
                getAyahCoordinates(false, pages);
              }
            }));
  }

  private void getAyahCoordinates(boolean delay, Integer... pages) {
    Completable completable = delay ?
        Completable.timer(1, TimeUnit.SECONDS) : Completable.complete();
    compositeDisposable.add(
        completable.andThen(
            Observable.fromArray(pages)
                .flatMap(p -> coordinatesModel.getAyahCoordinates(isTabletMode, p)))
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
                if (screen != null) {
                  screen.setAyahCoordinatesError();
                }
              }

              @Override
              public void onComplete() {
              }
            })
    );
  }

  public void refresh() {
    getAyahCoordinates(false, pages);
    getPageCoordinates(pages);
  }

  @Override
  public void bind(QuranPageScreen screen) {
    this.screen = screen;
    if (quranSettings.shouldHighlightBookmarks()) {
      getBookmarkedAyahs(pages);
    } else {
      getAyahCoordinates(true, pages);
    }
    getPageCoordinates(pages);
  }

  @Override
  public void unbind(QuranPageScreen screen) {
    this.screen = null;
    compositeDisposable.dispose();
  }
}
