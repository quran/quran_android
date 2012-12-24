package com.quran.labs.androidquran.widgets;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.util.ArabicStyle;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;

import java.util.List;

public class TranslationView extends LinearLayout {

   private Context mContext;
   private int mDividerColor;
   private int mLeftRightMargin;
   private int mTopBottomMargin;
   private int mTextStyle;
   private int mFontSize;
   private int mHeaderColor;
   private int mHeaderStyle;
   private boolean mIsArabic;
   private int mArabicStatus;
   private boolean mUseArabicFont;
   private boolean mShouldReshape;
   private List<QuranAyah> mAyat;
   private OnTextClickedListener mListener;

   public TranslationView(Context context){
      super(context);
      init(context);
   }

   public TranslationView(Context context, AttributeSet attrs){
      super(context, attrs);
      init(context);
   }

   public TranslationView(Context context, AttributeSet attrs, int defStyle){
      super(context, attrs, defStyle);
      init(context);
   }

   public void init(Context context){
      mContext = context;
      setOrientation(VERTICAL);

      Resources resources = getResources();
      mDividerColor = resources.getColor(R.color.translation_hdr_color);
      mLeftRightMargin = resources.getDimensionPixelSize(
              R.dimen.translation_left_right_margin);
      mTopBottomMargin = resources.getDimensionPixelSize(
              R.dimen.translation_top_bottom_margin);
      mHeaderColor = resources.getColor(R.color.translation_sura_header);
      mHeaderStyle = R.style.translation_sura_title;
      mFontSize = QuranSettings.getTranslationTextSize(mContext);

      mIsArabic = QuranSettings.isArabicNames(mContext);
      mShouldReshape = QuranSettings.isReshapeArabic(mContext);
      mUseArabicFont = QuranSettings.needArabicFont(mContext);
      mArabicStatus = 0;

      boolean nightMode = QuranSettings.isNightMode(mContext);
      mTextStyle = nightMode ? R.style.translation_night_mode :
              R.style.translation_text;
   }

   public void refresh(){
      int size = QuranSettings.getTranslationTextSize(mContext);

      if (size != mFontSize){
         mFontSize = size;
         setAyahs(mAyat);
      }
   }

   public void setAyahs(List<QuranAyah> ayat){
      removeAllViews();
      mAyat = ayat;

      int currentSura = 0;
      boolean isFirst = true;
      SpannableStringBuilder ayatInSura = new SpannableStringBuilder();
      for (QuranAyah ayah : ayat){
         if (ayah.getSura() != currentSura){
            if (ayatInSura.length() > 0){
               addTextForSura(ayatInSura);
            }
            ayatInSura.clear();
            currentSura = ayah.getSura();
            addSuraHeader(currentSura);

            isFirst = true;
         }

         if (!isFirst){ ayatInSura.append("\n\n"); }
         isFirst = false;
         int start = ayatInSura.length();
         // Ayah Header
         ayatInSura.append(ayah.getSura() + ":" + ayah.getAyah());
         int end = ayatInSura.length();
         ayatInSura.setSpan(new StyleSpan(Typeface.BOLD), start, end, 0);
         ayatInSura.append("\n");
         start = end+1;

         String ayahText = ayah.getText();
         if (!TextUtils.isEmpty(ayahText)){
            // Ayah Text
            if (mShouldReshape){
               ayahText = ArabicStyle.reshape(mContext, ayahText);
               mArabicStatus = 1;
            }

            ayatInSura.append(ayahText);
            end = ayatInSura.length();
            ayatInSura.setSpan(new StyleSpan(Typeface.BOLD), start, end, 0);
            ayatInSura.append("\n");
            start = end+1;
         }
         
         // Translation
         String translationText = ayah.getTranslation();
         if (mShouldReshape && mArabicStatus != 2){
            if (mArabicStatus == 0){  // 0 means we didn't check yet
               if (QuranUtils.doesStringContainArabic(translationText)){
                  mArabicStatus = 1;  // 1 means we have arabic
               }
               else { mArabicStatus= 2; } // no arabic in this translation
            }

            if (mArabicStatus == 1){
               translationText = ArabicStyle.reshape(mContext,
                       translationText);
            }
         }
         ayatInSura.append(translationText);
         end = ayatInSura.length();
      }
      if (ayatInSura.length() > 0){
         addTextForSura(ayatInSura);
      }
   }

   private OnClickListener mOnTextClickListener = new OnClickListener() {
      @Override
      public void onClick(View view) {
         if (mListener != null){
            mListener.onTextClicked();
         }
      }
   };

   public void setOnTextClickedListener(OnTextClickedListener listener){
      mListener = listener;
   }

   public interface OnTextClickedListener {
      public void onTextClicked();
   }

   private void addTextForSura(SpannableStringBuilder stringBuilder){
      TextView translationText = new TextView(mContext);
      translationText.setTextAppearance(mContext, mTextStyle);
      if (Build.VERSION.SDK_INT >= 11){
         translationText.setTextIsSelectable(true);
         translationText.setOnClickListener(mOnTextClickListener);
      }
      translationText.setText(stringBuilder);
      translationText.setTextSize(mFontSize);
      if (mUseArabicFont && mArabicStatus == 1){
         translationText.setTypeface(ArabicStyle.getTypeface(mContext));
      }
      LinearLayout.LayoutParams params = new LayoutParams(
              LayoutParams.MATCH_PARENT,
              LayoutParams.WRAP_CONTENT);
      params.setMargins(mLeftRightMargin, mTopBottomMargin,
              mLeftRightMargin, mTopBottomMargin);
      translationText.setLineSpacing(1.4f, 1.4f);
      addView(translationText, params);
   }

   private void addSuraHeader(int currentSura){
      View view = new View(mContext);
      
      view.setBackgroundColor(mHeaderColor);
      LinearLayout.LayoutParams params = new LayoutParams(
              LayoutParams.MATCH_PARENT, 2);
      params.topMargin = mTopBottomMargin;
      addView(view, params);

      String suraName = QuranInfo.getSuraName(mContext, currentSura, true);

      TextView headerView = new TextView(mContext);
      params = new LayoutParams(LayoutParams.MATCH_PARENT,
              LayoutParams.WRAP_CONTENT);
      params.leftMargin = mLeftRightMargin;
      params.rightMargin = mLeftRightMargin;
      params.topMargin = mTopBottomMargin / 2;
      params.bottomMargin = mTopBottomMargin / 2;
      headerView.setTextAppearance(mContext, mHeaderStyle);
      if (mIsArabic){
         if (mShouldReshape){
            suraName = ArabicStyle.reshape(mContext, suraName);
         }

         if (mUseArabicFont){
            headerView.setTypeface(ArabicStyle.getTypeface(mContext));
         }
      }
      headerView.setText(suraName);
      addView(headerView, params);

      view = new View(mContext);
      view.setBackgroundColor(mDividerColor);
      addView(view, LayoutParams.MATCH_PARENT, 2);
   }
}
