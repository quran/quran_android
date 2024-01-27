package com.quran.labs.androidquran.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Space;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.view.ViewCompat;

import com.quran.data.model.audio.Qari;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.audio.model.QariItem;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;

public class AudioStatusBar extends LeftToRightLinearLayout {

  public static final int STOPPED_MODE = 1;
  public static final int DOWNLOADING_MODE = 2;
  public static final int LOADING_MODE = 3;
  public static final int PLAYING_MODE = 4;
  public static final int PAUSED_MODE = 5;
  public static final int PROMPT_DOWNLOAD_MODE = 6;
  public static final int RECITATION_LISTENING_MODE = 7;
  public static final int RECITATION_STOPPED_MODE = 8;
  public static final int RECITATION_PLAYING_MODE = 9;

  private static final int MAX_AUDIOBAR_QUICK_REPEAT = 3;

  private final Context context;
  private int currentMode;
  private final int buttonWidth;
  private final int separatorWidth;
  private final int separatorSpacing;
  private final int textFontSize;
  private final int textFullFontSize;
  private final int buttonPadding;

  private Qari currentQari;
  private int currentRepeat = 0;
  private final int defaultSpeedIndex = 2;
  private int currentSpeedIndex = defaultSpeedIndex;
  private final float[] speeds = { 0.5f, 0.75f, 1f, 1.25f, 1.5f};
  private float currentSpeed = speeds[currentSpeedIndex];
  @DrawableRes private int itemBackground;
  private final boolean isRtl;
  private boolean isDualPageMode;
  private boolean isRecitationEnabled;
  private boolean hasErrorText;
  private boolean haveCriticalError = false;

  private TextView qariView;
  private ImageView dropdownIconView;
  private TextView progressText;
  private ProgressBar progressBar;
  private final RepeatButton repeatButton;
  private final RepeatButton speedButton;
  private AudioBarListener audioBarListener;
  private AudioBarRecitationListener audioBarRecitationListener;

  public interface AudioBarListener {
    void onPlayPressed();
    void onPausePressed();
    void onNextPressed();
    void onPreviousPressed();
    void onStopPressed();
    void setPlaybackSpeed(float speed);
    void onCancelPressed(boolean stopDownload);
    void setRepeatCount(int repeatCount);
    void onAcceptPressed();
    void onAudioSettingsPressed();
    void onShowQariList();
  }

  public interface AudioBarRecitationListener {
    void onRecitationPressed();
    void onRecitationLongPressed();
    void onRecitationTranscriptPressed();
    void onHideVersesPressed();
    void onEndRecitationSessionPressed();
    void onPlayRecitationPressed();
    void onPauseRecitationPressed();
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
    repeatButton = new RepeatButton(context);
    speedButton = new RepeatButton(context);
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
    buttonPadding = resources
        .getDimensionPixelSize(R.dimen.audiobar_spinner_padding);
    setOrientation(LinearLayout.HORIZONTAL);

    // only flip the layout when the language is rtl and we're on api 17+
    isRtl = QuranSettings.getInstance(this.context).isArabicNames() || QuranUtils.isRtl();

    itemBackground = 0;
    if (attrs != null) {
      TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.AudioStatusBar);
      itemBackground = ta.getResourceId(R.styleable.AudioStatusBar_android_itemBackground,
          itemBackground);
      ta.recycle();
    }
  }

  public void setCurrentQariBridge(CurrentQariBridge currentQariBridge) {
    currentQariBridge.listenToQaris(qari -> {
      currentQari = qari;
      updateButton();
      return null;
    });
  }

  public void setIsDualPageMode(boolean isDualPageMode) {
    this.isDualPageMode = isDualPageMode;
  }

  public boolean getIsRecitationEnabled() {
    return isRecitationEnabled;
  }

  public void setIsRecitationEnabled(boolean isEnabled) {
    this.isRecitationEnabled = isEnabled;
  }

  public int getCurrentMode() {
    return currentMode;
  }

  public void switchMode(int mode) {
    switchMode(mode, false);
  }

  public void switchMode(int mode, boolean force) {
    if (mode == currentMode && !force) {
      return;
    }

    if (mode == STOPPED_MODE) {
      showStoppedMode();
    } else if (mode == PROMPT_DOWNLOAD_MODE) {
      showPromptForDownloadMode();
    } else if (mode == DOWNLOADING_MODE || mode == LOADING_MODE) {
      showProgress(mode);
    } else if (mode == PLAYING_MODE) {
      showPlayingMode(false);
    } else if (mode == PAUSED_MODE){
      showPlayingMode(true);
    } else if (mode == RECITATION_LISTENING_MODE){
      showRecitationListeningMode();
    } else if (mode == RECITATION_STOPPED_MODE){
      showRecitationStoppedMode();
    } else if (mode == RECITATION_PLAYING_MODE){
      showRecitationPlayingMode();
    }
  }

  @NonNull
  public QariItem getAudioInfo() {
    return QariItem.Companion.fromQari(context, currentQari);
  }

  public void setSpeed(float speed) {
    for (int i = 0; i < speeds.length; i++) {
      if (speeds[i] == speed) {
        currentSpeedIndex = i;
        updateSpeedButtonText();
        return;
      }
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
      if (isRecitationEnabled) {
        addButton(com.quran.labs.androidquran.common.toolbar.R.drawable.ic_mic, false);
        addSeparator();
      }
      addButton();
      addSeparator();
      addButton(com.quran.labs.androidquran.common.toolbar.R.drawable.ic_play, false);
    } else {
      addButton(com.quran.labs.androidquran.common.toolbar.R.drawable.ic_play, false);
      addSeparator();
      addButton();
      if (isRecitationEnabled) {
        addSeparator();
        addButton(com.quran.labs.androidquran.common.toolbar.R.drawable.ic_mic, false);
      }
    }
  }

  private void updateButton() {
    final TextView currentQariView = qariView;
    final Qari qari = currentQari;
    if (currentQariView != null && qari != null) {
      currentQariView.setText(qari.getNameResource());
    }
  }

  private void addButton() {
    if (qariView == null) {
      qariView = new TextView(context);
      qariView.setOnClickListener(view -> audioBarListener.onShowQariList());
      qariView.setGravity(Gravity.CENTER_VERTICAL);
      qariView.setTextColor(Color.WHITE);
      qariView.setBackgroundResource(itemBackground);
      qariView.setPadding(buttonPadding, 0, buttonPadding, 0);
    }

    if (dropdownIconView == null) {
      dropdownIconView = new ImageView(context);
      dropdownIconView.setImageResource(R.drawable.ic_action_expand);
      dropdownIconView.setBackgroundResource(itemBackground);
      dropdownIconView.setOnClickListener(view -> audioBarListener.onShowQariList());
      dropdownIconView.setPadding(buttonPadding, 0, buttonPadding, 0);
    }
    updateButton();

    final ViewGroup.LayoutParams dropdownParams =
        new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        );

    final LayoutParams params = new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
    params.weight = 1;

    if (isRtl) {
      ViewCompat.setLayoutDirection(qariView, ViewCompat.LAYOUT_DIRECTION_RTL);
      addView(dropdownIconView, dropdownParams);
    }
    addView(qariView, params);
    if (!isRtl) {
      addView(dropdownIconView, dropdownParams);
    }
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

  private void showProgress(int mode) {
    currentMode = mode;

    removeAllViews();

    final int text = mode == DOWNLOADING_MODE ? R.string.downloading_title : R.string.index_loading;
    if (isRtl) {
      addDownloadProgress(text);
      addSeparator();
      addButton(R.drawable.ic_cancel, false);
    } else {
      addButton(R.drawable.ic_cancel, false);
      addSeparator();
      addDownloadProgress(text);
    }
  }

  private void addDownloadProgress(@StringRes int text) {
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
    progressText.setText(text);

    ll.addView(progressText, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

    LinearLayout.LayoutParams lp =
        new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT);
    lp.weight = 1;
    lp.setMargins(separatorSpacing, 0, separatorSpacing, 0);
    if (isRtl) {
      lp.leftMargin = buttonPadding;
    } else {
      lp.rightMargin = buttonPadding;
    }
    addView(ll, lp);
  }

  private void showRecitationListeningMode() {
    currentMode = RECITATION_LISTENING_MODE;
    removeAllViews();

    ImageView recitationButton = new ImageView(context);
    recitationButton.setImageTintList(ColorStateList.valueOf(Color.CYAN));

    if (isRtl) {
      addButton(recitationButton, com.quran.labs.androidquran.common.toolbar.R.drawable.ic_mic, false);
      addSeparator();
      addButton(R.drawable.ic_transcript, false);
      addSeparator();
      addSpacer();
      addSeparator();
      addButton(R.drawable.ic_hide_page, false);
    } else {
      addButton(R.drawable.ic_hide_page, false);
      addSeparator();
      addSpacer();
      addSeparator();
      addButton(R.drawable.ic_transcript, false);
      addSeparator();
      addButton(recitationButton, com.quran.labs.androidquran.common.toolbar.R.drawable.ic_mic, false);
    }
  }

  private void showRecitationStoppedMode() {
    currentMode = RECITATION_STOPPED_MODE;
    removeAllViews();

    if (isRtl) {
      addButton(com.quran.labs.androidquran.common.toolbar.R.drawable.ic_mic, false);
      addSeparator();
      addButton(R.drawable.ic_transcript, false);
      addSeparator();
      addSpacer();
      addSeparator();
      addButton(com.quran.labs.androidquran.common.toolbar.R.drawable.ic_play, false);
      addButton(R.drawable.ic_cancel, false);
    } else {
      addButton(R.drawable.ic_cancel, false);
      addButton(com.quran.labs.androidquran.common.toolbar.R.drawable.ic_play, false);
      addSeparator();
      addSpacer();
      addSeparator();
      addButton(R.drawable.ic_transcript, false);
      addSeparator();
      addButton(com.quran.labs.androidquran.common.toolbar.R.drawable.ic_mic, false);
    }
  }

  private void showRecitationPlayingMode() {
    currentMode = RECITATION_PLAYING_MODE;
    removeAllViews();

    if (isRtl) {
      addButton(com.quran.labs.androidquran.common.toolbar.R.drawable.ic_mic, false);
      addSeparator();
      addButton(R.drawable.ic_transcript, false);
      addSeparator();
      addSpacer();
      addSeparator();
      addButton(R.drawable.ic_pause, false);
      addButton(R.drawable.ic_cancel, false);
    } else {
      addButton(R.drawable.ic_cancel, false);
      addButton(R.drawable.ic_pause, false);
      addSeparator();
      addSpacer();
      addSeparator();
      addButton(R.drawable.ic_transcript, false);
      addSeparator();
      addButton(com.quran.labs.androidquran.common.toolbar.R.drawable.ic_mic, false);
    }
  }

  private void showPlayingMode(boolean isPaused) {
    removeAllViews();

    final boolean withWeight = !isDualPageMode;

    int button;
    if (isPaused) {
      button = com.quran.labs.androidquran.common.toolbar.R.drawable.ic_play;
      currentMode = PAUSED_MODE;
    } else {
      button = R.drawable.ic_pause;
      currentMode = PLAYING_MODE;
    }

    addButton(R.drawable.ic_stop, withWeight);
    addButton(R.drawable.ic_previous, withWeight);
    addButton(button, withWeight);
    addButton(R.drawable.ic_next, withWeight);

    addButton(repeatButton, R.drawable.ic_repeat, withWeight);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
      addButton(speedButton, R.drawable.ic_speed, withWeight);
    }
    updateRepeatButtonText();
    updateSpeedButtonText();

    addButton(R.drawable.ic_action_settings, withWeight);
  }

  private void addButton(int imageId, boolean withWeight) {
    addButton(new ImageView(context), imageId, withWeight);
  }

  private void addButton(@NonNull ImageView button, int imageId, boolean withWeight) {
    button.setImageResource(imageId);
    button.setScaleType(ImageView.ScaleType.CENTER);
    button.setOnClickListener(onClickListener);
    button.setOnLongClickListener(onLongClickListener);
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
    addView(separator, paddingParams);
  }

  private void addSpacer() {
    Space spacer = new Space(context);
    LinearLayout.LayoutParams params = new LayoutParams(0, LayoutParams.MATCH_PARENT);
    params.weight = 1;
    addView(spacer, params);
  }

  private void incrementRepeat() {
    currentRepeat++;
    if (currentRepeat - 1 == MAX_AUDIOBAR_QUICK_REPEAT) {
      currentRepeat = -1;
    } else if (currentRepeat > MAX_AUDIOBAR_QUICK_REPEAT) {
      currentRepeat = 0;
    }
    updateRepeatButtonText();
  }

  private void updatePlaybackSpeed() {
    currentSpeedIndex = (currentSpeedIndex + 1) % speeds.length;
    currentSpeed = speeds[currentSpeedIndex];
    updateSpeedButtonText();
  }

  private void updateRepeatButtonText() {
    final String str;
    if (currentRepeat == -1) {
      str = context.getString(R.string.infinity);
    } else if (currentRepeat == 0) {
      str = "";
    } else {
      str = String.valueOf(currentRepeat);
    }
    repeatButton.setText(str);
  }

  private void updateSpeedButtonText(){
    currentSpeed = speeds[currentSpeedIndex];
    final String str;
    if (currentSpeedIndex == 2) {
      str = "";
    } else {
      str = String.valueOf(currentSpeed);
    }

    post(() -> {
      if (speedButton != null) {
        speedButton.setText(str);
      }
    });
  }

  public void setRepeatCount(int repeatCount) {
    boolean updated = false;
    if (currentRepeat != repeatCount) {
      currentRepeat = repeatCount;
      updated = true;
    }

    if (updated && repeatButton != null) {
      updateRepeatButtonText();
    }
  }

  public void setAudioBarListener(AudioBarListener listener) {
    audioBarListener = listener;
  }

  public void setAudioBarRecitationListener(AudioBarRecitationListener listener) {
    audioBarRecitationListener = listener;
  }

  OnClickListener onClickListener = new OnClickListener() {
    @Override
    public void onClick(View view) {
      if (audioBarListener != null) {
        int tag = (Integer) view.getTag();
        if (tag == com.quran.labs.androidquran.common.toolbar.R.drawable.ic_mic) {
          audioBarRecitationListener.onRecitationPressed();
        } else if (tag == R.drawable.ic_transcript) {
          audioBarRecitationListener.onRecitationTranscriptPressed();
        } else if (tag == R.drawable.ic_hide_page) {
          audioBarRecitationListener.onHideVersesPressed();
        } else if (tag == com.quran.labs.androidquran.common.toolbar.R.drawable.ic_play) {
          if (currentMode == RECITATION_STOPPED_MODE) {
            audioBarRecitationListener.onPlayRecitationPressed();
          } else {
            audioBarListener.onPlayPressed();
          }
        } else if (tag == R.drawable.ic_stop) {
          audioBarListener.onStopPressed();
        } else if (tag == R.drawable.ic_pause) {
          if (currentMode == RECITATION_PLAYING_MODE) {
            audioBarRecitationListener.onPauseRecitationPressed();
          } else {
            audioBarListener.onPausePressed();
          }
        } else if (tag == R.drawable.ic_next) {
          audioBarListener.onNextPressed();
        } else if (tag == R.drawable.ic_speed) {
          updatePlaybackSpeed();
          audioBarListener.setPlaybackSpeed(currentSpeed);
        } else if (tag == R.drawable.ic_previous) {
          audioBarListener.onPreviousPressed();
        } else if (tag == R.drawable.ic_repeat) {
          incrementRepeat();
          audioBarListener.setRepeatCount(currentRepeat);
        } else if (tag == R.drawable.ic_cancel) {
          if (currentMode == RECITATION_STOPPED_MODE || currentMode == RECITATION_PLAYING_MODE) {
            audioBarRecitationListener.onEndRecitationSessionPressed();
          } else if (haveCriticalError) {
            haveCriticalError = false;
            switchMode(STOPPED_MODE);
          } else {
            audioBarListener.onCancelPressed(currentMode == DOWNLOADING_MODE);
          }
        } else if (tag == R.drawable.ic_accept) {
          audioBarListener.onAcceptPressed();
        } else if (tag == R.drawable.ic_action_settings) {
          audioBarListener.onAudioSettingsPressed();
        }
      }
    }
  };

  OnLongClickListener onLongClickListener = new OnLongClickListener() {
    @Override
    public boolean onLongClick(View view) {
      if (audioBarListener != null) {
        int tag = (Integer) view.getTag();
        if (tag == com.quran.labs.androidquran.common.toolbar.R.drawable.ic_mic) {
          audioBarRecitationListener.onRecitationLongPressed();
          return true;
        }
      }
      return false;
    }
  };

}
