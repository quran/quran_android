package com.quran.labs.androidquran.data;

public class QuranInfo extends BaseQuranInfo {

  public static int getJuzFromPage(int page) {
    int juz = ((page -3) / 20) + 1;
    if (page>=563 && page <587) juz = 29;
    if (page>=587) juz = 30;
    return juz > 30 ? 30 : juz < 1 ? 1 : juz;
  }
}
