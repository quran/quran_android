package com.quran.labs.androidquran.view;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.util.AttributeSet;

import com.quran.labs.androidquran.R;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;

public class RepeatButton extends AppCompatImageView {
  @NonNull private String text;
  @NonNull private TextPaint paint;
  private boolean canDraw;
  private int viewWidth;
  private int viewHeight;
  private int textXPosition;
  private int textYPosition;
  private int textYPadding;

  public RepeatButton(Context context) {
    this(context, null);
  }

  public RepeatButton(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public RepeatButton(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(Color.WHITE);
    final Resources resources = context.getResources();
    paint.setTextSize(resources.getDimensionPixelSize(R.dimen.repeat_superscript_text_size));
    textYPadding = resources.getDimensionPixelSize(R.dimen.repeat_text_y_padding);
    text = "";
  }

  public void setText(@NonNull String text) {
    this.text = text;
    updateCoordinates();
    invalidate();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    viewWidth = getMeasuredWidth();
    viewHeight = getMeasuredHeight();
    updateCoordinates();
  }

  private void updateCoordinates() {
    canDraw = false;
    final Drawable drawable = getDrawable();
    if (drawable != null) {
      final Rect bounds = drawable.getBounds();
      if (bounds.width() > 0) {
        textXPosition = viewWidth - (viewWidth - bounds.width()) / 2;
        textYPosition = textYPadding + (viewHeight - bounds.height()) / 2;
        canDraw = true;
      }
    }
  }

  @Override
  protected void onDraw(@NonNull Canvas canvas) {
    super.onDraw(canvas);

    final int length = text.length();
    if (canDraw && length > 0) {
      canvas.drawText(text, 0, length, textXPosition, textYPosition, paint);
    }
  }
}
