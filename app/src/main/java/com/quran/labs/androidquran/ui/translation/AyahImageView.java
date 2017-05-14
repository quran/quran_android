package com.quran.labs.androidquran.ui.translation;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class AyahImageView extends View {
  private final static Rect TMP_RECT = new Rect();

  private final List<Bitmap> bitmaps = new ArrayList<>();
  private final List<Integer> drawingRows = new ArrayList<>();
  private float scaleFactor;

  public AyahImageView(Context context) {
    super(context);
  }

  public AyahImageView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public AyahImageView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public void setBitmaps(List<Bitmap> bitmaps) {
    this.bitmaps.clear();
    this.bitmaps.addAll(bitmaps);
    invalidate();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    int totalWidth = MeasureSpec.getSize(widthMeasureSpec) -
        (getPaddingRight() + getPaddingLeft());

    int maxWidthFound = 0;
    for (int i = 0, size = bitmaps.size(); i < size; i++) {
      Bitmap bitmap = bitmaps.get(i);
      maxWidthFound = Math.max(maxWidthFound, bitmap.getWidth());
    }

    scaleFactor = 1.0f;
    if (maxWidthFound > totalWidth) {
      scaleFactor = (1.0f * totalWidth) / (1.0f * maxWidthFound);
    }

    int height = 0;
    this.drawingRows.clear();
    int usedWidth = 0;
    int row = -1;
    for (int i = 0, size = bitmaps.size(); i < size; i++) {
      Bitmap bitmap = bitmaps.get(i);
      if (usedWidth == 0 || usedWidth + (bitmap.getWidth() * scaleFactor) > totalWidth) {
        usedWidth = bitmap.getWidth();
        height += bitmap.getHeight();
        this.drawingRows.add(++row);
      } else {
        usedWidth = usedWidth + bitmap.getWidth();
        this.drawingRows.add(row);
      }
    }

    setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (int) (height * scaleFactor));
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    int width = getMeasuredWidth() - getPaddingRight();
    if (scaleFactor != 1.0f) {
      canvas.save();
      canvas.scale(scaleFactor, scaleFactor);
      canvas.getClipBounds(TMP_RECT);
      width = TMP_RECT.right - getPaddingRight();
    }

    int x = width;
    int y = 0;
    int row = 0;
    Bitmap lastBitmap = null;
    for (int i = 0, size = bitmaps.size(); i < size; i++) {
      int currentRow = drawingRows.get(i);
      Bitmap bitmap = bitmaps.get(i);
      if (currentRow != row) {
        x = width;
        y += lastBitmap != null ? lastBitmap.getHeight() : 0;
        row = currentRow;
      }
      lastBitmap = bitmap;
      x = x - bitmap.getWidth();
      canvas.drawBitmap(bitmap, x, y, null);
    }

    if (scaleFactor != 1.0f) {
      canvas.restore();
    }
  }
}
