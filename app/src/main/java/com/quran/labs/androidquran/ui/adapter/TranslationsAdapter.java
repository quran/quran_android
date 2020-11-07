package com.quran.labs.androidquran.ui.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.dao.translation.TranslationItem;
import com.quran.labs.androidquran.dao.translation.TranslationRowData;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import io.reactivex.Observable;
import io.reactivex.subjects.UnicastSubject;

public class TranslationsAdapter extends RecyclerView.Adapter<TranslationsAdapter.TranslationViewHolder> {

  private final UnicastSubject<TranslationRowData> onClickDownloadSubject = UnicastSubject.create();
  private final UnicastSubject<TranslationRowData> onClickRemoveSubject = UnicastSubject.create();
  private final UnicastSubject<TranslationRowData> onClickRankUpSubject = UnicastSubject.create();
  private final UnicastSubject<TranslationRowData> onClickRankDownSubject = UnicastSubject.create();

  private final DownloadedMenuActionListener downloadedMenuActionListener;
  private final DownloadedItemActionListener downloadedItemActionListener;

  private List<TranslationRowData> translations = new ArrayList<>();

  private TranslationItem selectedItem;

  public TranslationsAdapter(DownloadedMenuActionListener aDownloadedMenuActionListener) {
    this.downloadedMenuActionListener = aDownloadedMenuActionListener;
    this.downloadedItemActionListener = new DownloadedItemActionListenerImpl();
  }

  @NotNull
  @Override
  public TranslationViewHolder onCreateViewHolder(@NotNull ViewGroup parent, int viewType) {
    View view = LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false);
    return new TranslationViewHolder(view, viewType);
  }

  @Override
  public void onBindViewHolder(@NotNull TranslationViewHolder holder, int position) {
    TranslationRowData rowItem = translations.get(position);
    switch (holder.getItemViewType()) {
      case R.layout.translation_row:
        TranslationItem item = (TranslationItem) rowItem;
        holder.setItem(item);
        holder.itemView.setActivated(
          (selectedItem != null) && (item.getTranslation().getId() == selectedItem.getTranslation().getId())
        );
        holder.getTranslationTitle().setText(item.name());
        if (TextUtils.isEmpty(item.getTranslation().getTranslatorNameLocalized())) {
          holder.getTranslationInfo().setText(item.getTranslation().getTranslator());
        } else {
          holder.getTranslationInfo().setText(item.getTranslation().getTranslatorNameLocalized());
        }

        ImageView leftImage = holder.getLeftImage();
        ImageView rightImage = holder.getRightImage();

        if (item.exists()) {
          rightImage.setVisibility(View.GONE);
          holder.itemView.setOnLongClickListener(holder.actionMenuListener);
          if (item.needsUpgrade()) {
            leftImage.setImageResource(R.drawable.ic_download);
            leftImage.setVisibility(View.VISIBLE);
            holder.getTranslationInfo().setText(R.string.update_available);
          } else {
            leftImage.setVisibility(View.GONE);
          }
        } else {
          leftImage.setVisibility(View.GONE);
          rightImage.setImageResource(R.drawable.ic_download);
          rightImage.setOnClickListener(null);
          rightImage.setVisibility(View.VISIBLE);
          rightImage.setOnClickListener(null);
          rightImage.setClickable(false);
          rightImage.setContentDescription(null);
        }
        break;
      case R.layout.translation_sep:
        holder.itemView.setActivated(false);
        holder.getSeparatorText().setText(rowItem.name());
        break;
    }
  }

  @Override
  public int getItemCount() {
    return translations.size();
  }

  @Override
  public int getItemViewType(int position) {
    return translations.get(position).isSeparator() ?
        R.layout.translation_sep : R.layout.translation_row;
  }

  public Observable<TranslationRowData> getOnClickDownloadSubject() {
    return onClickDownloadSubject.hide();
  }

  public Observable<TranslationRowData> getOnClickRemoveSubject() {
    return onClickRemoveSubject.hide();
  }

  public Observable<TranslationRowData> getOnClickRankUpSubject() {
    return onClickRankUpSubject.hide();
  }

  public Observable<TranslationRowData> getOnClickRankDownSubject() {
    return onClickRankDownSubject.hide();
  }

  public void setTranslations(List<TranslationRowData> data) {
    this.translations = data;
  }

  public List<TranslationRowData> getTranslations() {
    return translations;
  }

  public void setSelectedItem ( TranslationItem selectedItem ) {
    this.selectedItem = selectedItem;
    notifyDataSetChanged();
  }

  class DownloadedItemActionListenerImpl implements DownloadedItemActionListener {
    public void handleDeleteItemAction() {
      onClickRemoveSubject.onNext(selectedItem);
    }

    public void handleRankUpItemAction() {
      onClickRankUpSubject.onNext(selectedItem);
    }

    public void handleRankDownItemAction() {
      onClickRankDownSubject.onNext(selectedItem);
    }
  }

  class TranslationViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

    @Nullable TextView translationTitle;
    @Nullable TextView translationInfo;
    @Nullable ImageView leftImage;
    @Nullable ImageView rightImage;
    @Nullable TextView separatorText;
    @Nullable TranslationItem item;

    TranslationViewHolder(View itemView, int viewType) {
      super(itemView);
      translationTitle = itemView.findViewById(R.id.translation_title);
      translationInfo = itemView.findViewById(R.id.translation_info);
      leftImage = itemView.findViewById(R.id.left_image);
      rightImage = itemView.findViewById(R.id.right_image);
      separatorText = itemView.findViewById(R.id.separator_txt);
      if (viewType == R.layout.translation_row) {
        itemView.setOnClickListener(this);
      }
    }

    void setItem ( @Nullable TranslationItem item ) {
      this.item = item;
    }

    TextView getSeparatorText() {
      return separatorText;
    }

    TextView getTranslationTitle() {
      return translationTitle;
    }

    TextView getTranslationInfo() {
      return translationInfo;
    }

    ImageView getLeftImage() {
      return leftImage;
    }

    ImageView getRightImage() {
      return rightImage;
    }

    final View.OnLongClickListener actionMenuListener = v -> {
      downloadedMenuActionListener.startMenuAction(item, downloadedItemActionListener);
      return true;
    };

    @Override
    public void onClick(View v) {
      downloadedMenuActionListener.finishMenuAction();
      if (!item.exists() || item.needsUpgrade()) {
        onClickDownloadSubject.onNext(item);
      }
    }
  }
}
