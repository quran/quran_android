package com.quran.page.common.data;

import android.graphics.RectF;

public class AyahBounds {
  private final int line;
  private final int position;
  private RectF bounds;

  public AyahBounds(int line, int position, int minX, int minY, int maxX, int maxY) {
    this(line, position, new RectF(minX, minY, maxX, maxY));
  }

  public AyahBounds(int line, int position, RectF bounds) {
    this.line = line;
    this.position = position;
    this.bounds = bounds;
  }

  public void engulf(AyahBounds other) {
    bounds.union(other.getBounds());
  }

  public RectF getBounds() {
    return new RectF(bounds);
  }

  public AyahBounds withBounds(RectF bounds) {
    return new AyahBounds(line, position, bounds);
  }

  public int getLine() {
    return line;
  }

  public int getPosition() {
    return position;
  }
}
