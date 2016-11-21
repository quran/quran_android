package com.quran.labs.androidquran.widgets;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.annotation.DrawableRes;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.QariItem;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.util.AudioUtils;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;

import java.util.List;

public class AudioStatusBar extends LeftToRightLinearLayout {

  public static final int STOPPED_MODE = 1;
  public static final int DOWNLOADING_MODE = 2;
  public static final int PLAYING_MODE = 3;
  public static final int PAUSED_MODE = 4;
  public static final int PROMPT_DOWNLOAD_MODE = 5;

  private Context context;
  private int currentMode;
  private int buttonWidth;
  private int separatorWidth;
  private int separatorSpacing;
  private int textFontSize;
  private int textFullFontSize;
  private int spinnerPadding;
  private QariAdapter adapter;

  private int currentQari;
  private int currentRepeat = 0;
  @DrawableRes private int itemBackground;
  private boolean isRtl;
  private boolean isTablet;
  private boolean hasErrorText;
  private boolean haveCriticalError = false;
  private SharedPreferences sharedPreferences;

  private QuranSpinner spinner;
  private TextView progressText;
  private ProgressBar progressBar;
  private RepeatButton repeatButton;
  private AudioBarListener audioBarListener;

  private int[] repeatValues = {0, 1, 2, 3, -1};

  public interface AudioBarListener {
    void onPlayPressed();
    void onPausePressed();
    void onNextPressed();
    void onPreviousPressed();
    void onStopPressed();
    void onCancelPressed(boolean stopDownload);
    void setRepeatCount(int repeatCount);
    void onAcceptPressed();
    void onAudioSettingsPressed();
  }

  public AudioStatusBar(Context context) {
    this(context, null);
  }

  public AudioStatusBar(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public AudioStatusBar(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);

    this.context = context;
    Resources resources = getResources();
    buttonWidth = resources.getDimensionPixelSize(
        R.dimen.audiobar_button_width);
    separatorWidth = resources.getDimensionPixelSize(
        R.dimen.audiobar_separator_width);
    separatorSpacing = resources.getDimensionPixelSize(
        R.dimen.audiobar_separator_padding);
    textFontSize = resources.getDimensionPixelSize(
        R.dimen.audiobar_text_font_size);
    textFullFontSize = resources.getDimensionPixelSize(
        R.dimen.audiobar_text_full_font_size);
    spinnerPadding = resources
        .getDimensionPixelSize(R.dimen.audiobar_spinner_padding);
    setOrientation(LinearLayout.HORIZONTAL);

    // only flip the layout when the language is rtl and we're on api 17+
    isRtl = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 &&
        (QuranSettings.getInstance(this.context).isArabicNames() || QuranUtils.isRtl());
    isTablet = QuranScreenInfo.getOrMakeInstance(this.context).isTablet(this.context);
    sharedPreferences = PreferenceManager
        .getDefaultSharedPreferences(context.getApplicationContext());
    currentQari = sharedPreferences.getInt(Constants.PREF_DEFAULT_QARI, 0);

    itemBackground = 0;
    if (attrs != null) {
      TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.AudioStatusBar);
      itemBackground = ta.getResourceId(R.styleable.AudioStatusBar_android_itemBackground,
          itemBackground);
      ta.recycle();
    }

    List<QariItem> qariList = AudioUtils.getQariList(this.context);

    // TODO: optimize - PREF_DEFAULT_QARI is the qari id, should introduce a helper pref for pos
    final int qaris = qariList.size();
    if (currentQari >= qaris || qariList.get(currentQari).getId() != currentQari) {
      // figure out the updated position for the index
      int updatedIndex = 0;
      for (int i = 0; i < qaris; i++) {
        if (qariList.get(i).getId() == currentQari) {
          updatedIndex = i;
          break;
        }
      }
      currentQari = updatedIndex;
    }

    adapter = new QariAdapter(this.context, qariList,
        R.layout.sherlock_spinner_item, R.layout.sherlock_spinner_dropdown_item);
    showStoppedMode();
  }

  public int getCurrentMode() {
    return currentMode;
  }

  public void switchMode(int mode) {
    if (mode == currentMode) {
      return;
    }

    if (mode == STOPPED_MODE) {
      showStoppedMode();
    } else if (mode == PROMPT_DOWNLOAD_MODE) {
      showPromptForDownloadMode();
    } else if (mode == DOWNLOADING_MODE) {
      showDownloadingMode();
    } else if (mode == PLAYING_MODE) {
      showPlayingMode(false);
    } else {
      showPlayingMode(true);
    }
  }

  @NonNull
  public QariItem getAudioInfo() {
    final int position = spinner != null ? spinner.getSelectedItemPosition() : currentQari;
    return adapter.getItem(position);
  }

  public void updateSelectedItem() {
    if (spinner != null) {
      spinner.setSelection(currentQari);
    }
  }

  public void setProgress(int progress) {
    if (hasErrorText) {
      progressText.setText(R.string.downloading_title);
      hasErrorText = false;
    }

    if (progressBar != null) {
      if (progress >= 0) {
        progressBar.setIndeterminate(false);
        progressBar.setProgress(progress);
        progressBar.setMax(100);
      } else {
        progressBar.setIndeterminate(true);
      }
    }
  }

  public void setProgressText(String progressText, boolean isCriticalError) {
    if (this.progressText != null) {
      hasErrorText = true;
      this.progressText.setText(progressText);
      if (isCriticalError && progressBar != null) {
        progressBar.setVisibility(View.GONE);
        this.progressText.setTextSize(TypedValue.COMPLEX_UNIT_PX,
            textFullFontSize);
        haveCriticalError = true;
      }
    }
  }

  private void showStoppedMode() {
    currentMode = STOPPED_MODE;
    removeAllViews();

    if (isRtl) {
      addSpinner();
      addSeparator();
      addButton(R.drawable.ic_play, false);
    } else {
      addButton(R.drawable.ic_play, false);
      addSeparator();
      addSpinner();
    }
  }

  private static class QariAdapter extends BaseAdapter {
    @NonNull LayoutInflater mInflater;
    @NonNull private final List<QariItem> mItems;
    @LayoutRes private final int mLayoutViewId;
    @LayoutRes private final int mDropDownViewId;

    QariAdapter(@NonNull Context context,
                @NonNull List<QariItem> items,
                @LayoutRes int layoutViewId,
                @LayoutRes int dropDownViewId) {
      mItems = items;
      mLayoutViewId = layoutViewId;
      mDropDownViewId = dropDownViewId;
      mInflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
      return mItems.size();
    }

    @Override
    public QariItem getItem(int position) {
      return mItems.get(position);
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      return getViewInternal(position, convertView, parent, mLayoutViewId);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
      return getViewInternal(position, convertView, parent, mDropDownViewId);
    }

    private View getViewInternal(int position, View convertView,
        ViewGroup parent, @LayoutRes int resource) {
      TextView textView;
      if (convertView == null) {
        textView = (TextView) mInflater.inflate(resource, parent, false);
      } else {
        textView = (TextView) convertView;
      }

      QariItem item = getItem(position);
      textView.setText(item.getName());
      return textView;
    }
  }

  private void addSpinner() {
    if (spinner == null) {
      spinner = new QuranSpinner(context, null,
          R.attr.actionDropDownStyle);
      spinner.setDropDownVerticalOffset(spinnerPadding);
      spinner.setAdapter(adapter);

      spinner.setOnItemSelectedListener(
          new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
              if (position != currentQari) {
                sharedPreferences.edit().
                    putInt(Constants.PREF_DEFAULT_QARI, adapter.getItem(position).getId()).apply();
                currentQari = position;
              }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
          });
    }
    spinner.setSelection(currentQari);
    final LayoutParams params = new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
    params.weight = 1;
    if (isRtl) {
      ViewCompat.setLayoutDirection(spinner, ViewCompat.LAYOUT_DIRECTION_RTL);
      params.leftMargin = spinnerPadding;
    } else {
      params.rightMargin = spinnerPadding;
    }
    addView(spinner, params);
  }

  private void showPromptForDownloadMode() {
    currentMode = PROMPT_DOWNLOAD_MODE;

    removeAllViews();

    if (isRtl) {
      addButton(R.drawable.ic_cancel, false);
      addDownloadOver3gPrompt();
      addSeparator();
      addButton(R.drawable.ic_accept, false);
    } else {
      addButton(R.drawable.ic_accept, false);
      addSeparator();
      addDownloadOver3gPrompt();
      addButton(R.drawable.ic_cancel, false);
    }
  }

  private void addDownloadOver3gPrompt() {
    TextView mPromptText = new TextView(context);
    mPromptText.setTextColor(Color.WHITE);
    mPromptText.setGravity(Gravity.CENTER_VERTICAL);
    mPromptText.setTextSize(TypedValue.COMPLEX_UNIT_PX,
        textFontSize);
    mPromptText.setText(R.string.download_non_wifi_prompt);
    LayoutParams params = new LayoutParams(0,
        LayoutParams.MATCH_PARENT);
    params.weight = 1;
    addView(mPromptText, params);
  }

  private void showDownloadingMode() {
    currentMode = DOWNLOADING_MODE;

    removeAllViews();

    if (isRtl) {
      addDownloadProgress();
      addSeparator();
      addButton(R.drawable.ic_cancel, false);
    } else {
      addButton(R.drawable.ic_cancel, false);
      addSeparator();
      addDownloadProgress();
    }
  }

  private void addDownloadProgress() {
    LinearLayout ll = new LinearLayout(context);
    ll.setOrientation(LinearLayout.VERTICAL);

    progressBar = (ProgressBar) LayoutInflater.from(context)
        .inflate(R.layout.download_progress_bar, this, false);
    progressBar.setIndeterminate(true);
    progressBar.setVisibility(View.VISIBLE);

    ll.addView(progressBar, LayoutParams.MATCH_PARENT,
        LayoutParams.WRAP_CONTENT);

    progressText = new TextView(context);
    progressText.setTextColor(Color.WHITE);
    progressText.setGravity(Gravity.CENTER_VERTICAL);
    progressText.setTextSize(TypedValue.COMPLEX_UNIT_PX, textFontSize);
    progressText.setText(R.string.downloading_title);

    ll.addView(progressText, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

    LinearLayout.LayoutParams lp =
        new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT);
    lp.weight = 1;
    lp.setMargins(separatorSpacing, 0, separatorSpacing, 0);
    if (isRtl) {
      lp.leftMargin = spinnerPadding;
    } else {
      lp.rightMargin = spinnerPadding;
    }
    addView(ll, lp);
  }

  private void showPlayingMode(boolean isPaused) {
    removeAllViews();

    final boolean withWeight = !isTablet;

    int button;
    if (isPaused) {
      button = R.drawable.ic_play;
      currentMode = PAUSED_MODE;
    } else {
      button = R.drawable.ic_pause;
      currentMode = PLAYING_MODE;
    }

    addButton(R.drawable.ic_stop, withWeight);
    addButton(R.drawable.ic_previous, withWeight);
    addButton(button, withWeight);
    addButton(R.drawable.ic_next, withWeight);

    repeatButton = new RepeatButton(context);
    addButton(repeatButton, R.drawable.ic_repeat, withWeight);
    updateRepeatButtonText();

    addButton(R.drawable.ic_action_settings, withWeight);
  }

  private void addButton(int imageId, boolean withWeight) {
    addButton(new ImageView(context), imageId, withWeight);
  }

  private void addButton(@NonNull ImageView button, int imageId, boolean withWeight) {
    button.setImageResource(imageId);
    button.setScaleType(ImageView.ScaleType.CENTER);
    button.setOnClickListener(mOnClickListener);
    button.setTag(imageId);
    button.setBackgroundResource(itemBackground);
    final LayoutParams params = new LayoutParams(
        withWeight ? 0 : buttonWidth, LayoutParams.MATCH_PARENT);
    if (withWeight) {
      params.weight = 1;
    }
    addView(button, params);
  }

  private void addSeparator() {
    ImageView separator = new ImageView(context);
    separator.setBackgroundColor(Color.WHITE);
    separator.setPadding(0, separatorSpacing, 0, separatorSpacing);
    LinearLayout.LayoutParams paddingParams =
        new LayoutParams(separatorWidth, LayoutParams.MATCH_PARENT);

    final int right = isRtl ? 0 : separatorSpacing;
    final int left = isRtl ? separatorSpacing : 0;
    paddingParams.setMargins(left, 0, right, 0);
    addView(separator, paddingParams);
  }

  private void incrementRepeat() {
    currentRepeat++;
    if (currentRepeat == repeatValues.length) {
      currentRepeat = 0;
    }
    updateRepeatButtonText();
  }

  private void updateRepeatButtonText() {
    String str;
    int value = repeatValues[currentRepeat];
    if (value == 0) {
      str = "";
    } else if (value > 0) {
      str = repeatValues[currentRepeat] + "";
    } else {
      str = context.getString(R.string.infinity);
    }
    repeatButton.setText(str);
  }

  public void setRepeatCount(int repeatCount) {
    boolean updated = false;
    for (int i = 0; i < repeatValues.length; i++) {
      if (repeatValues[i] == repeatCount) {
        if (currentRepeat != i) {
          currentRepeat = i;
          updated = true;
        }
        break;
      }
    }

    if (updated && repeatButton != null) {
      updateRepeatButtonText();
    }
  }

  public void setAudioBarListener(AudioBarListener listener) {
    audioBarListener = listener;
  }

  OnClickListener mOnClickListener = new OnClickListener() {
    @Override
    public void onClick(View view) {
      if (audioBarListener != null) {
        int tag = (Integer) view.getTag();
        switch (tag) {
          case R.drawable.ic_play:
            audioBarListener.onPlayPressed();
            break;
          case R.drawable.ic_stop:
            audioBarListener.onStopPressed();
            break;
          case R.drawable.ic_pause:
            audioBarListener.onPausePressed();
            break;
          case R.drawable.ic_next:
            audioBarListener.onNextPressed();
            break;
          case R.drawable.ic_previous:
            audioBarListener.onPreviousPressed();
            break;
          case R.drawable.ic_repeat:
            incrementRepeat();
            audioBarListener.setRepeatCount(repeatValues[currentRepeat]);
            break;
          case R.drawable.ic_cancel:
            if (haveCriticalError) {
              haveCriticalError = false;
              switchMode(STOPPED_MODE);
            } else {
              audioBarListener.onCancelPressed(currentMode != PROMPT_DOWNLOAD_MODE);
            }
            break;
          case R.drawable.ic_accept:
            audioBarListener.onAcceptPressed();
            break;
          case R.drawable.ic_action_settings:
            audioBarListener.onAudioSettingsPressed();
            break;
        }
      }
    }
  };
}
