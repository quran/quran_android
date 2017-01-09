package com.quran.labs.androidquran.ui.util;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.LocalTranslation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TranslationsSpinnerAdapter extends ArrayAdapter<String> {

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
    // and the String[] constructor makes a new (immutable) List with the items of the array.
    super(context, resource, new ArrayList<>());
    this.layoutInflater = LayoutInflater.from(context);
    this.translationNames = translationNames;
    this.translations = translations;
    this.selectedItems = selectedItems;
    this.listener = listener;
    addAll(translationNames);
  }

  private View.OnClickListener onCheckedChangeListener = buttonView -> {
    CheckBoxHolder holder = (CheckBoxHolder) ((View) buttonView.getParent()).getTag();
    LocalTranslation localTranslation = translations.get(holder.checkBoxPosition);

    boolean updated = true;
    if (selectedItems.contains(localTranslation.filename)) {
      if (selectedItems.size() > 1) {
        selectedItems.remove(localTranslation.filename);
      } else {
        updated = false;
        holder.checkBox.setChecked(true);
      }
    } else {
      selectedItems.add(localTranslation.filename);
    }

    if (updated && listener != null) {
      listener.onSelectionChanged(selectedItems);
    }

  };

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

    holder.title.setText(R.string.translations);
    holder.subtitle.setVisibility(View.GONE);

    return convertView;
  }

  @Override
  public View getDropDownView(int position, View convertView, ViewGroup parent) {
    CheckBoxHolder holder;
    if (convertView == null) {
      convertView = layoutInflater.inflate(
          R.layout.translation_ab_spinner_item, parent, false);
      convertView.setTag(new CheckBoxHolder(convertView));
    }
    holder = (CheckBoxHolder) convertView.getTag();

    holder.checkBoxPosition = position;
    holder.checkBox.setText(translationNames[position]);
    holder.checkBox.setChecked(selectedItems.contains(translations.get(position).filename));
    holder.checkBox.setOnClickListener(onCheckedChangeListener);
    return convertView;
  }

  public void updateItems(String[] translationNames,
                          List<LocalTranslation> translations,
                          Set<String> selectedItems) {
    clear();
    this.translationNames = translationNames;
    this.translations = translations;
    this.selectedItems = selectedItems;
    addAll(translationNames);
    notifyDataSetChanged();
  }

  static class CheckBoxHolder {
    final CheckBox checkBox;
    int checkBoxPosition;

    CheckBoxHolder(View view) {
      this.checkBox = (CheckBox) view.findViewById(R.id.checkbox);
    }
  }

  protected static class SpinnerHolder {
    public TextView title;
    public TextView subtitle;
  }

  public interface OnSelectionChangedListener {
    void onSelectionChanged(Set<String> selectedItems);
  }
}
