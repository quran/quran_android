package com.quran.labs.androidquran.ui.helpers;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.quran.labs.androidquran.R;

public class HighlightType implements Comparable<HighlightType> {

  public static final HighlightType SELECTION = new HighlightType(1, false, R.color.selection_highlight, Mode.HIGHLIGHT, HighlightAnimationConfig.NONE);
  public static final HighlightType AUDIO =     new HighlightType(2, false, R.color.audio_highlight,     Mode.HIGHLIGHT, HighlightAnimationConfig.AUDIO);
  public static final HighlightType NOTE =      new HighlightType(3, true,  R.color.note_highlight,      Mode.HIGHLIGHT, HighlightAnimationConfig.NONE);
  public static final HighlightType BOOKMARK =  new HighlightType(4, true,  R.color.bookmark_highlight,  Mode.HIGHLIGHT, HighlightAnimationConfig.NONE);

  private final Long id;
  private final boolean multipleHighlightsAllowed;
  private final int colorId;
  private final Mode mode;
  private final HighlightAnimationConfig animationConfig;

  private Integer color = null;

  public enum Mode {
    HIGHLIGHT,  // Highlights the text of the ayah (rectangular overlay on the text)
    BACKGROUND, // Applies a background color to the entire line (full height/width, even ayahs that are centered like first 2 pages)
    UNDERLINE,  // Draw an underline below the text of the ayah
    COLOR,      // Change the text color of the ayah/word (apply a color filter)
    HIDE        // Hide the ayah/word (i.e. won't be rendered)
  }

  private HighlightType(long id, boolean multipleHighlightsAllowed, int colorId, Mode mode, HighlightAnimationConfig config) {
    this.id = id;
    this.multipleHighlightsAllowed = multipleHighlightsAllowed;
    this.colorId = colorId;
    this.mode = mode;
    this.animationConfig = config;
  }

  public boolean shouldExpandHorizontally() {
    return mode == Mode.BACKGROUND;
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

  @NonNull
  public Mode getMode() {
    return mode;
  }

  public boolean isFloatable() {
    return animationConfig.isFloatable();
  }

  public boolean hasAnimation() {
    return animationConfig != HighlightAnimationConfig.NONE;
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
