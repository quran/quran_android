package com.quran.labs.androidquran.ui.helpers;

/**
 * Activity or fragment implements this is meant to be a jump destination/target.
 */
public interface JumpDestination {
  void jumpTo(int page);

  void jumpToAndHighlight(int page, int sura, int ayah);
}
