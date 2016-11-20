package com.quran.labs.androidquran.ui.helpers;

import android.content.Context;
import android.graphics.PorterDuff;
import android.support.v7.widget.RecyclerView;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.dao.Bookmark;
import com.quran.labs.androidquran.dao.Tag;
import com.quran.labs.androidquran.ui.QuranActivity;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.widgets.JuzView;
import com.quran.labs.androidquran.widgets.TagsViewGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class QuranListAdapter extends
    RecyclerView.Adapter<QuranListAdapter.HeaderHolder>
    implements View.OnClickListener, View.OnLongClickListener {

  private Context context;
  private LayoutInflater inflater;
  private QuranRow[] elements;
  private RecyclerView recyclerView;
  private SparseBooleanArray checkedState;
  private QuranTouchListener touchListener;
  private Map<Long, Tag> tagMap;
  private boolean showTags;
  private boolean isEditable;

  public QuranListAdapter(Context context, RecyclerView recyclerView,
                          QuranRow[] items, boolean isEditable) {
    inflater = LayoutInflater.from(context);
    this.recyclerView = recyclerView;
    elements = items;
    this.context = context;
    checkedState = new SparseBooleanArray();
    this.isEditable = isEditable;
  }

  public long getItemId(int position) {
    return position;
  }

  @Override
  public int getItemCount() {
    return elements.length;
  }

  private QuranRow getQuranRow(int position) {
    return elements[position];
  }

  public boolean isItemChecked(int position) {
    return checkedState.get(position);
  }

  public void setItemChecked(int position, boolean checked) {
    checkedState.put(position, checked);
    notifyItemChanged(position);
  }

  public List<QuranRow> getCheckedItems() {
    final List<QuranRow> result = new ArrayList<>();
    final int count = checkedState.size();
    final int elements = getItemCount();
    for (int i = 0; i < count; i++) {
      final int key = checkedState.keyAt(i);
      // TODO: figure out why sometimes elements > key
      if (checkedState.get(key) && elements > key) {
        result.add(getQuranRow(key));
      }
    }
    return result;
  }

  public void uncheckAll() {
    checkedState.clear();
    notifyDataSetChanged();
  }

  public void setElements(QuranRow[] elements, Map<Long, Tag> tagMap) {
    this.elements = elements;
    this.tagMap = tagMap;
  }

  public void setShowTags(boolean showTags) {
    this.showTags = showTags;
  }

  private void bindRow(HeaderHolder vh, int position) {
    ViewHolder holder = (ViewHolder) vh;

    final QuranRow item = elements[position];
    bindHeader(vh, position);
    holder.number.setText(
        QuranUtils.getLocalizedNumber(context, item.sura));

    holder.metadata.setVisibility(View.VISIBLE);
    holder.metadata.setText(item.metadata);
    holder.tags.setVisibility(View.GONE);

    if (item.juzType != null) {
      holder.image.setImageDrawable(
          new JuzView(context, item.juzType, item.juzOverlayText));
      holder.image.setVisibility(View.VISIBLE);
      holder.number.setVisibility(View.GONE);
    } else if (item.imageResource == null) {
      holder.number.setVisibility(View.VISIBLE);
      holder.image.setVisibility(View.GONE);
    } else {
      holder.image.setImageResource(item.imageResource);
      if (item.imageFilterColor == null) {
        holder.image.setColorFilter(null);
      } else {
        holder.image.setColorFilter(
            item.imageFilterColor, PorterDuff.Mode.SRC_ATOP);
      }
      holder.image.setVisibility(View.VISIBLE);
      holder.number.setVisibility(View.GONE);

      List<Tag> tags = new ArrayList<>();
      Bookmark bookmark = item.bookmark;
      if (bookmark != null && !bookmark.tags.isEmpty() && showTags) {
        for (int i = 0, bookmarkTags = bookmark.tags.size(); i < bookmarkTags; i++) {
          Long tagId = bookmark.tags.get(i);
          Tag tag = tagMap.get(tagId);
          if (tag != null) {
            tags.add(tag);
          }
        }
      }

      if (tags.isEmpty()) {
        holder.tags.setVisibility(View.GONE);
      } else {
        holder.tags.setTags(tags);
        holder.tags.setVisibility(View.VISIBLE);
      }
    }
  }

  private void bindHeader(HeaderHolder holder, int pos) {
    final QuranRow item = elements[pos];
    holder.title.setText(item.text);
    if (item.page == 0) {
      holder.pageNumber.setVisibility(View.GONE);
    } else {
      holder.pageNumber.setVisibility(View.VISIBLE);
      holder.pageNumber.setText(
          QuranUtils.getLocalizedNumber(context, item.page));
    }
    holder.setChecked(isItemChecked(pos));

    final boolean enabled = isEnabled(pos);
    holder.setEnabled(enabled);
  }

  @Override
  public HeaderHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    if (viewType == 0) {
      final View view = inflater.inflate(R.layout.index_header_row, parent, false);
      return new HeaderHolder(view);
    } else {
      final View view = inflater.inflate(R.layout.index_sura_row, parent, false);
      return new ViewHolder(view);
    }
  }

  @Override
  public void onBindViewHolder(HeaderHolder viewHolder, int position) {
    final int type = getItemViewType(position);
    if (type == 0) {
      bindHeader(viewHolder, position);
    } else {
      bindRow(viewHolder, position);
    }
  }

  @Override
  public int getItemViewType(int position) {
    return elements[position].isHeader() ? 0 : 1;
  }

  private boolean isEnabled(int position) {
    final QuranRow selected = elements[position];
    return !isEditable ||                     // anything in surahs or juzs
        selected.isBookmark() ||              // actual bookmarks
        selected.rowType == QuranRow.NONE ||  // the actual "current page"
        selected.isBookmarkHeader();          // tags
  }

  public void setQuranTouchListener(QuranTouchListener listener) {
    touchListener = listener;
  }

  @Override
  public void onClick(View v) {
    final int position = recyclerView.getChildAdapterPosition(v);
    if (position != RecyclerView.NO_POSITION) {
      final QuranRow element = elements[position];
      if (touchListener == null) {
        ((QuranActivity) context).jumpTo(element.page);
      } else {
        touchListener.onClick(element, position);
      }
    }
  }

  @Override
  public boolean onLongClick(View v) {
    if (touchListener != null) {
      final int position = recyclerView.getChildAdapterPosition(v);
      if (position != RecyclerView.NO_POSITION) {
        return touchListener.onLongClick(elements[position], position);
      }
    }
    return false;
  }

  class HeaderHolder extends RecyclerView.ViewHolder {

    TextView title;
    TextView pageNumber;
    View view;

    HeaderHolder(View itemView) {
      super(itemView);
      view = itemView;
      title = (TextView) itemView.findViewById(R.id.title);
      pageNumber = (TextView) itemView.findViewById(R.id.pageNumber);
    }

    void setEnabled(boolean enabled) {
      view.setEnabled(enabled);
      itemView.setOnClickListener(enabled ? QuranListAdapter.this : null);
      itemView.setOnLongClickListener(isEditable && enabled ? QuranListAdapter.this : null);
    }

    void setChecked(boolean checked) {
      view.setActivated(checked);
    }
  }

  private class ViewHolder extends HeaderHolder {

    TextView number;
    TextView metadata;
    ImageView image;
    TagsViewGroup tags;

    ViewHolder(View itemView) {
      super(itemView);
      metadata = (TextView) itemView.findViewById(R.id.metadata);
      number = (TextView) itemView.findViewById(R.id.suraNumber);
      image = (ImageView) itemView.findViewById(R.id.rowIcon);
      tags = (TagsViewGroup) itemView.findViewById(R.id.tags);
    }
  }

  public interface QuranTouchListener {

    void onClick(QuranRow row, int position);

    boolean onLongClick(QuranRow row, int position);
  }
}
