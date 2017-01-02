package com.quran.labs.androidquran.widgets;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.annotation.ColorInt;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

import com.quran.labs.androidquran.R;

public class DividerView extends View {
  private int dividerColor;
  private int dividerHeight;
  private final Paint paint;

  private int y;
  private int highlightedColor;
  private boolean isLineVisible;

  public DividerView(Context context) {
    this(context, null);
  }

  public DividerView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);

    if (attrs != null) {
      TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.DividerView);
      dividerColor = ta.getColor(R.styleable.DividerView_dividerColor, dividerColor);
      dividerHeight = ta.getDimensionPixelSize(
          R.styleable.DividerView_dividerHeight, dividerHeight);
      ta.recycle();
    }

    paint = new Paint();
    paint.setColor(dividerColor);
    isLineVisible = true;
  }

  public void toggleLine(boolean showLine) {
    if (isLineVisible != showLine) {
      isLineVisible = showLine;
      invalidate();
    }
  }

  public void setDividerColor(int color) {
    if (dividerColor != color) {
      dividerColor = color;
      paint.setColor(color);
      invalidate();
    }
  }

  public void highlight(@ColorInt int color) {
    if (color != highlightedColor) {
      highlightedColor = color;
      invalidate();
    }
  }

  public void unhighlight() {
    if (highlightedColor != 0) {
      highlightedColor = 0;
      invalidate();
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    y = getMeasuredHeight() - dividerHeight;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    if (highlightedColor != 0) {
      canvas.drawColor(highlightedColor);
    }

    if (isLineVisible) {
      canvas.drawRect(0, y, getWidth(), y + dividerHeight, paint);
    }
  }
}
