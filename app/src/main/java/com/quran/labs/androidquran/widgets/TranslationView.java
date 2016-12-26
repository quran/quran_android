package com.quran.labs.androidquran.widgets;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.support.annotation.StyleRes;
import android.support.v4.content.ContextCompat;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.model.translation.ArabicDatabaseUtils;
import com.quran.labs.androidquran.ui.helpers.UthmaniSpan;
import com.quran.labs.androidquran.ui.helpers.VerseLineHeightSpan;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranSettings;

import java.util.List;

public class TranslationView extends ScrollView {
  private static final boolean USE_UTHMANI_SPAN =
      Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR1;
  private static final float ARABIC_RELATIVE_SIZE = 1.4f;

  private Context context;
  private Resources resources;
  private int dividerColor;
  private int leftRightMargin;
  private int topBottomMargin;
  @StyleRes private int textStyle;
  @StyleRes private int highlightedStyle;
  private int fontSize;
  private int headerColor;
  private int headerStyle;
  private int footerSpacerHeight;
  private int lastHighlightedAyah;
  private boolean isNightMode;
  private int nightModeTextColor;
  private boolean isInAyahActionMode;
  private boolean isDataMissing;

  private List<QuranAyah> ayat;
  private SparseArray<TextView> ayahMap;
  private SparseArray<TextView> ayahHeaderMap;

  private LinearLayout linearLayout;
  private TranslationClickedListener translationClickedListener;

  public TranslationView(Context context) {
    this(context, null);
  }

  public TranslationView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public TranslationView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init(context);
  }

  public void setIsInAyahActionMode(boolean isInAyahActionMode) {
    this.isInAyahActionMode = isInAyahActionMode;
  }

  private void init(Context context) {
    this.context = context;
    ayahMap = new SparseArray<>();
    ayahHeaderMap = new SparseArray<>();
    isDataMissing = true;

    setFillViewport(true);
    linearLayout = new LinearLayout(context);
    linearLayout.setOrientation(LinearLayout.VERTICAL);
    addView(linearLayout, ScrollView.LayoutParams.MATCH_PARENT,
        ScrollView.LayoutParams.WRAP_CONTENT);
    linearLayout.setOnClickListener(v -> {
      if (translationClickedListener != null) {
        translationClickedListener.onTranslationClicked();
      }
    });

    resources = getResources();
    dividerColor = ContextCompat.getColor(context, R.color.translation_hdr_color);
    leftRightMargin = resources.getDimensionPixelSize(R.dimen.translation_left_right_margin);
    topBottomMargin = resources.getDimensionPixelSize(R.dimen.translation_top_bottom_margin);
    footerSpacerHeight = resources.getDimensionPixelSize(R.dimen.translation_footer_spacer);
    headerColor = ContextCompat.getColor(context, R.color.translation_sura_header);
    headerStyle = R.style.translation_sura_title;
    initResources();
  }

  private void initResources() {
    QuranSettings settings = QuranSettings.getInstance(context);
    fontSize = settings.getTranslationTextSize();

    isNightMode = settings.isNightMode();
    if (isNightMode) {
      int brightness = settings.getNightModeTextBrightness();
      nightModeTextColor = Color.rgb(brightness, brightness, brightness);
    }
    textStyle = isNightMode ? R.style.TranslationText_NightMode :
        R.style.TranslationText;
    highlightedStyle = isNightMode ?
        R.style.TranslationText_NightMode_Highlighted :
        R.style.TranslationText_Highlighted;
  }

  public void refresh() {
    if (ayat != null) {
      initResources();
      setAyahs(ayat);
    }
  }

  public void setDataMissing(boolean isDataMissing) {
    this.isDataMissing = isDataMissing;
  }

  public boolean isDataMissing() {
    return isDataMissing;
  }

  public void setNightMode(boolean isNightMode, int textBrightness) {
    this.isNightMode = isNightMode;
    if (isNightMode) {
      nightModeTextColor = Color.rgb(textBrightness, textBrightness, textBrightness);
    }
    textStyle = this.isNightMode ? R.style.TranslationText_NightMode :
        R.style.TranslationText;
    highlightedStyle = this.isNightMode ?
        R.style.TranslationText_NightMode_Highlighted :
        R.style.TranslationText_Highlighted;
    if (ayat != null) {
      setAyahs(ayat);
    }
  }

  public void setAyahs(List<QuranAyah> ayat) {
    linearLayout.removeAllViews();
    ayahMap.clear();
    ayahHeaderMap.clear();
    this.ayat = ayat;

    int currentSura = 0;
    for (int i = 0, ayatSize = ayat.size(); i < ayatSize; i++) {
      QuranAyah ayah = ayat.get(i);

      final int sura = ayah.getSura();
      if (!isInAyahActionMode && sura != currentSura) {
        addSuraHeader(sura);
        if (ayah.getAyah() == 1 && (sura != 1 && sura != 9)) {
          // explicitly add basmallah
          addBasmallah();
        }
        currentSura = sura;
      }
      addTextForAyah(ayah);
    }

    addFooterSpacer();

    if (lastHighlightedAyah > -1) {
      highlightAyah(lastHighlightedAyah);
    }
  }

  public void unhighlightAyat() {
    if (lastHighlightedAyah > 0) {
      TextView text = ayahMap.get(lastHighlightedAyah);
      if (text != null) {
        text.setTextAppearance(context, textStyle);
        text.setTextSize(fontSize);
      }

      text = ayahHeaderMap.get(lastHighlightedAyah);
      if (text != null) {
        styleAyahHeader(text, textStyle);
      }
    }
    lastHighlightedAyah = -1;
  }

  public void highlightAyah(int ayahId) {
    if (lastHighlightedAyah > 0) {
      unhighlightAyat();
    }

    TextView text = ayahMap.get(ayahId);
    if (text != null) {
      text.setTextAppearance(context, highlightedStyle);
      text.setTextSize(fontSize);
      lastHighlightedAyah = ayahId;

      TextView header = ayahHeaderMap.get(ayahId);
      if (header != null) {
        styleAyahHeader(header, highlightedStyle);
      }

      int screenHeight = QuranScreenInfo.getInstance().getHeight();
      int y = text.getTop() - (int) (0.25 * screenHeight);
      smoothScrollTo(getScrollX(), y);
    } else if (ayat != null && ayat.size() > 0) {
      lastHighlightedAyah = -1;
    } else {
      lastHighlightedAyah = ayahId;
    }
  }

  private OnClickListener mOnAyahClickListener = new OnClickListener() {
    @Override
    public void onClick(View v) {
      if (translationClickedListener != null) {
        translationClickedListener.onTranslationClicked();
      }
    }
  };

  private void addFooterSpacer() {
    final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LayoutParams.MATCH_PARENT, footerSpacerHeight);
    final View view = new View(context);
    linearLayout.addView(view, params);
  }

  private void addTextForAyah(QuranAyah ayah) {
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LayoutParams.MATCH_PARENT,
        LayoutParams.WRAP_CONTENT);
    params.setMargins(leftRightMargin, topBottomMargin,
        leftRightMargin, topBottomMargin);

    final int suraNumber = ayah.getSura();
    final int ayahNumber = ayah.getAyah();
    final int ayahId = QuranInfo.getAyahId(suraNumber, ayahNumber);
    TextView ayahHeader = new TextView(context);
    styleAyahHeader(ayahHeader, textStyle);
    ayahHeader.setText(resources.getString(R.string.sura_ayah, suraNumber, ayahNumber));
    linearLayout.addView(ayahHeader, params);
    ayahHeaderMap.put(ayahId, ayahHeader);

    TextView ayahView = new TextView(context);
    ayahView.setOnClickListener(mOnAyahClickListener);
    ayahMap.put(ayahId, ayahView);

    ayahView.setTextAppearance(context, textStyle);
    if (isInAyahActionMode) {
      ayahView.setTextColor(Color.WHITE);
    } else if (isNightMode) {
      ayahView.setTextColor(nightModeTextColor);
    }
    ayahView.setTextSize(fontSize);

    // arabic
    String ayahText = ayah.getText();
    if (!TextUtils.isEmpty(ayahText)) {
      ayahText = ArabicDatabaseUtils.getAyahWithoutBasmallah(suraNumber, ayahNumber, ayahText);

      // Ayah Text
      ayahView.setLineSpacing(VerseLineHeightSpan.TRANSLATION_LINE_HEIGHT_ADDITION,
          VerseLineHeightSpan.TRANSLATION_LINE_HEIGHT_MULTIPLY);

      SpannableString arabicText = new SpannableString(ayahText);
      if (USE_UTHMANI_SPAN) {
        UthmaniSpan uthmaniSpan = new UthmaniSpan(context);
        int length = ayahText.length();
        arabicText.setSpan(uthmaniSpan, 0, length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        arabicText.setSpan(new RelativeSizeSpan(ARABIC_RELATIVE_SIZE),
            0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        arabicText.setSpan(new VerseLineHeightSpan(),
            0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
      ayahView.setText(arabicText);
      ayahView.append("\n\n");
    }

    // translation
    String translationText = ayah.getTranslation();

    SpannableString translation = new SpannableString(translationText);
    ayahView.append(translation);

    params = new LinearLayout.LayoutParams(
        LayoutParams.MATCH_PARENT,
        LayoutParams.WRAP_CONTENT);
    params.setMargins(leftRightMargin, topBottomMargin,
        leftRightMargin, topBottomMargin);
    setTextSelectable(ayahView);
    linearLayout.addView(ayahView, params);
  }

  private void styleAyahHeader(TextView headerView, @StyleRes int style) {
    headerView.setTextAppearance(context, style);
    if (isInAyahActionMode) {
      headerView.setTextColor(Color.WHITE);
    } else if (isNightMode) {
      headerView.setTextColor(nightModeTextColor);
    }
    headerView.setTextSize(fontSize);
    headerView.setTypeface(null, Typeface.BOLD);
  }

  private void setTextSelectable(TextView ayahView) {
    ayahView.setTextIsSelectable(true);
  }

  private void addSuraHeader(int currentSura) {
    View view = new View(context);

    view.setBackgroundColor(headerColor);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LayoutParams.MATCH_PARENT, 2);
    params.topMargin = topBottomMargin;
    linearLayout.addView(view, params);

    String suraName = QuranInfo.getSuraName(context, currentSura, true);

    TextView headerView = new TextView(context);
    params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
        LayoutParams.WRAP_CONTENT);
    params.leftMargin = leftRightMargin;
    params.rightMargin = leftRightMargin;
    params.topMargin = topBottomMargin / 2;
    params.bottomMargin = topBottomMargin / 2;
    headerView.setTextAppearance(context, headerStyle);
    headerView.setText(suraName);
    linearLayout.addView(headerView, params);

    view = new View(context);
    view.setBackgroundColor(dividerColor);
    linearLayout.addView(view, LayoutParams.MATCH_PARENT, 2);
  }

  private void addBasmallah() {
    TextView tv = new TextView(context);
    tv.setTextAppearance(context, textStyle);
    if (isNightMode) {
      tv.setTextColor(nightModeTextColor);
    }
    tv.setTextSize(fontSize);

    SpannableString str = new SpannableString(ArabicDatabaseUtils.AR_BASMALLAH);

    if (USE_UTHMANI_SPAN) {
      UthmaniSpan uthmaniSpan = new UthmaniSpan(context);
      int length = str.length();
      str.setSpan(uthmaniSpan, 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      str.setSpan(new RelativeSizeSpan(ARABIC_RELATIVE_SIZE),
          0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    tv.setText(str);

    LinearLayout.LayoutParams params =
        new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    params.leftMargin = leftRightMargin;
    params.rightMargin = leftRightMargin;
    params.topMargin = topBottomMargin / 2;
    params.bottomMargin = topBottomMargin / 2;

    linearLayout.addView(tv, params);
  }

  public void setTranslationClickedListener(
      TranslationClickedListener listener) {
    translationClickedListener = listener;
  }

  public interface TranslationClickedListener {

    void onTranslationClicked();
  }
}
