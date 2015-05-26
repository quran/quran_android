package com.quran.labs.androidquran.widgets;

import com.quran.labs.androidquran.R;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.widget.ImageView;

public class RepeatButton extends ImageView {
  @NonNull private String mText;
  @NonNull private TextPaint mPaint;
  private boolean mCanDraw;
  private int mViewWidth;
  private int mViewHeight;
  private int mTextXPosition;
  private int mTextYPosition;
  private int mTextYPadding;

  public RepeatButton(Context context) {
    this(context, null);
  }

  public RepeatButton(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public RepeatButton(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    mPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    mPaint.setColor(Color.WHITE);
    final Resources resources = context.getResources();
    mPaint.setTextSize(resources.getDimensionPixelSize(R.dimen.repeat_superscript_text_size));
    mTextYPadding = resources.getDimensionPixelSize(R.dimen.repeat_text_y_padding);
    mText = "";
  }

  public void setText(@NonNull String text) {
    mText = text;
    updateCoordinates();
    invalidate();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    mViewWidth = getMeasuredWidth();
    mViewHeight = getMeasuredHeight();
    updateCoordinates();
  }

  private void updateCoordinates() {
    mCanDraw = false;
    final Drawable drawable = getDrawable();
    if (drawable != null) {
      final Rect bounds = drawable.getBounds();
      if (bounds != null && bounds.width() > 0) {
        mTextXPosition = mViewWidth - (mViewWidth - bounds.width()) / 2;
        mTextYPosition = mTextYPadding + (mViewHeight - bounds.height()) / 2;
        mCanDraw = true;
      }
    }
  }

  @Override
  protected void onDraw(@NonNull Canvas canvas) {
    super.onDraw(canvas);

    final int length = mText.length();
    if (mCanDraw && length > 0) {
      canvas.drawText(mText, 0, length, mTextXPosition, mTextYPosition, mPaint);
    }
  }
}
