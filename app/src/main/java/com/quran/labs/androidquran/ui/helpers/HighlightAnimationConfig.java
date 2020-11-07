package com.quran.labs.androidquran.ui.helpers;

import android.animation.TimeInterpolator;
import android.animation.TypeEvaluator;
import android.view.animation.AccelerateDecelerateInterpolator;

public class HighlightAnimationConfig {
  public static final HighlightAnimationConfig AUDIO = new HighlightAnimationConfig(
      500,
      new HighlightAnimationTypeEvaluator(
//          new NormalizeToMaxAyahBoundsWithDivisionStrategy()
          new NormalizeToMinAyahBoundsWithGrowingDivisionStrategy()
      ),
      new AccelerateDecelerateInterpolator());
  public static final HighlightAnimationConfig NONE = new HighlightAnimationConfig();

  private boolean floatable;
  private int duration;
  private TypeEvaluator typeEvaluator;
  private TimeInterpolator interpolator;

  public HighlightAnimationConfig(int duration, TypeEvaluator evaluator, TimeInterpolator interpolator) {
    this.floatable = true;
    this.duration = duration;
    this.typeEvaluator = evaluator;
    this.interpolator = interpolator;
  }

  public HighlightAnimationConfig() {
    this.floatable = false;
  }

  public boolean isFloatable() {
    return this.floatable;
  }

  public int getDuration() {
    return this.duration;
  }

  public TypeEvaluator getTypeEvaluator() {
    return this.typeEvaluator;
  }

  public TimeInterpolator getInterpolator() {
    return this.interpolator;
  }
}

class AudioHighlightAnimationConfig extends HighlightAnimationConfig {
  @Override
  public boolean isFloatable() {
    return true;
  }
}
