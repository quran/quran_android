package com.quran.labs.androidquran.ui.helpers;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.quran.labs.androidquran.R;

public class HighlightType implements Comparable<HighlightType> {

  public static final HighlightType SELECTION = new HighlightType(1, false, R.color.selection_highlight);
  public static final HighlightType AUDIO =     new HighlightType(2, false, R.color.audio_highlight);
  public static final HighlightType NOTE =      new HighlightType(3, true,  R.color.note_highlight);
  public static final HighlightType BOOKMARK =  new HighlightType(4, true,  R.color.bookmark_highlight);

  private Long id;
  private boolean multipleHighlightsAllowed;
  private int colorId;
  private Integer color = null;

  private HighlightType(long id, boolean multipleHighlightsAllowed, int colorId) {
    this.id = id;
    this.multipleHighlightsAllowed = multipleHighlightsAllowed;
    this.colorId = colorId;
  }

  public boolean isMultipleHighlightsAllowed() {
    return multipleHighlightsAllowed;
  }

  public int getColor(Context context) {
    if (color == null) {
      color = ContextCompat.getColor(context, colorId);
    }
    return color;
  }

  @Override
  public int compareTo(@NonNull HighlightType another) {
    return id.compareTo(another.id);
  }

  @Override
  public boolean equals(Object o) {
    return this == o || (o instanceof HighlightType && id.equals(((HighlightType) o).id));
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

}
