package com.quran.labs.androidquran.ui.fragment;

import static com.quran.labs.androidquran.data.Constants.JUZ2_COUNT;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockFragment;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.ui.QuranActivity;
import com.quran.labs.androidquran.ui.helpers.QuranListAdapter;
import com.quran.labs.androidquran.ui.helpers.QuranRow;

public class JuzListFragment extends SherlockFragment {

   private ListView mListView;
   private QuranListAdapter mAdapter;
   
   public static JuzListFragment newInstance(){
      return new JuzListFragment();
   }
   
   @Override
   public View onCreateView(LayoutInflater inflater,
         ViewGroup container, Bundle savedInstanceState){
      View view = inflater.inflate(R.layout.quran_list, container, false);
      mListView = (ListView)view.findViewById(R.id.list);
      mAdapter = new QuranListAdapter(getActivity(),
            R.layout.index_sura_row, getJuz2List());
      mListView.setAdapter(mAdapter);
      
      mListView.setOnItemClickListener(new OnItemClickListener(){
         public void onItemClick(AdapterView<?> parent, View v,
               int position, long id){
            QuranRow elem = (QuranRow)mAdapter.getItem((int)id);
            if (elem.page > 0){
               ((QuranActivity)getActivity()).jumpTo(elem.page);
            }
         }
      });
      
      return view;
   }
   
   @Override
   public void onResume() {
      SharedPreferences prefs = PreferenceManager
            .getDefaultSharedPreferences(
                  getActivity().getApplicationContext());
      int lastPage = prefs.getInt(Constants.PREF_LAST_PAGE,
            Constants.NO_PAGE_SAVED);
      if (lastPage != Constants.NO_PAGE_SAVED){
         int juz = QuranInfo.getJuzFromPage(lastPage);
         int position = (juz - 1) * 9;
         mListView.setSelectionFromTop(position, 20);
      }

      super.onResume();
   }
   
   private QuranRow[] getJuz2List() {
      Activity activity = getActivity();
      int[] images = { R.drawable.hizb_full, R.drawable.hizb_quarter,
                       R.drawable.hizb_half, R.drawable.hizb_threequarters };
      Resources res = getResources();
      String[] quarters = res.getStringArray(R.array.quarter_prefix_array);
      QuranRow[] elements = new QuranRow[JUZ2_COUNT * (8 + 1)];
      
      int ctr = 0;
      for (int i = 0; i < (8 * JUZ2_COUNT); i++){
         int[] pos = QuranInfo.QUARTERS[i];
         int page = QuranInfo.getPageFromSuraAyah(pos[0], pos[1]);
         
         if (i % 8 == 0){
            int juz = 1 + (i / 8);
            elements[ctr++] = new QuranRow(QuranInfo.getJuzTitle(activity) +
                    " " + juz, null, QuranRow.HEADER, juz,
                    QuranInfo.JUZ_PAGE_START[juz-1], null);
         }
         String verseString = getString(R.string.quran_ayah) + " " + pos[1];
         elements[ctr++] = new QuranRow(quarters[i],
               QuranInfo.getSuraName(activity, pos[0], true) +
                       ", " + verseString, 0, page, images[i % 4]);
         if (i % 4 == 0)
            elements[ctr-1].imageText = (1 + (i / 4)) + "";
      }
      
      return elements;
   }
}
