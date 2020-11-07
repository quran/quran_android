package com.quran.labs.androidquran.ui.helpers;

import android.animation.TypeEvaluator;
import android.graphics.RectF;

import com.quran.page.common.data.AyahBounds;

import java.util.ArrayList;
import java.util.List;

public class HighlightAnimationTypeEvaluator implements TypeEvaluator<List<AyahBounds>> {

  HighlightNormalizingStrategy normalizingStrategy;
  public HighlightAnimationTypeEvaluator(HighlightNormalizingStrategy strategy) {
    normalizingStrategy = strategy;
  }

  @Override
  public List<AyahBounds> evaluate(float fraction, List<AyahBounds> start, List<AyahBounds> end) {
    normalizingStrategy.apply(start, end);

    int size = start.size();

    // return a new result object to avoid data race with onAnimationUpdate
    List<AyahBounds> result = new ArrayList<>(size);

    for(int i=0; i<size; ++i) {
      RectF startValue = start.get(i).getBounds();
      RectF endValue = end.get(i).getBounds();
      float left = startValue.left + (endValue.left - startValue.left) * fraction;
      float top = startValue.top + (endValue.top - startValue.top) * fraction;
      float right = startValue.right + (endValue.right - startValue.right) * fraction;
      float bottom = startValue.bottom + (endValue.bottom - startValue.bottom) * fraction;
      AyahBounds intermediateBounds = new AyahBounds(0,0, new RectF(left, top, right, bottom));
      result.add(intermediateBounds);
    }
    return result;
  }
}
