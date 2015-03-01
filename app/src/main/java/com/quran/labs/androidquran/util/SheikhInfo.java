package com.quran.labs.androidquran.util;

import android.util.Pair;
import android.util.SparseBooleanArray;

import java.util.Collections;
import java.util.List;

public class SheikhInfo {

  public String path;
  public SparseBooleanArray partialSuras;
  public SparseBooleanArray downloadedSuras;

  public SheikhInfo(String path) {
    this(path, Collections.<Integer>emptyList());
  }

  public static SheikhInfo withPartials(String path,
      List<Pair<Integer, Boolean>> suras) {
    SheikhInfo info = new SheikhInfo(path, Collections.<Integer>emptyList());
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

  public SheikhInfo(String path, List<Integer> suras) {
    this.path = path;
    this.partialSuras = new SparseBooleanArray();
    this.downloadedSuras = new SparseBooleanArray();
    for (Integer sura : suras) {
      this.downloadedSuras.put(sura, true);
    }
  }
}
