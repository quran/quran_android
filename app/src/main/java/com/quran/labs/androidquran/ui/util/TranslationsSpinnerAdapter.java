package com.quran.labs.androidquran.ui.util;

import android.content.Context;
import android.content.res.Resources;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.mobile.translation.model.LocalTranslation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class TranslationsSpinnerAdapter extends ArrayAdapter<String> {

  private Context context;
  private final LayoutInflater layoutInflater;

  private String[] translationNames;
  private List<LocalTranslation> translations;
  private Set<String> selectedItems;
  private OnSelectionChangedListener listener;

  public TranslationsSpinnerAdapter(Context context,
                                    int resource,
                                    String[] translationNames,
                                    List<LocalTranslation> translations,
                                    Set<String> selectedItems,
                                    OnSelectionChangedListener listener) {
    // intentionally making a new ArrayList instead of using the constructor for String[].
    // this is because clear() relies on being able to clear the List passed into the constructor,
    // and the String[] constructor makes a fixed size List with the items of the array.
    super(context, resource, new ArrayList<>());
    this.context = context;
    this.layoutInflater = LayoutInflater.from(this.context);
    translationNames = updateTranslationNames(translationNames);
    this.translationNames = translationNames;
    this.translations = translations;
    this.selectedItems = selectedItems;
    this.listener = listener;
    addAll(translationNames);
  }

  private final View.OnClickListener onCheckedChangeListener = buttonView -> {
    CheckBoxHolder holder = (CheckBoxHolder) ((View) buttonView.getParent()).getTag();
    LocalTranslation localTranslation = translations.get(holder.position);

    if (selectedItems.contains(localTranslation.getFilename())) {
        selectedItems.remove(localTranslation.getFilename());
    } else {
      selectedItems.add(localTranslation.getFilename());
    }

    if (listener != null) {
      listener.onSelectionChanged(selectedItems);
    }

  };

  private final View.OnClickListener onTextClickedListener = textView -> {
    CheckBoxHolder holder = (CheckBoxHolder) ((View) textView.getParent()).getTag();
    if (holder.position == translationNames.length - 1) {
      if (this.context instanceof PagerActivity pagerActivity) {
        pagerActivity.startTranslationManager();
      }
    } else {
      holder.checkBox.performClick();
    }
  };

  @NonNull
  @Override
  public View getView(int position, View convertView, @NonNull ViewGroup parent) {
    SpinnerHolder holder;
    if (convertView == null) {
      holder = new SpinnerHolder();
      convertView = layoutInflater.inflate(R.layout.translation_ab_spinner_selected, parent, false);
      holder.title = convertView.findViewById(R.id.title);
      holder.subtitle = convertView.findViewById(R.id.subtitle);
      convertView.setTag(holder);
    }
    holder = (SpinnerHolder) convertView.getTag();

    holder.title.setText(R.string.translations);
    holder.subtitle.setVisibility(View.GONE);

    return convertView;
  }

  @Override
  public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
    CheckBoxHolder holder;
    if (convertView == null) {
      convertView = layoutInflater.inflate(
          R.layout.translation_ab_spinner_item, parent, false);
      convertView.setTag(new CheckBoxHolder(convertView));
    }
    holder = (CheckBoxHolder) convertView.getTag();
    holder.position = position;
    holder.textView.setOnClickListener(onTextClickedListener);
    if (position == translationNames.length - 1) {
      holder.checkBox.setVisibility(View.GONE);
      holder.checkBox.setOnClickListener(null);
      Resources r = convertView.getResources();
      float leftPadding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, r.getDisplayMetrics());
      holder.textView.setPadding(Math.round(leftPadding), 0, 0, 0);
      holder.textView.setText(R.string.more_translations);
    } else {
      holder.checkBox.setVisibility(View.VISIBLE);
      holder.checkBox.setChecked(selectedItems.contains(translations.get(position).getFilename()));
      holder.checkBox.setOnClickListener(onCheckedChangeListener);
      holder.textView.setText(translationNames[position]);
    }

    return convertView;
  }

  @Override
  public int getItemViewType(int position) {
    if (position == translationNames.length - 1) {
      return 1; // Last item in spinner should be text "More Translations"
    } else {
      return 0;
    }
  }

  public void updateItems(String[] translationNames,
                          List<LocalTranslation> translations,
                          Set<String> selectedItems) {
    clear();
    translationNames = updateTranslationNames(translationNames);
    this.translationNames = translationNames;
    this.translations = translations;
    this.selectedItems = selectedItems;
    addAll(translationNames);
    notifyDataSetChanged();
  }

  private static class CheckBoxHolder {
    final CheckBox checkBox;
    final TextView textView;
    int position;

    CheckBoxHolder(View view) {
      this.checkBox = view.findViewById(R.id.checkbox);
      this.textView = view.findViewById(R.id.text);
    }
  }

  protected static class SpinnerHolder {
    public TextView title;
    public TextView subtitle;
  }

  public interface OnSelectionChangedListener {
    void onSelectionChanged(Set<String> selectedItems);
  }

  private String[] updateTranslationNames(String[] translationNames) {
    final List<String> translationsList = new ArrayList<>();
    Collections.addAll(translationsList, translationNames);
    translationsList.add(getContext().getString(R.string.more_translations));
    translationNames = translationsList.toArray(new String[0]);

    return translationNames;
  }
}
