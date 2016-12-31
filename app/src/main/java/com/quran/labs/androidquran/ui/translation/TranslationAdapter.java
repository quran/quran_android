package com.quran.labs.androidquran.ui.translation;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.support.annotation.LayoutRes;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableString;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.model.translation.ArabicDatabaseUtils;
import com.quran.labs.androidquran.ui.helpers.UthmaniSpan;
import com.quran.labs.androidquran.util.QuranSettings;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import butterknife.BindView;
import butterknife.ButterKnife;

import static com.quran.labs.androidquran.ui.translation.TranslationViewRow.Type.SPACER;

class TranslationAdapter extends RecyclerView.Adapter<TranslationAdapter.RowViewHolder> {
  private static final boolean USE_UTHMANI_SPAN =
      Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR1;
  private static final float ARABIC_MULTIPLIER = 1.4f;

  private final Context context;
  private final LayoutInflater inflater;
  private final List<TranslationViewRow> data;
  private View.OnClickListener onClickListener;

  private int fontSize;
  private int textColor;

  private View.OnClickListener defaultClickListener = new View.OnClickListener() {
    @Override
    public void onClick(View v) {
      if (onClickListener != null) {
        onClickListener.onClick(v);
      }
    }
  };

  TranslationAdapter(Context context) {
    this.context = context;
    this.data = new ArrayList<>();
    this.inflater = LayoutInflater.from(context);
  }

  void setData(List<TranslationViewRow> data) {
    this.data.clear();
    this.data.addAll(data);
  }

  void setOnTranslationClickedListener(View.OnClickListener listener) {
    this.onClickListener = listener;
  }

  void refresh(QuranSettings quranSettings) {
    this.fontSize = quranSettings.getTranslationTextSize();
    boolean isNightMode = quranSettings.isNightMode();
    if (isNightMode) {
      int textBrightness = quranSettings.getNightModeTextBrightness();
      this.textColor = Color.rgb(textBrightness, textBrightness, textBrightness);
    } else {
      this.textColor = Color.BLACK;
    }

    if (!this.data.isEmpty()) {
      notifyDataSetChanged();
    }
  }

  @Override
  public int getItemViewType(int position) {
    return data.get(position).type;
  }

  @Override
  public RowViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    @LayoutRes int layout;
    if (viewType == TranslationViewRow.Type.SURA_HEADER) {
      layout = R.layout.quran_translation_header_row;
    } else if (viewType == TranslationViewRow.Type.BASMALLAH ||
        viewType == TranslationViewRow.Type.QURAN_TEXT) {
      layout = R.layout.quran_translation_arabic_row;
    } else if (viewType == SPACER) {
      layout = R.layout.quran_translation_spacer_row;
    } else {
      layout = R.layout.quran_translation_text_row;
    }
    View view = inflater.inflate(layout, parent, false);
    return new RowViewHolder(view);
  }

  @Override
  public void onBindViewHolder(RowViewHolder holder, int position) {
    TranslationViewRow row = data.get(position);

    if (holder.text != null) {
      final CharSequence text;
      if (row.type == TranslationViewRow.Type.SURA_HEADER) {
        text = QuranInfo.getSuraName(context, row.data.getSura(), true);
      } else if (row.type == TranslationViewRow.Type.BASMALLAH ||
          row.type == TranslationViewRow.Type.QURAN_TEXT) {
        SpannableString str = new SpannableString(row.type == TranslationViewRow.Type.BASMALLAH ?
            ArabicDatabaseUtils.AR_BASMALLAH : ArabicDatabaseUtils.getAyahWithoutBasmallah(
            row.data.getSura(), row.data.getAyah(), row.data.getText()));
        if (USE_UTHMANI_SPAN) {
          str.setSpan(new UthmaniSpan(context), 0, str.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        text = str;
        holder.text.setTextColor(textColor);
        holder.text.setTextSize(ARABIC_MULTIPLIER * fontSize);
      } else {
        if (row.type == TranslationViewRow.Type.VERSE_NUMBER) {
          text = context.getString(R.string.sura_ayah, row.data.getSura(), row.data.getAyah());
        } else {
          text = row.data.getTranslation();
        }
        holder.text.setTextColor(textColor);
        holder.text.setTextSize(fontSize);
      }
      holder.text.setText(text);
    }
  }

  @Override
  public int getItemCount() {
    return data.size();
  }

  class RowViewHolder extends RecyclerView.ViewHolder {
    @BindView(R.id.text) @Nullable TextView text;

    RowViewHolder(View itemView) {
      super(itemView);
      ButterKnife.bind(this, itemView);
      itemView.setOnClickListener(defaultClickListener);
    }
  }
}
