package com.quran.labs.androidquran.util;

import com.quran.labs.androidquran.common.QariItem;

import android.util.Pair;
import android.util.SparseBooleanArray;

import java.util.Collections;
import java.util.List;

public class QariDownloadInfo {

  public final QariItem mQariItem;
  public SparseBooleanArray partialSuras;
  public SparseBooleanArray downloadedSuras;

  public QariDownloadInfo(QariItem item) {
    this(item, Collections.<Integer>emptyList());
  }

  public QariDownloadInfo(QariItem item, List<Integer> suras) {
    this.mQariItem = item;
    this.partialSuras = new SparseBooleanArray();
    this.downloadedSuras = new SparseBooleanArray();
    for (Integer sura : suras) {
      this.downloadedSuras.put(sura, true);
    }
  }

  public static QariDownloadInfo withPartials(QariItem item, List<Pair<Integer, Boolean>> suras) {
    QariDownloadInfo info = new QariDownloadInfo(item, Collections.<Integer>emptyList());
    for (Pair<Integer, Boolean> sura : suras) {
      if (sura != null) {
        if (sura.second) {
          info.downloadedSuras.put(sura.first, true);
        } else {
          info.partialSuras.put(sura.first, true);
        }
      }
    }
    return info;
  }
}
