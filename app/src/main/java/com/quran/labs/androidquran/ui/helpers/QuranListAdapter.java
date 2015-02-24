package com.quran.labs.androidquran.ui.helpers;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.ui.QuranActivity;
import com.quran.labs.androidquran.util.ArabicStyle;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.widgets.CheckableLinearLayout;
import com.quran.labs.androidquran.widgets.JuzView;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.support.v7.widget.RecyclerView;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class QuranListAdapter extends
    RecyclerView.Adapter<QuranListAdapter.HeaderHolder>
    implements View.OnClickListener, View.OnLongClickListener {

   private Context mContext;
   private LayoutInflater mInflater;
   private QuranRow[] mElements;
   private boolean mReshapeArabic;
   private boolean mUseArabicFont;
   private boolean mSelectableHeaders;
   private RecyclerView mRecyclerView;
   private SparseBooleanArray mCheckedState;
   private QuranTouchListener mTouchListener;

   public QuranListAdapter(Context context, RecyclerView recyclerView,
       QuranRow[] items, boolean selectableHeaders){
      mInflater = LayoutInflater.from(context);
      mRecyclerView = recyclerView;
      mElements = items;
      mContext = context;
      mSelectableHeaders = selectableHeaders;
      mCheckedState = new SparseBooleanArray();

      // should we reshape if we have arabic?
      mUseArabicFont = QuranSettings.needArabicFont(context);
      mReshapeArabic = QuranSettings.isReshapeArabic(context);
   }

   public long getItemId(int position) {
      return position;
   }

   @Override
   public int getItemCount() {
      return mElements.length;
   }

   public QuranRow getQuranRow(int position) {
      return mElements[position];
   }

   public boolean isItemChecked(int position) {
      return mCheckedState.get(position);
   }

   public void setItemChecked(int position, boolean checked) {
      mCheckedState.put(position, checked);
      notifyItemChanged(position);
   }

   public List<QuranRow> getCheckedItems() {
      final List<QuranRow> result = new ArrayList<>();
      final int count = mCheckedState.size();
      for (int i = 0; i < count; i++) {
         final int key = mCheckedState.keyAt(i);
         if (mCheckedState.get(key)) {
            result.add(getQuranRow(key));
         }
      }
      return result;
   }

   public void uncheckAll() {
      mCheckedState.clear();
      notifyDataSetChanged();
   }

   public void setElements(QuranRow[] elements){
      mElements = elements;
   }

   private void bindRow(HeaderHolder vh, int position) {
      ViewHolder holder = (ViewHolder) vh;

      final QuranRow item = mElements[position];
      bindHeader(vh, position);
      holder.number.setText(
              QuranUtils.getLocalizedNumber(mContext, item.sura));

      String info = item.metadata;
      holder.metadata.setVisibility(View.VISIBLE);
      holder.metadata.setText(ArabicStyle.reshape(info));

      if (item.juzType != null) {
         holder.image.setImageDrawable(
             new JuzView(mContext, item.juzType, item.juzOverlayText));
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
      }
   }

   private void bindHeader(HeaderHolder holder, int pos) {
      final QuranRow item = mElements[pos];
      String text = item.text;
      if (mReshapeArabic){
         text = ArabicStyle.reshape(text);
      }

      holder.title.setText(text);
      if (item.page == 0) {
         holder.pageNumber.setVisibility(View.GONE);
      } else {
         holder.pageNumber.setVisibility(View.VISIBLE);
         holder.pageNumber.setText(
             QuranUtils.getLocalizedNumber(mContext, item.page));
      }
      holder.setChecked(isItemChecked(pos));

      final boolean enabled = isEnabled(pos);
      holder.view.setEnabled(enabled);
   }

   @Override
   public HeaderHolder onCreateViewHolder(ViewGroup parent, int viewType) {
      if (viewType == 0) {
         final View view = mInflater.inflate(R.layout.index_header_row, parent, false);
         return new HeaderHolder(view);
      } else {
         final View view = mInflater.inflate(R.layout.index_sura_row, parent, false);
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
      return mElements[position].isHeader() ? 0 : 1;
   }

   public boolean isEnabled(int position) {
      final QuranRow selected = mElements[position];
      return mSelectableHeaders || selected.isBookmark() ||
          selected.rowType == QuranRow.NONE ||
          (selected.isBookmarkHeader() && selected.tagId >= 0);
   }

  public void setQuranTouchListener(QuranTouchListener listener) {
    mTouchListener = listener;
  }

  @Override
  public void onClick(View v) {
    final int position = mRecyclerView.getChildPosition(v);
    if (position != RecyclerView.NO_POSITION) {
      final QuranRow element = mElements[position];
      if (mTouchListener == null) {
        ((QuranActivity) mContext).jumpTo(element.page);
      } else {
        mTouchListener.onClick(element, position);
      }
    }
  }

  @Override
  public boolean onLongClick(View v) {
    if (mTouchListener != null) {
      final int position = mRecyclerView.getChildPosition(v);
      if (position != RecyclerView.NO_POSITION) {
        return mTouchListener.onLongClick(mElements[position], position);
      }
    }
    return false;
  }

  class HeaderHolder extends RecyclerView.ViewHolder {
      TextView title;
      TextView pageNumber;
      CheckableLinearLayout view;

      public HeaderHolder(View itemView) {
         super(itemView);
         view = (CheckableLinearLayout) itemView;
         title = (TextView) itemView.findViewById(R.id.title);
         pageNumber = (TextView) itemView.findViewById(R.id.pageNumber);

         if (mUseArabicFont){
            Typeface typeface = ArabicStyle.getTypeface(mContext);
            title.setTypeface(typeface);
         }
         itemView.setOnClickListener(QuranListAdapter.this);
         itemView.setOnLongClickListener(QuranListAdapter.this);
      }

      public void setChecked(boolean checked) {
         view.setChecked(checked);
      }
   }

  class ViewHolder extends HeaderHolder {
      TextView number;
      TextView metadata;
      ImageView image;

     public ViewHolder(View itemView) {
        super(itemView);
        metadata = (TextView) itemView.findViewById(R.id.metadata);
        number = (TextView) itemView.findViewById(R.id.suraNumber);
        image = (ImageView) itemView.findViewById(R.id.rowIcon);

        if (mUseArabicFont){
           Typeface typeface = ArabicStyle.getTypeface(mContext);
           metadata.setTypeface(typeface);
        }
     }
  }

  public interface QuranTouchListener {
    public void onClick(QuranRow row, int position);
    public boolean onLongClick(QuranRow row, int position);
  }
}
