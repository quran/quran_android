package com.quran.labs.androidquran.ui.helpers;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.util.ArabicStyle;

public class QuranListAdapter extends BaseAdapter {
   
   private Context mContext;
   private LayoutInflater mInflater;
   private QuranRow[] mElements;
   private int mLayout;
   
   public QuranListAdapter(Context context, int layout, QuranRow[] items){
       mInflater = LayoutInflater.from(context);
       mElements = items;
       mLayout = layout;
       mContext = context;
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
       ViewHolder holder;
       
       if (convertView == null) {
           convertView = mInflater.inflate(mLayout, null);
           holder = new ViewHolder();
           holder.text = (TextView)convertView.findViewById(R.id.suraName);
           holder.text.setTypeface(ArabicStyle.getTypeface());
           holder.metadata = (TextView)convertView.findViewById(R.id.suraDetails);
           holder.metadata.setTypeface(ArabicStyle.getTypeface());
           holder.page = (TextView)convertView.findViewById(R.id.pageNumber);
           holder.number = (TextView)convertView.findViewById(R.id.suraNumber);
           holder.header = (TextView)convertView.findViewById(R.id.headerName);
           holder.image = (TextView)convertView.findViewById(R.id.rowIcon);
           convertView.setTag(holder);
       }
       else { holder = (ViewHolder) convertView.getTag(); }

     QuranRow item = mElements[position];
       holder.page.setText(ArabicStyle.reshape(String.valueOf(item.page)));
       holder.text.setText(ArabicStyle.reshape(item.text));
       holder.header.setText(ArabicStyle.reshape(item.text));
       holder.number.setText(ArabicStyle.reshape(String.valueOf(item.number)));
       
       int color = R.color.sura_details_color;
       if (mElements[position].isHeader){
           holder.text.setVisibility(View.GONE);
           holder.header.setVisibility(View.VISIBLE);
           holder.metadata.setVisibility(View.GONE);
           holder.number.setVisibility(View.GONE);
           holder.image.setVisibility(View.GONE);
           color = R.color.header_text_color;
       }
       else {
           String info = item.metadata;
           holder.metadata.setVisibility(View.VISIBLE);
           holder.text.setVisibility(View.VISIBLE);
           holder.header.setVisibility(View.GONE);
           holder.metadata.setText(ArabicStyle.reshape(info));
           
           if (item.imageResource == null){
              holder.number.setVisibility(View.VISIBLE);
              holder.image.setVisibility(View.GONE);
           }
           else {
              holder.image.setBackgroundResource(item.imageResource);
              holder.image.setText(item.imageText);
              holder.image.setVisibility(View.VISIBLE);
              holder.number.setVisibility(View.GONE);
           }
       }
       holder.page.setTextColor(mContext.getResources().getColor(color));
       int pageVisibility = item.page == 0? View.GONE : View.VISIBLE;
       holder.page.setVisibility(pageVisibility);
       return convertView;
   }
         
   class ViewHolder {
       TextView text;
       TextView page;
       TextView number;
       TextView metadata;
       TextView header;
       TextView image;
   }
}
