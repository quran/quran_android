package com.quran.labs.androidquran.common;

import android.graphics.RectF;

public class AyahBounds {
  private int line;
  private int position;
  private RectF bounds;

  public AyahBounds(int line, int position, int minX, int minY, int maxX, int maxY) {
    this.line = line;
    this.position = position;
    bounds = new RectF(minX, minY, maxX, maxY);
  }

  public void engulf(AyahBounds other) {
    bounds.union(other.getBounds());
  }

  public RectF getBounds() {
    return new RectF(bounds);
  }

  public int getLine() {
    return line;
  }

  public int getPosition() {
    return position;
  }
}
