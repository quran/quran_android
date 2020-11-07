package com.quran.labs.androidquran.view;

import android.content.Context;
import android.graphics.Color;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.util.QuranSettings;

/**
 * Wrapper around {@link ViewGroup} that adds {@link #showError(int)} and {@link #hideError()}
 * methods. These methods display/hide a given error message and a retry button.
 * When the retry button is clicked, {@link #handleRetryClicked} is called.
 */
public abstract class QuranPageWrapperLayout extends ViewGroup {

  private View errorLayout;
  private TextView errorText;
  private boolean isNightMode;

  public QuranPageWrapperLayout(Context context) {
    super(context);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    if (errorLayout != null) {
      int width = MeasureSpec.getSize(widthMeasureSpec);
      int height = MeasureSpec.getSize(heightMeasureSpec);
      errorLayout.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
          MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
    }
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    if (errorLayout != null) {
      int errorWidth = errorLayout.getMeasuredWidth();
      int errorHeight = errorLayout.getMeasuredHeight();
      int x = (getMeasuredWidth() - errorWidth) / 2;
      int y = (getMeasuredHeight() - errorHeight) / 2;
      errorLayout.layout(x, y, x + errorWidth, y + errorHeight);
    }
  }

  public void updateView(@NonNull QuranSettings quranSettings) {
    isNightMode = quranSettings.isNightMode();
    if (errorText != null) {
      updateErrorTextColor();
    }
  }

  public void showError(@StringRes int errorRes) {
    if (errorLayout == null) {
      inflateErrorLayout();
    }
    errorLayout.setVisibility(VISIBLE);
    errorText.setText(errorRes);
  }

  public void hideError() {
    if (errorLayout != null) {
      errorLayout.setVisibility(GONE);
    }
  }

  private void inflateErrorLayout() {
    final LayoutInflater inflater = LayoutInflater.from(getContext());
    errorLayout = inflater.inflate(R.layout.page_load_error, this, false);
    LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    addView(errorLayout, lp);
    errorText = errorLayout.findViewById(R.id.reason_text);
    final Button button = errorLayout.findViewById(R.id.retry_button);
    updateErrorTextColor();
    button.setOnClickListener(v -> {
      errorLayout.setVisibility(GONE);
      handleRetryClicked();
    });
  }

  abstract void handleRetryClicked();

  private void updateErrorTextColor() {
    errorText.setTextColor(isNightMode ? Color.WHITE : Color.BLACK);
  }
}
