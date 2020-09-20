package com.quran.labs.androidquran.ui.helpers;

import java.util.HashSet;
import java.util.Set;

public class SingleAyahHighlight extends AyahHighlight {

  public SingleAyahHighlight(String key) {
    super(key);
  }

  public SingleAyahHighlight(int surah, int ayah) {
    super(surah + ":" + ayah);
  }

  public static Set<AyahHighlight> createSet(Set<String> ayahKeys) {
    Set<AyahHighlight> set = new HashSet<>();
    for(String ayahKey: ayahKeys) {
      set.add(new SingleAyahHighlight(ayahKey));
    }
    return set;
  }
}
