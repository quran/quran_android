package com.quran.labs.androidquran.util;


import android.util.Pair;

import com.quran.labs.androidquran.common.QariItem;
import com.quran.labs.androidquran.data.QuranInfo;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Single;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.schedulers.Schedulers;


public class AudioManagerUtils {

  private static String padSuraNumber(int number) {
    return number < 10 ? "00" + number :
        number < 100 ? "0" + number :
            String.valueOf(number);
  }

  private static Map<QariItem, QariDownloadInfo> sCache = new ConcurrentHashMap<>();

  public static void clearCache() {
    sCache.clear();
  }

  public static void clearCacheKeyForSheikh(QariItem qariItem) {
    sCache.remove(qariItem);
  }

  public static Single<List<QariDownloadInfo>> shuyookhDownloadObservable(
      final String basePath, List<QariItem> qariItems) {
    return Observable.fromIterable(qariItems)
        .flatMap(new Function<QariItem, ObservableSource<QariDownloadInfo>>() {
          @Override
          public ObservableSource<QariDownloadInfo> apply(QariItem item) throws Exception {
            QariDownloadInfo cached = sCache.get(item);
            if (cached != null) {
              return Observable.just(cached);
            }

            File baseFile = new File(basePath, item.getPath());
            return !baseFile.exists() ? Observable.just(new QariDownloadInfo(item)) :
                item.isGapless() ? getGaplessSheikhObservable(baseFile, item).toObservable() :
                    getGappedSheikhObservable(baseFile, item).toObservable();
          }
        })
        .doOnNext(new Consumer<QariDownloadInfo>() {
          @Override
          public void accept(QariDownloadInfo qariDownloadInfo) {
            sCache.put(qariDownloadInfo.mQariItem, qariDownloadInfo);
          }
        })
        .toList()
        .subscribeOn(Schedulers.io());
  }

  private static Single<QariDownloadInfo> getGaplessSheikhObservable(
      final File path, final QariItem qariItem) {
    return Observable.range(1, 114)
        .map(new Function<Integer, Integer>() {
          @Override
          public Integer apply(Integer sura) {
            return new File(path, padSuraNumber(sura) + ".mp3").exists() ? sura : null;
          }
        })
        .filter(new Predicate<Integer>() {
          @Override
          public boolean test(Integer integer) {
            return integer != null;
          }
        })
        .toList()
        .map(new Function<List<Integer>, QariDownloadInfo>() {
          @Override
          public QariDownloadInfo apply(List<Integer> suras) {
            return new QariDownloadInfo(qariItem, suras);
          }
        });
  }

  private static Single<QariDownloadInfo> getGappedSheikhObservable(
      final File basePath, final QariItem qariItem) {
    return Observable.range(1, 114)
      .map(new Function<Integer, Pair<Integer, Boolean>>() {
        @Override
        public Pair<Integer, Boolean> apply(Integer sura) {
          final File path = new File(basePath, String.valueOf(sura));
          return !path.exists() ? null :
              new Pair<>(sura, path.listFiles().length >= QuranInfo.SURA_NUM_AYAHS[sura - 1]);
        }
      })
      .toList()
      .map(new Function<List<Pair<Integer, Boolean>>, QariDownloadInfo>() {
        @Override
        public QariDownloadInfo apply(List<Pair<Integer, Boolean>> downloaded) {
          return QariDownloadInfo.withPartials(qariItem, downloaded);
        }
      });
  }
}
