package com.quran.labs.androidquran.util;


import com.quran.labs.androidquran.common.QariItem;
import com.quran.labs.androidquran.data.QuranInfo;

import android.util.Pair;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

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

  public static Observable<List<QariDownloadInfo>> shuyookhDownloadObservable(
      final String basePath, List<QariItem> qariItems) {
    return Observable.from(qariItems)
        .flatMap(new Func1<QariItem, Observable<QariDownloadInfo>>() {
          @Override
          public Observable<QariDownloadInfo> call(final QariItem item) {
            QariDownloadInfo cached = sCache.get(item);
            if (cached != null) {
              return Observable.just(cached);
            }

            File baseFile = new File(basePath, item.getPath());
            return !baseFile.exists() ? Observable.just(new QariDownloadInfo(item)) :
                item.isGapless() ? getGaplessSheikhObservable(baseFile, item) :
                    getGappedSheikhObservable(baseFile, item);
          }
        })
        .doOnNext(new Action1<QariDownloadInfo>() {
          @Override
          public void call(QariDownloadInfo qariDownloadInfo) {
            sCache.put(qariDownloadInfo.mQariItem, qariDownloadInfo);
          }
        })
        .toList()
        .subscribeOn(Schedulers.io());
  }

  private static Observable<QariDownloadInfo> getGaplessSheikhObservable(
      final File path, final QariItem qariItem) {
    return Observable.range(1, 114)
        .map(new Func1<Integer, Integer>() {
          @Override
          public Integer call(Integer sura) {
            return new File(path, padSuraNumber(sura) + ".mp3").exists() ? sura : null;
          }
        })
        .filter(new Func1<Integer, Boolean>() {
          @Override
          public Boolean call(Integer integer) {
            return integer != null;
          }
        })
        .toList()
        .map(new Func1<List<Integer>, QariDownloadInfo>() {
          @Override
          public QariDownloadInfo call(List<Integer> suras) {
            return new QariDownloadInfo(qariItem, suras);
          }
        });
  }

  private static Observable<QariDownloadInfo> getGappedSheikhObservable(
      final File basePath, final QariItem qariItem) {
    return Observable.range(1, 114)
      .map(new Func1<Integer, Pair<Integer, Boolean>>() {
        @Override
        public Pair<Integer, Boolean> call(Integer sura) {
          final File path = new File(basePath, String.valueOf(sura));
          return !path.exists() ? null :
              new Pair<>(sura, path.listFiles().length >= QuranInfo.SURA_NUM_AYAHS[sura - 1]);
        }
      })
      .toList()
      .map(new Func1<List<Pair<Integer, Boolean>>, QariDownloadInfo>() {
        @Override
        public QariDownloadInfo call(List<Pair<Integer, Boolean>> downloaded) {
          return QariDownloadInfo.withPartials(qariItem, downloaded);
        }
      });
  }
}
