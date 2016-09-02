package com.quran.labs.androidquran.ui.util;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.LocalTranslation;
import com.quran.labs.androidquran.util.TranslationUtils;

import java.util.ArrayList;
import java.util.List;

public class TranslationsSpinnerAdapter extends ArrayAdapter<String> {

  private final Context appContext;
  private final LayoutInflater layoutInflater;

  private String[] translationNames;
  private List<LocalTranslation> translations;

  public TranslationsSpinnerAdapter(Context context, int resource,
      String[] translationNames, List<LocalTranslation> translations) {
    // intentionally making a new ArrayList instead of using the constructor for String[].
    // this is because clear() relies on being able to clear the List passed into the constructor,
    // and the String[] constructor makes a new (immutable) List with the items of the array.
    super(context, resource, new ArrayList<String>());
    this.layoutInflater = LayoutInflater.from(context);
    this.translationNames = translationNames;
    this.translations = translations;
    this.appContext = context.getApplicationContext();
    addAll(translationNames);
  }

  @NonNull
  @Override
  public View getView(int position, View convertView, @NonNull ViewGroup parent) {
    SpinnerHolder holder;
    if (convertView == null) {
      holder = new SpinnerHolder();
      convertView = layoutInflater.inflate(R.layout.translation_ab_spinner_selected, parent, false);
      holder.title = (TextView) convertView.findViewById(R.id.title);
      holder.subtitle = (TextView) convertView.findViewById(R.id.subtitle);
      convertView.setTag(holder);
    }
    holder = (SpinnerHolder) convertView.getTag();

    holder.title.setText(translationNames[position]);
    holder.subtitle.setVisibility(View.GONE);

    return convertView;
  }

  public void updateItems(String[] translationNames, List<LocalTranslation> translations) {
    clear();
    this.translationNames = translationNames;
    this.translations = translations;
    addAll(translationNames);
  }

  public LocalTranslation getTranslationItem(int pos) {
    return translations.get(pos);
  }

  public int getPositionForActiveTranslation() {
    String activeTranslation = TranslationUtils.getDefaultTranslation(appContext, translations);
    int index = 0;
    for (int i = 0, mTranslationsSize = translations.size(); i < mTranslationsSize; i++) {
      LocalTranslation item = translations.get(i);
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
