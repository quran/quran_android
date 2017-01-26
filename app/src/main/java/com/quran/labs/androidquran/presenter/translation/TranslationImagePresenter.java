package com.quran.labs.androidquran.presenter.translation;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.view.View;

import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.di.QuranPageScope;
import com.quran.labs.androidquran.model.quran.CoordinatesModel;
import com.quran.labs.androidquran.presenter.Presenter;
import com.quran.labs.androidquran.ui.translation.AyahImageView;
import com.quran.labs.androidquran.ui.translation.TranslationView;
import com.quran.labs.androidquran.util.QuranFileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import io.reactivex.Maybe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.observers.DisposableMaybeObserver;
import io.reactivex.schedulers.Schedulers;

@QuranPageScope
public class TranslationImagePresenter implements Presenter<TranslationView> {

  private final int page;
  private final String path;
  private final CoordinatesModel coordinatesModel;
  private final CompositeDisposable compositeDisposable;
  private final Map<View, Disposable> map;

  private volatile BitmapRegionDecoder regionDecoder;
  private volatile Map<String, List<AyahBounds>> pageCoordinates;

  @Inject
  TranslationImagePresenter(Context context, CoordinatesModel coordinatesModel, Integer... pages) {
    this.page = pages[0];
    this.coordinatesModel = coordinatesModel;
    this.compositeDisposable = new CompositeDisposable();
    this.map = new HashMap<>();
    this.path = QuranFileUtils.getQuranImagesDirectory(context);
  }

  public void loadImageForAyah(AyahImageView imageView, View placeholderView, int sura, int ayah) {
    placeholderView.setVisibility(View.VISIBLE);
    imageView.setVisibility(View.GONE);

    Disposable disposable = map.get(imageView);
    if (disposable != null) {
      disposable.dispose();
    }
    map.put(imageView, loadImage(imageView, placeholderView, sura, ayah));
  }

  private Disposable loadImage(AyahImageView imageView, View placeholderView, int sura, int ayah) {
    return Maybe.zip(getCoordinates(sura, ayah), getBitmapRegionDecoder(),
        (ayahBounds, bitmapDecoder) -> {
          BitmapFactory.Options options = new BitmapFactory.Options();
          options.inPreferredConfig = Bitmap.Config.ALPHA_8;

          List<Rect> rects = new ArrayList<>();
          for (int i = 0, size = ayahBounds.size(); i < size; i++) {
            if (i == 0 || i == size - 1) {
              rects.add(ayahBounds.get(i).getBoundsAsRect());
            } else {
              Rect rect = ayahBounds.get(i).getBoundsAsRect();
              for (i = i + 1; i < size - 1; i++) {
                AyahBounds b = ayahBounds.get(i);
                rect.union(b.getBoundsAsRect());
              }
              rects.add(rect);
              i--;
            }
          }

          List<Bitmap> bitmaps = new ArrayList<>();
          for (int i = 0, size = rects.size(); i < size; i++) {
            Rect bounds = rects.get(i);
            bitmaps.add(bitmapDecoder.decodeRegion(bounds, options));
          }
          return bitmaps;
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeWith(new DisposableMaybeObserver<List<Bitmap>>() {
          @Override
          public void onSuccess(List<Bitmap> bitmap) {
            placeholderView.setVisibility(View.GONE);
            imageView.setBitmaps(bitmap);
            imageView.setVisibility(View.VISIBLE);
          }

          @Override
          public void onError(Throwable e) {
          }

          @Override
          public void onComplete() {
            map.remove(imageView);
          }
        });
  }

  private Maybe<List<AyahBounds>> getCoordinates(int sura, int ayah) {
    final String key = sura + ":" + ayah;
    return this.pageCoordinates != null ?
        Maybe.just(pageCoordinates.get(key)) :
        coordinatesModel.getAyahCoordinates(page)
            .doOnNext(result -> this.pageCoordinates = result.second)
            .map(results -> results.second.get(key))
            .firstElement();
  }

  private Maybe<BitmapRegionDecoder> getBitmapRegionDecoder() {
    return this.regionDecoder != null ?
        Maybe.just(regionDecoder) :
        Maybe.defer(() -> {
          BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(getPathName(), true);
          return decoder == null ? Maybe.empty() : Maybe.just(decoder);
        })
        .doAfterSuccess(decoder -> this.regionDecoder = decoder);
  }

  private String getPathName() {
    return this.path + File.separator + QuranFileUtils.getPageFileName(this.page);
  }

  @Override
  public void bind(TranslationView what) {
  }

  @Override
  public void unbind(TranslationView what) {
    compositeDisposable.dispose();
  }
}
