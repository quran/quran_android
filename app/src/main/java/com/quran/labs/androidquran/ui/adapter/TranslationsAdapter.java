package com.quran.labs.androidquran.ui.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.recyclerview.widget.RecyclerView;
import io.reactivex.Observable;
import io.reactivex.subjects.UnicastSubject;

public class TranslationsAdapter extends RecyclerView.Adapter<TranslationsAdapter.TranslationViewHolder> {

  private final UnicastSubject<TranslationRowData> onClickDownloadSubject = UnicastSubject.create();
  private final UnicastSubject<TranslationRowData> onClickRemoveSubject = UnicastSubject.create();
  private final UnicastSubject<TranslationRowData> onClickRankUpSubject = UnicastSubject.create();
  private final UnicastSubject<TranslationRowData> onClickRankDownSubject = UnicastSubject.create();

  private final AppCompatActivity activity;
  private final TranslationSelectionListener selectionListener;
  private final DownloadedItemActionListener downloadedItemActionListener;

  private List<TranslationRowData> translations = new ArrayList<>();

  private ActionMode actionMode;

  private TranslationItem selectedItem;

  public TranslationsAdapter(AppCompatActivity anActivity) {
    this.activity = anActivity;
    this.selectionListener = new TranslationSelectionListener(this);
    this.downloadedItemActionListener = new DownloadedItemActionListener();
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
        holder.itemView.setActivated(item.equals(selectedItem));
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

  class TranslationSelectionListener {
    private final TranslationsAdapter adapter;

    TranslationSelectionListener(TranslationsAdapter anAdapter) {
      adapter = anAdapter;
    }

    void handleSelection(TranslationItem item) {
      adapter.setSelectedItem(item);
    }

    void clearSelection() {
      adapter.setSelectedItem(null);
    }
  }

  class DownloadedItemActionListener {
    void handleDeleteItemAction() {
      onClickRemoveSubject.onNext(selectedItem);
      selectedItem = null;
    }

    void handleRankUpItemAction() {
      onClickRankUpSubject.onNext(selectedItem);
      selectedItem = null;
    }

    void handleRankDownItemAction() {
      onClickRankDownSubject.onNext(selectedItem);
      selectedItem = null;
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
      if (actionMode != null) {
        actionMode.finish();
        selectionListener.clearSelection();
      } else if (activity != null) {
        selectionListener.handleSelection(item);
        actionMode = activity.startSupportActionMode(new ModeCallback());
      }
      return true;
    };

    @Override
    public void onClick(View v) {
      if (actionMode != null) {
        actionMode.finish();
      }
      selectionListener.clearSelection();
      if (!item.exists() || item.needsUpgrade()) {
        onClickDownloadSubject.onNext(item);
      }
    }
  }

  private class ModeCallback implements ActionMode.Callback  {
    @Override
    public boolean onCreateActionMode ( ActionMode mode, Menu menu )
    {
      MenuInflater inflater = activity.getMenuInflater();
      inflater.inflate(R.menu.downloaded_translation_menu, menu);
      return true;
    }

    @Override
    public boolean onPrepareActionMode ( ActionMode mode, Menu menu )
    {
      return false;
    }

    @Override
    public boolean onActionItemClicked ( ActionMode mode, MenuItem item )
    {
      switch(item.getItemId ()) {
        case R.id.dtm_delete:
          downloadedItemActionListener.handleDeleteItemAction();
          endAction();
          break;
        case R.id.dtm_move_up:
          downloadedItemActionListener.handleRankUpItemAction();
          endAction();
          break;
        case R.id.dtm_move_down:
          downloadedItemActionListener.handleRankDownItemAction();
          endAction();
          break;
      }
      return false;
    }

    @Override
    public void onDestroyActionMode ( ActionMode mode )
    {
      if (mode == actionMode) actionMode = null;
    }

    private void endAction() {
      if (actionMode != null) {
        selectionListener.clearSelection();
        actionMode.finish();
      }
    }
  }
}
