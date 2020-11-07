package com.quran.labs.androidquran.ui.helpers;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.quran.labs.androidquran.R;

public class HighlightType implements Comparable<HighlightType> {

  public static final HighlightType SELECTION = new HighlightType(1, false, R.color.selection_highlight, HighlightAnimationConfig.NONE);
  public static final HighlightType AUDIO =     new HighlightType(2, false, R.color.audio_highlight,     HighlightAnimationConfig.AUDIO);
  public static final HighlightType NOTE =      new HighlightType(3, true,  R.color.note_highlight,      HighlightAnimationConfig.NONE);
  public static final HighlightType BOOKMARK =  new HighlightType(4, true,  R.color.bookmark_highlight,  HighlightAnimationConfig.NONE);

  private Long id;
  private boolean multipleHighlightsAllowed;
  private int colorId;
  private Integer color = null;
  private HighlightAnimationConfig animationConfig;

  private HighlightType(long id, boolean multipleHighlightsAllowed, int colorId, HighlightAnimationConfig config) {
    this.id = id;
    this.multipleHighlightsAllowed = multipleHighlightsAllowed;
    this.colorId = colorId;
    this.animationConfig = config;
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

  public boolean isFloatable() {
    return animationConfig.isFloatable();
  }

  public HighlightAnimationConfig getAnimationConfig() {
    return animationConfig;
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
