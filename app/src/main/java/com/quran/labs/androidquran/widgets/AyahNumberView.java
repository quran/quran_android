package com.quran.labs.androidquran.widgets;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import com.quran.labs.androidquran.R;

import java.util.Locale;

public class AyahNumberView extends View {
  private int boxColor;
  private int nightBoxColor;
  private int boxWidth;
  private int boxHeight;
  private int textSize;
  private int textBoxPadding;
  private String suraAyah;
  private boolean isNightMode;
  private int verticalPadding;
  private int horizontalPadding;

  private Paint boxPaint;
  private TextPaint textPaint;
  private TextPaint actionTextPaint;
  private StaticLayout textLayout;
  private StaticLayout copyLayout;

  public AyahNumberView(Context context) {
    this(context, null);
  }

  public AyahNumberView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);

    int textColor = 0;
    int actionTextSize = 0;
    if (attrs != null) {
      TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.AyahNumberView);
      textColor = ta.getColor(R.styleable.AyahNumberView_android_textColor, textColor);
      boxColor = ta.getColor(R.styleable.AyahNumberView_backgroundColor, boxColor);
      nightBoxColor = ta.getColor(R.styleable.AyahNumberView_nightBackgroundColor, nightBoxColor);
      boxWidth = ta.getDimensionPixelSize(R.styleable.AyahNumberView_verseBoxWidth, boxWidth);
      boxHeight = ta.getDimensionPixelSize(R.styleable.AyahNumberView_verseBoxHeight, boxHeight);
      textSize = ta.getDimensionPixelSize(R.styleable.AyahNumberView_android_textSize, textSize);
      textBoxPadding = ta.getDimensionPixelSize(
          R.styleable.AyahNumberView_boxPadding, textBoxPadding);
      actionTextSize = ta.getDimensionPixelSize(
          R.styleable.AyahNumberView_actionFontSize, textSize);
      ta.recycle();
    }

    boxPaint = new Paint();
    boxPaint.setColor(boxColor);
    textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    textPaint.setColor(textColor);
    textPaint.setTextSize(textSize);
    actionTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    actionTextPaint.setColor(textColor);
    actionTextPaint.setTextSize(actionTextSize);
  }

  public void setAyahString(@NonNull String suraAyah) {
    if (!suraAyah.equals(this.suraAyah)) {
      this.suraAyah = suraAyah;
      this.textLayout = new StaticLayout(suraAyah, textPaint, boxWidth,
          Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
      invalidate();
    }
  }

  public void setNightMode(boolean isNightMode) {
    if (this.isNightMode != isNightMode) {
      boxPaint.setColor(isNightMode ? nightBoxColor : boxColor);
      this.isNightMode = isNightMode;
      invalidate();
    }
  }

  public void setTextColor(int textColor) {
    textPaint.setColor(textColor);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    if (copyLayout == null) {
      String copyString = getContext().getString(R.string.copy)
          .toUpperCase(Locale.getDefault());
      copyLayout = new StaticLayout(copyString, actionTextPaint, getMeasuredWidth(),
          Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
    }
    verticalPadding = (getMeasuredHeight() -
        (boxHeight + copyLayout.getHeight() + textBoxPadding)) / 2;
    horizontalPadding = (getMeasuredWidth() - boxWidth) / 2;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    canvas.drawRect(horizontalPadding, verticalPadding,
        horizontalPadding + boxWidth, verticalPadding + boxHeight, boxPaint);
    if (this.textLayout != null) {
      int startY = verticalPadding + ((boxHeight - this.textLayout.getHeight()) / 2);
      canvas.translate(horizontalPadding, startY);
      this.textLayout.draw(canvas);
      canvas.translate(-horizontalPadding, -startY);
    }

    if (copyLayout != null) {
      int startY = verticalPadding + boxHeight + textBoxPadding;
      canvas.translate(0, startY);
      this.copyLayout.draw(canvas);
      canvas.translate(0, -startY);
    }
  }
}
