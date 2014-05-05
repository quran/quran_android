package com.quran.labs.androidquran.ui.helpers;

import com.quran.labs.androidquran.widgets.HighlightingImageView;

public interface AyahSelectedListener {

  public enum EventType {SINGLE_TAP, LONG_PRESS, DOUBLE_TAP}

  /** Return true to receive the ayah info along with the
   * click event, false to receive just the event type */
  public boolean isListeningForAyahSelection(EventType eventType);

  /** Click event with ayah info and highlighter passed */
  public boolean onAyahSelected(EventType eventType,
          int sura, int ayah, int page, HighlightingImageView hv);

  /** General click event without ayah info */
  public boolean onClick(EventType eventType);

}
