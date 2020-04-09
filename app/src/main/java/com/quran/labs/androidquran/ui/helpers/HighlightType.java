package com.quran.labs.androidquran.ui.helpers;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.quran.labs.androidquran.R;

public class HighlightType implements Comparable<HighlightType> {

  public static final HighlightType SELECTION = new HighlightType(1, false, R.color.selection_highlight, false);
  public static final HighlightType AUDIO =     new HighlightType(2, false, R.color.audio_highlight,     true);
  public static final HighlightType NOTE =      new HighlightType(3, true,  R.color.note_highlight,      false);
  public static final HighlightType BOOKMARK =  new HighlightType(4, true,  R.color.bookmark_highlight,  false);

  private Long id;
  private boolean multipleHighlightsAllowed;
  private int colorId;
  private Integer color = null;
  private boolean floatable; 
  // TODO: replace this with HighlightAnimationConfig
  // which contains:
  // 1. duration of animation
  // 2. under what circumstances to animate, given ayahs
  // 3. type of interpolator etc.
  // 4. animation rule - The mapping of bounds (if ayah A has 3 bounds and B has 2 bounds, how to take care of the extra one?)
  // I am not doing this right now because this seems
  // over-engineering to me, given that animation takes place just for Audio and that too for consecutive ayahs

  private HighlightType(long id, boolean multipleHighlightsAllowed, int colorId, boolean floatable) {
    this.id = id;
    this.multipleHighlightsAllowed = multipleHighlightsAllowed;
    this.colorId = colorId;
    this.floatable = floatable;
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
    return this.floatable;
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
