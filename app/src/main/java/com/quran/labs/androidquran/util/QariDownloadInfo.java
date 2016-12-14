package com.quran.labs.androidquran.util;

import android.util.Pair;
import android.util.SparseBooleanArray;

import com.quran.labs.androidquran.common.QariItem;

import java.util.Collections;
import java.util.List;

public class QariDownloadInfo {

  public final QariItem qariItem;
  public SparseBooleanArray downloadedSuras;
  private SparseBooleanArray partialSuras;

  QariDownloadInfo(QariItem item) {
    this(item, Collections.<Integer>emptyList());
  }

  QariDownloadInfo(QariItem item, List<Integer> suras) {
    this.qariItem = item;
    this.partialSuras = new SparseBooleanArray();
    this.downloadedSuras = new SparseBooleanArray();
    for (int i = 0, surasSize = suras.size(); i < surasSize; i++) {
      Integer sura = suras.get(i);
      this.downloadedSuras.put(sura, true);
    }
  }

  static QariDownloadInfo withPartials(QariItem item, List<Pair<Integer, Boolean>> suras) {
    QariDownloadInfo info = new QariDownloadInfo(item, Collections.emptyList());
    for (int i = 0, surasSize = suras.size(); i < surasSize; i++) {
      Pair<Integer, Boolean> sura = suras.get(i);
      if (sura.second) {
        info.downloadedSuras.put(sura.first, true);
      } else {
        info.partialSuras.put(sura.first, true);
      }
    }
    return info;
  }
}
