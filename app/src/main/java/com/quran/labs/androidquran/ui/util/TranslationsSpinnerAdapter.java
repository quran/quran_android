package com.quran.labs.androidquran.ui.util;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.TranslationItem;
import com.quran.labs.androidquran.util.TranslationUtils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

public class TranslationsSpinnerAdapter extends ArrayAdapter<String> {

  private final LayoutInflater mInflater;
  private String[] mTranslationItems;
  private List<TranslationItem> mTranslations;
  private final Context mAppContext;

  public TranslationsSpinnerAdapter(Context context, int resource,
      String[] translationNames, List<TranslationItem> translations) {
    super(context, resource, translationNames);
    mInflater = LayoutInflater.from(context);
    mTranslationItems = translationNames;
    mTranslations = translations;
    mAppContext = context.getApplicationContext();
  }

  public void setData(List<TranslationItem> items, String[] names) {
    mTranslations = items;
    mTranslationItems = names;
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    SpinnerHolder holder;
    if (convertView == null) {
      holder = new SpinnerHolder();
      convertView = mInflater.inflate(
          R.layout.translation_ab_spinner_selected,
          parent, false);
      holder.title = (TextView) convertView.findViewById(R.id.title);
      holder.subtitle = (TextView) convertView.findViewById(
          R.id.subtitle);
      convertView.setTag(holder);
    }
    holder = (SpinnerHolder) convertView.getTag();

    holder.title.setText(mTranslationItems[position]);
    holder.subtitle.setVisibility(View.GONE);

    return convertView;
  }

  public TranslationItem getTranslationItem(int pos) {
    return mTranslations.get(pos);
  }

  public int getPositionForActiveTranslation() {
    String activeTranslation = TranslationUtils.getDefaultTranslation(mAppContext, mTranslations);
    int index = 0;
    for (TranslationItem item : mTranslations) {
      if (item.filename.equals(activeTranslation)) {
        return index;
      } else {
        index++;
      }
    }
    return index;
  }

  protected static class SpinnerHolder {

    public TextView title;
    public TextView subtitle;
  }
}
