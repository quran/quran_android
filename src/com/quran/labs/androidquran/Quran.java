package com.quran.labs.androidquran;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.quran.labs.androidquran.R;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;

public class Quran extends ListActivity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.quran_list);
        showSuras();
    }
        
    private void showSuras(){
    	ArrayList< Map<String, String> > suraList =
    		new ArrayList< Map<String, String> >();
    	for (int i=0; i<114; i++){
    		String suraStr = (i+1) + ". Surat " + QuranInfo.SURA_NAMES[i];
    		Map<String, String> map = new HashMap<String, String>();
    		map.put("suraname", suraStr);
    		suraList.add(map);
    	}
    	
    	String[] from = new String[]{ "suraname" };
    	int[] to = new int[]{ R.id.surarow };
    	
    	SimpleAdapter suraAdapter =
    		new SimpleAdapter(this, suraList, R.layout.quran_row, from, to);
    	
    	setListAdapter(suraAdapter);
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id){
    	super.onListItemClick(l, v, position, id);
    	Intent i = new Intent(this, QuranView.class);
    	i.putExtra("page", QuranInfo.SURA_PAGE_START[(int)id]);
    	startActivity(i);
    }
}