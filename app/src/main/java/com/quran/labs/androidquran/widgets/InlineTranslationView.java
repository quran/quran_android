package com.quran.labs.androidquran.widgets;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.support.annotation.StyleRes;
import android.text.SpannableString;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.util.QuranSettings;

import java.util.List;

public class InlineTranslationView extends ScrollView {
  private Context context;
  private Resources resources;
  private int leftRightMargin;
  private int topBottomMargin;
  @StyleRes private int textStyle;
  private int fontSize;
  private int footerSpacerHeight;

  private List<QuranAyah> ayat;

  private LinearLayout linearLayout;

  public InlineTranslationView(Context context) {
    this(context, null);
  }

  public InlineTranslationView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public InlineTranslationView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init(context);
  }

  private void init(Context context) {
    this.context = context;

    setFillViewport(true);
    linearLayout = new LinearLayout(context);
    linearLayout.setOrientation(LinearLayout.VERTICAL);
    addView(linearLayout, ScrollView.LayoutParams.MATCH_PARENT,
        ScrollView.LayoutParams.WRAP_CONTENT);

    resources = getResources();
    leftRightMargin = resources.getDimensionPixelSize(R.dimen.translation_left_right_margin);
    topBottomMargin = resources.getDimensionPixelSize(R.dimen.translation_top_bottom_margin);
    footerSpacerHeight = resources.getDimensionPixelSize(R.dimen.translation_footer_spacer);
    initResources();
  }

  private void initResources() {
    QuranSettings settings = QuranSettings.getInstance(context);
    fontSize = settings.getTranslationTextSize();
    textStyle = R.style.TranslationText;
  }

  public void refresh() {
    if (ayat != null) {
      initResources();
      setAyahs(ayat);
    }
  }

  public void setAyahs(List<QuranAyah> ayat) {
    linearLayout.removeAllViews();
    this.ayat = ayat;

    for (int i = 0, ayatSize = ayat.size(); i < ayatSize; i++) {
      addTextForAyah(ayat.get(i));
    }
    addFooterSpacer();
  }

  private void addFooterSpacer() {
    final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LayoutParams.MATCH_PARENT, footerSpacerHeight);
    final View view = new View(context);
    linearLayout.addView(view, params);
  }

  private void addTextForAyah(QuranAyah ayah) {
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    params.setMargins(leftRightMargin, topBottomMargin, leftRightMargin, topBottomMargin);

    final int suraNumber = ayah.getSura();
    final int ayahNumber = ayah.getAyah();
    TextView ayahHeader = new TextView(context);
    ayahHeader.setTextColor(Color.WHITE);
    ayahHeader.setTextSize(fontSize);
    ayahHeader.setTypeface(null, Typeface.BOLD);
    ayahHeader.setText(resources.getString(R.string.sura_ayah, suraNumber, ayahNumber));
    linearLayout.addView(ayahHeader, params);

    TextView ayahView = new TextView(context);
    ayahView.setTextAppearance(context, textStyle);
    ayahView.setTextColor(Color.WHITE);
    ayahView.setTextSize(fontSize);

    // translation
    String translationText = ayah.getTranslation();

    SpannableString translation = new SpannableString(translationText);
    ayahView.append(translation);

    params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    params.setMargins(leftRightMargin, topBottomMargin, leftRightMargin, topBottomMargin);
    ayahView.setTextIsSelectable(true);
    linearLayout.addView(ayahView, params);
  }

}
