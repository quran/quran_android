package com.quran.labs.androidquran.util;


import com.quran.labs.androidquran.data.QuranInfo;

import android.support.annotation.NonNull;
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
  public static boolean isGapless(@NonNull String pathName) {
    return Character.isLetter(pathName.charAt(0)) &&
        !"ydussary".equals(pathName);
  }

  private static String padSuraNumber(int number) {
    return number < 10 ? "00" + number :
        number < 100 ? "0" + number :
            String.valueOf(number);
  }

  private static Map<String, SheikhInfo> sCache = new ConcurrentHashMap<>();

  public static void clearCache() {
    sCache.clear();
  }

  public static void clearCacheKeyForSheikh(String path) {
    sCache.remove(path);
  }

  public static Observable<List<SheikhInfo>> shuyookhDownloadObservable(
      final String basePath, final String[] paths) {
    return Observable.from(paths)
        .flatMap(new Func1<String, Observable<SheikhInfo>>() {
          @Override
          public Observable<SheikhInfo> call(final String path) {
            SheikhInfo cached = sCache.get(path);
            if (cached != null) {
              return Observable.just(cached);
            }

            File baseFile = new File(basePath, path);
            return !baseFile.exists() ? Observable.just(new SheikhInfo(path)) :
                isGapless(path) ? getGaplessSheikhObservable(baseFile, path) :
                    getGappedSheikhObservable(baseFile, path);
          }
        })
        .doOnNext(new Action1<SheikhInfo>() {
          @Override
          public void call(SheikhInfo sheikhInfo) {
            sCache.put(sheikhInfo.path, sheikhInfo);
          }
        })
        .toList()
        .subscribeOn(Schedulers.io());
  }

  private static Observable<SheikhInfo> getGaplessSheikhObservable(
      final File path, final String sheikhPath) {
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
        .map(new Func1<List<Integer>, SheikhInfo>() {
          @Override
          public SheikhInfo call(List<Integer> suras) {
            return new SheikhInfo(sheikhPath, suras);
          }
        });
  }

  private static Observable<SheikhInfo> getGappedSheikhObservable(
      final File basePath, final String sheikhPath) {
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
      .map(new Func1<List<Pair<Integer, Boolean>>, SheikhInfo>() {
        @Override
        public SheikhInfo call(List<Pair<Integer, Boolean>> downloaded) {
          return SheikhInfo.withPartials(sheikhPath, downloaded);
        }
      });
  }
}
