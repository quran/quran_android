package com.quran.labs.androidquran;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

import com.google.gson.Gson;
import com.quran.labs.androidquran.common.BaseQuranActivity;
import com.quran.labs.androidquran.common.DownloadItem;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.util.RestClient;

public class DownloadActivity extends BaseQuranActivity {
	
	public static final String URL_KEY = "url";
	public static final String LOCALHOST_TEST_URL = "http://192.168.1.2/quran_ws/translations.php";
	private String url;
	private DownloadItem [] downloadItems;
	private ListView listView;
	private ProgressDialog progressDialog;
	public QuranAsyncTask currentTask;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.download_list);
        listView = (ListView) findViewById(R.id.download_list);
        progressDialog = new ProgressDialog(this);
        
        // Check for url sent in savedInstance
        url = savedInstanceState != null ? savedInstanceState.getString("url") : LOCALHOST_TEST_URL;
                
        new LoadTranslationsTask().execute((Object []) null);
	}
	
	private void populateList() {
		// Set up column mappings
		String [] dataColumns = new String [] {"displayName", "is_downloaded"};
		int [] dataColumnsIds = new int [] {R.id.display_name, R.id.is_downloaded, R.id.remove_translation};
		
		// Now iterate on all records
		List<HashMap<String, String>> lst = new ArrayList<HashMap<String,String>>();
		for (int i = 0; i < downloadItems.length; i++) {
			HashMap<String, String> map = new HashMap<String, String>();
			map.put("displayName", downloadItems[i].getDisplay_name());
			map.put("is_downloaded",downloadItems[i].isDownloaded() ? "Downloaded" : "");
			lst.add(map);
		}
		
		SimpleAdapter adapter = new SimpleAdapter(this, lst, R.layout.download_row, dataColumns, dataColumnsIds);
		listView.setAdapter(adapter);
		listView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				new DownloadTranslationsTask().execute(new String[] {downloadItems[position].getFile_url()});
			}
		});
	}
	
	private void sendRequest() {
		JSONObject jsonObject = RestClient.connect(url, null, null);
		// convert json object to Download item array
		
		try{
	        JSONArray jsonArray = jsonObject.getJSONArray("data");
	        int nItems = jsonArray.length();
	        downloadItems = new DownloadItem[nItems];
	        Gson gson = new Gson();
	        for (int i = 0; i < nItems; i++) {
	        	JSONObject json = jsonArray.getJSONObject(i);                	
	        	downloadItems[i] = gson.fromJson(json.toString(), DownloadItem.class);
	            Log.i("Praeda","<jsonobject>\n"+json.toString()+"\n</jsonobject>");
	        }	
		} catch (JSONException e) {
			// Show Error msg
			Log.d("JSON Exception", e.getMessage());
		} catch (NullPointerException e){
			// Show Error msg
			Log.d("JSON Exception", e.getMessage());
		}
	}
	
	private abstract class QuranAsyncTask extends AsyncTask<Object [], Object, Object>  {
		protected void onPreExecute() {
			currentTask = this;
			progressDialog.show();
		}
		
		@Override
    	public void onPostExecute(Object result){
    		currentTask = null;
    		progressDialog.hide();
    	}
	}
	
	private class LoadTranslationsTask extends QuranAsyncTask {
		public void onPreExecute() {
			super.onPreExecute();
			progressDialog.setMessage("Loading Translations List, Please wait..");
		}
		
    	public String doInBackground(Object[]... params){
    		sendRequest();
    		return null;
    	}
    	    	
    	@Override
    	public void onPostExecute(Object result){
    		super.onPostExecute(result);
    		populateList();
    	}
    }
	
	private class DownloadTranslationsTask extends QuranAsyncTask {
		public void onPreExecute() {
			super.onPreExecute();
			progressDialog.setMessage("Downloading Translation, Please wait..");
		}
		
    	public String doInBackground(Object[]... params){
    		Integer numDownloads = 0;
    		
    		Object[] translations = (Object[]) params[0];
    		for (Object dbName : translations){
    			if (QuranUtils.getTranslation((String)dbName))
    				numDownloads++;
    		}
    		return null;
    	}
    	    	
    	@Override
    	public void onPostExecute(Object result){
    		super.onPostExecute(result);
    		currentTask = null;
    		Toast.makeText(DownloadActivity.this, "File Downloaded", Toast.LENGTH_SHORT);
    	}
    }

}
