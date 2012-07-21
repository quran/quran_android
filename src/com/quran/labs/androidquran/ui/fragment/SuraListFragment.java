package com.quran.labs.androidquran.ui.fragment;

import static com.quran.labs.androidquran.data.Constants.JUZ2_COUNT;
import static com.quran.labs.androidquran.data.Constants.PAGES_LAST;
import static com.quran.labs.androidquran.data.Constants.SURAS_COUNT;

import android.app.Activity;
import android.content.SharedPreferences;
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

public class SuraListFragment extends SherlockFragment {

   private ListView mListView;
   private QuranListAdapter mAdapter;
   
   public static SuraListFragment newInstance(){
      return new SuraListFragment();
   }
   
   @Override
   public View onCreateView(LayoutInflater inflater,
         ViewGroup container, Bundle savedInstanceState){
      View view = inflater.inflate(R.layout.quran_list, container, false);
      mListView = (ListView)view.findViewById(R.id.list);
      mAdapter = new QuranListAdapter(getActivity(),
            R.layout.index_sura_row, getSuraList());
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
      if (lastPage != Constants.NO_PAGE_SAVED &&
              lastPage >= Constants.PAGES_FIRST &&
              lastPage <= Constants.PAGES_LAST){
         int sura = QuranInfo.PAGE_SURA_START[lastPage-1];
         int juz = QuranInfo.getJuzFromPage(lastPage);
         int position = sura + juz - 1;
         mListView.setSelectionFromTop(position, 20);
      }

      super.onResume();
   }

   private QuranRow[] getSuraList(){
      int pos = 0;
      int sura = 1;
      int next = 1;
      QuranRow[] elements = new QuranRow[SURAS_COUNT + JUZ2_COUNT];

      Activity activity = getActivity();
      for (int juz=1; juz <= JUZ2_COUNT; juz++){
         elements[pos++] = new QuranRow(QuranInfo.getJuzTitle(activity) + " " +
               juz, null, QuranRow.HEADER, juz, QuranInfo.JUZ_PAGE_START[juz-1], null);
         next = (juz == JUZ2_COUNT) ? PAGES_LAST+1 :
            QuranInfo.JUZ_PAGE_START[juz];
         
         while ((sura <= SURAS_COUNT) &&
                (QuranInfo.SURA_PAGE_START[sura-1] < next)) {
            String title = QuranInfo.getSuraName(activity, sura, true);
            elements[pos++] = new QuranRow(title, 
                  QuranInfo.getSuraListMetaString(activity, sura),
                  sura, QuranInfo.SURA_PAGE_START[sura-1], null);
            sura++;
         }
      }

      return elements;
   }
}
