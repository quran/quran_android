package com.quran.labs.androidquran.util;


import android.support.annotation.NonNull;
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
import io.reactivex.functions.Function;
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

  @NonNull
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
        .doOnNext(qariDownloadInfo -> sCache.put(qariDownloadInfo.qariItem, qariDownloadInfo))
        .toList()
        .subscribeOn(Schedulers.io());
  }

  @NonNull
  private static Single<QariDownloadInfo> getGaplessSheikhObservable(
      final File path, final QariItem qariItem) {
    return Observable.range(1, 114)
        .map(sura -> new SuraFileName(sura, new File(path, padSuraNumber(sura) + ".mp3")))
        .filter(sf -> sf.file.exists())
        .map(sf -> sf.sura)
        .toList()
        .map(suras -> new QariDownloadInfo(qariItem, suras));
  }

  @NonNull
  private static Single<QariDownloadInfo> getGappedSheikhObservable(
      final File basePath, final QariItem qariItem) {
    return Observable.range(1, 114)
        .map(sura -> new SuraFileName(sura, new File(basePath, String.valueOf(sura))))
        .filter(suraFile -> suraFile.file.exists())
        .map(sf -> new Pair<>(sf.sura,
            sf.file.listFiles().length >= QuranInfo.SURA_NUM_AYAHS[sf.sura - 1]))
        .toList()
        .map(downloaded -> QariDownloadInfo.withPartials(qariItem, downloaded));
  }

  private static class SuraFileName {
    public final int sura;
    public final File file;

    SuraFileName(int sura, File file) {
      this.sura = sura;
      this.file = file;
    }
  }
}
