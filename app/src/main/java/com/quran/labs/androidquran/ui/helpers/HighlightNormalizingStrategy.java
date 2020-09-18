package com.quran.labs.androidquran.ui.helpers;

import android.graphics.RectF;

import com.quran.page.common.data.AyahBounds;

import java.util.List;

public abstract class HighlightNormalizingStrategy {
  /*
  The parameters are passed by reference, they will be modified
   */
  public abstract void normalize(List<AyahBounds> start, List<AyahBounds> end);
  public abstract boolean isNormalized(List<AyahBounds> start, List<AyahBounds> end);
  void apply(List<AyahBounds> start, List<AyahBounds> end) {
    if(isNormalized(start, end)) {
      return;
    }
    normalize(start, end);
  }
}

class NormalizeToMaxAyahBoundsWithDivisionStrategy extends HighlightNormalizingStrategy {

  /*
  Say we are going from a list of x AyahBounds to a list of y AyahBounds
  Then normalizing algorithm is as follows:

  1. a = min(x,y); b = max(x, y)
  2. diff = b.length - a.length
  3. split a[-1] into (diff + 1) parts equally
  4. animate x[i] to y[i] for i in 0 to b.length-1
   */

  @Override
  public void normalize(List<AyahBounds> start, List<AyahBounds> end) {
    int startSize = start.size();
    int endSize = end.size();
    int minSize = Math.min(startSize, endSize);
    int maxSize = Math.max(startSize, endSize);
    List<AyahBounds> minList = startSize < endSize? start : end;
    int diff = maxSize - minSize;

    RectF rectToBeDivided = minList.get(minSize-1).getBounds();
    float originalLeft = rectToBeDivided.left;
    float originalRight = rectToBeDivided.right;
    float originalTop = rectToBeDivided.top;
    float originalBottom = rectToBeDivided.bottom;
    minList.remove(minSize-1);
    float part = (originalRight-originalLeft) /(diff+1);
    for(int i=0; i<(diff+1); ++i) {
      float left = originalLeft + part*i;
      float right = left + part;
      RectF rect = new RectF(left, originalTop, right, originalBottom);
      AyahBounds ayahBounds = new AyahBounds(0, 0, rect);
      minList.add(ayahBounds);
    }
  }

  @Override
  public boolean isNormalized(List<AyahBounds> start, List<AyahBounds> end) {
    return start.size() == end.size();
  }

}

class NormalizeToMinAyahBoundsWithGrowingDivisionStrategy extends NormalizeToMaxAyahBoundsWithDivisionStrategy {

  /*
  Say we are going from a list of x AyahBounds to a list of y AyahBounds
  Then normalizing algorithm is as follows:

  1. diff = max(x, y).length - min(x,y).length
  2. if x < y then, split x[-1] into (diff + 1) parts equally (Growing: use division strategy)
  3. else delete x[0..diff-1] (Shrinking: use deletion strategy)
  4. animate x[i] to y[i] for i in 0 to x.length-1
   */

  @Override
  public void normalize(List<AyahBounds> start, List<AyahBounds> end) {
    int startSize = start.size();
    int endSize = end.size();

    if(startSize >= endSize) {
      int diff = Math.abs(startSize - endSize);
      List toBeDeleted = start.subList(0, diff);
      toBeDeleted.clear();
    } else {
      super.normalize(start, end);
    }
  }
}
