package com.quran.labs.androidquran.ui.helpers;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.util.ArabicStyle;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.widgets.JuzView;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class QuranListAdapter extends BaseAdapter {

   private Context mContext;
   private LayoutInflater mInflater;
   private QuranRow[] mElements;
   private int mLayout;
   private boolean mReshapeArabic;
   private boolean mUseArabicFont;
   private boolean mSelectableHeaders;

   public QuranListAdapter(Context context, int layout,
       QuranRow[] items, boolean selectableHeaders){
      mInflater = LayoutInflater.from(context);
      mElements = items;
      mLayout = layout;
      mContext = context;
      mSelectableHeaders = selectableHeaders;

      // should we reshape if we have arabic?
      mUseArabicFont = QuranSettings.needArabicFont(context);
      mReshapeArabic = QuranSettings.isReshapeArabic(context);
   }

   public int getCount() {
      return mElements.length;
   }

   public Object getItem(int position) {
      return mElements[position];
   }

   public long getItemId(int position) {
      return position;
   }

   public void setElements(QuranRow[] elements){
      mElements = elements;
   }

   public View getView(final int position, View convertView, ViewGroup parent) {
      if (getItemViewType(position) == 0) {
         return getHeaderView(position, convertView, parent);
      }

      ViewHolder holder;
      if (convertView == null) {
         convertView = mInflater.inflate(mLayout, parent, false);
         holder = new ViewHolder();
         holder.text = (TextView) convertView.findViewById(R.id.suraName);
         holder.metadata = (TextView) convertView.findViewById(R.id.suraDetails);
         holder.page = (TextView) convertView.findViewById(R.id.pageNumber);
         holder.number = (TextView) convertView.findViewById(R.id.suraNumber);
         holder.image = (ImageView) convertView.findViewById(R.id.rowIcon);

         if (mUseArabicFont){
            Typeface typeface = ArabicStyle.getTypeface(mContext);
            holder.text.setTypeface(typeface);
            holder.metadata.setTypeface(typeface);
         }
         convertView.setTag(holder);
      }
      else { holder = (ViewHolder) convertView.getTag(); }

      QuranRow item = mElements[position];
      String text = item.text;
      if (mReshapeArabic){
         text = ArabicStyle.reshape(mContext, text);
      }

      holder.page.setText(QuranUtils.getLocalizedNumber(mContext, item.page));
      holder.text.setText(text);
      holder.number.setText(
              QuranUtils.getLocalizedNumber(mContext, item.sura));

      String info = item.metadata;
      holder.metadata.setVisibility(View.VISIBLE);
      holder.text.setVisibility(View.VISIBLE);
      holder.metadata.setText(ArabicStyle.reshape(mContext, info));

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
         holder.image.setVisibility(View.VISIBLE);
         holder.number.setVisibility(View.GONE);
      }

      // If the row is checked (for CAB mode), theme its bg appropriately
      if (parent != null && parent instanceof ListView){
         int background = ((ListView) parent).isItemChecked(position)?
                 R.color.accent_color_dark : 0;
         convertView.setBackgroundResource(background);
      }

      return convertView;
   }

   private View getHeaderView(int pos, View convertView, ViewGroup parent) {
      HeaderHolder holder;
      if (convertView == null) {
         convertView = mInflater.inflate(
             R.layout.index_header_row, parent, false);
         holder = new HeaderHolder();
         holder.header = (TextView)convertView.findViewById(R.id.title);
         holder.pageNumber = (TextView)convertView.findViewById(R.id.count);

         if (mUseArabicFont){
            Typeface typeface = ArabicStyle.getTypeface(mContext);
            holder.header.setTypeface(typeface);
         }
         convertView.setTag(holder);
      }
      else { holder = (HeaderHolder) convertView.getTag(); }

      final QuranRow item = mElements[pos];
      String text = item.text;
      if (mReshapeArabic){
         text = ArabicStyle.reshape(mContext, text);
      }

      holder.header.setText(text);
      if (item.page == 0) {
         holder.pageNumber.setVisibility(View.GONE);
      } else {
         holder.pageNumber.setVisibility(View.VISIBLE);
         holder.pageNumber.setText(
             QuranUtils.getLocalizedNumber(mContext, item.page));
      }

      // If the row is checked (for CAB mode), theme its bg appropriately
      if (parent != null && parent instanceof ListView){
         int background = ((ListView) parent).isItemChecked(pos)?
             R.color.accent_color_dark : R.drawable.list_header_background;
         convertView.setBackgroundResource(background);
      }
      return convertView;
   }

   @Override
   public int getViewTypeCount() {
      return 2;
   }

   @Override
   public int getItemViewType(int position) {
      return mElements[position].isHeader() ? 0 : 1;
   }

   @Override
  public boolean isEnabled(int position) {
    final QuranRow selected = mElements[position];
    return mSelectableHeaders || selected.isBookmark() ||
        selected.rowType == QuranRow.NONE ||
        (selected.isBookmarkHeader() && selected.tagId >= 0);
  }

   class HeaderHolder {
      TextView header;
      TextView pageNumber;
   }

  class ViewHolder {
      TextView text;
      TextView page;
      TextView number;
      TextView metadata;
      ImageView image;
   }
}
