package com.quran.labs.androidquran;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;

import com.google.gson.Gson;
import com.quran.labs.androidquran.common.DownloadItem;
import com.quran.labs.androidquran.common.InternetActivity;
import com.quran.labs.androidquran.common.TranslationItem;
import com.quran.labs.androidquran.common.TranslationsDBAdapter;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.util.RestClient;

public class DownloadActivity extends InternetActivity {

	private String url;
	private DownloadItem[] downloadItems;
	private ListView listView;
	private EfficientAdapter listAdapter;
	private ProgressDialog progressDialog;
	private LoadTranslationsTask currentTask;
	private TranslationsDBAdapter dba;
	public static final String WEB_SERVICE_URL =
		"http://labs.quran.com/androidquran/translations.php";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.download_list);
		listView = (ListView) findViewById(R.id.download_list);
		progressDialog = new ProgressDialog(this);
		progressDialog.setCancelable(true);
		progressDialog.setOnCancelListener(new OnCancelListener() {
			public void onCancel(DialogInterface dialog) {
				cancelDownload();
			}
		});
		url = savedInstanceState != null ? savedInstanceState.getString("url")
				: WEB_SERVICE_URL;
		dba = new TranslationsDBAdapter(getApplicationContext());
		connect();
	}

	@Override
	public void onBackPressed() {
		setResult(RESULT_OK);
		finish();
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event){
		if (QuranUtils.isSdk15() && keyCode == KeyEvent.KEYCODE_BACK
            && event.getRepeatCount() == 0) {
			onBackPressed();
		}
		return super.onKeyDown(keyCode, event);
	}
	
	@Override
	protected void onConnectionSuccess() {
		fetchTranslationsList();
	}

	private void fetchTranslationsList() {
		currentTask = new LoadTranslationsTask();
		currentTask.execute((Object[]) null);
	}

	private void populateList() {
		if (downloadItems == null)
			return;
		List<DownloadItem> alreadyDownloaded = new ArrayList<DownloadItem>();
		List<DownloadItem> available = new ArrayList<DownloadItem>();
		for (DownloadItem item : downloadItems) {
			if (item.isDownloaded())
				alreadyDownloaded.add(item);
			else
				available.add(item);
		}
		
		final List<TranslationListItem> listItems =
			new ArrayList<TranslationListItem>();
		String availableStr = getString(R.string.available_translations);
		String downloadedStr = getString(R.string.downloaded_translations);
		listItems.add(new TranslationListItem(downloadedStr));
		for (DownloadItem item : alreadyDownloaded)
			listItems.add(new TranslationListItem(item));
		listItems.add(new TranslationListItem(availableStr));
		for (DownloadItem item : available)
			listItems.add(new TranslationListItem(item));

		listAdapter = new EfficientAdapter(this, listItems);
		listView.setAdapter(listAdapter);
		listView.setOnItemClickListener(new OnItemClickListener(){
			@Override
			public void onItemClick(AdapterView<?> parent, final View view,
					final int position, long id) {
				final DownloadItem item = listItems.get(position).getItem();

				if (item == null) return;
				if (!QuranFileUtils.isSDCardMounted())
					showSDCardDialog();
				else if (item.isDownloaded())
					showRemoveItemDialog(item);
				else showDownloadItemDialog(item);
			}
		});
	}

	// http://www.androidpeople.com/android-custom-listview-tutorial-example/
	// also, http://android.amberfog.com/?p=296
	private static class EfficientAdapter extends BaseAdapter {
		private String activeTranslation;
		private LayoutInflater mInflater;
		private List<TranslationListItem> elements;
		private int TYPE_ITEM = 0;
		private int TYPE_SEPARATOR = 1;

		public EfficientAdapter(Context context,
				List<TranslationListItem> elements) {
			mInflater = LayoutInflater.from(context);
			this.elements = elements;
			this.activeTranslation =
				QuranSettings.getInstance().getActiveTranslation();
		}
		
		public void setActiveTranslation(String activeTranslation){
			this.activeTranslation = activeTranslation;
		}

		@Override
		public int getCount() {
			return elements.size();
		}
		
		@Override
	    public int getItemViewType(int position){
			return (elements.get(position).isSeparator())?
					TYPE_SEPARATOR : TYPE_ITEM;
	    }
	 
	    @Override
	    public int getViewTypeCount() {
	    	return 2;
	    }
	 

		@Override
		public Object getItem(int position) {
			return elements.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;
			
			if (convertView == null){
				holder = new ViewHolder();
				if (getItemViewType(position) == TYPE_ITEM){
					convertView = mInflater.inflate(
							R.layout.download_row, null);
					holder.translationTitle = (TextView)convertView
						.findViewById(R.id.translation_title);
					holder.translationInfo = (TextView)convertView
						.findViewById(R.id.translation_info);
					holder.activeCheckbox = (CheckBox)convertView
						.findViewById(R.id.active_checkbox);
				}
				else {
					convertView = mInflater.inflate(R.layout.list_sep, null);
					holder.separatorText = (TextView)convertView
						.findViewById(R.id.separator_txt);
				}
				convertView.setTag(holder);
			}
			else holder = (ViewHolder) convertView.getTag();

			DownloadItem item = elements.get(position).getItem();
			if (item == null){
				holder.separatorText.setText(
						elements.get(position).getSeparatorName());
			}
			else {
				holder.translationTitle.setText(item.getDisplayName());
				holder.translationInfo.setVisibility(View.GONE);

				boolean selected = item.getFileName().equals(activeTranslation);
				boolean downloaded = item.isDownloaded();
				if ((downloaded) && (selected)) {
					holder.activeCheckbox.setChecked(selected);
					holder.activeCheckbox.setVisibility(View.VISIBLE);
				} else {
					holder.activeCheckbox.setVisibility(View.GONE);
				}
			}
			
			return convertView;
		}

		static class ViewHolder {
			TextView translationTitle;
			TextView translationInfo;
			TextView separatorText;
			CheckBox activeCheckbox;
		}
	}

	class TranslationListItem {
		private String separatorName = null;
		private boolean isSeparator = false;
		private DownloadItem item = null;
		
		public TranslationListItem(DownloadItem item){
			this.item = item;
			this.separatorName = null;
			this.isSeparator = false;
		}
		
		public TranslationListItem(String separatorName){
			this.item = null;
			this.separatorName = separatorName;
			this.isSeparator = true;
		}
		
		public String getSeparatorName(){ return separatorName; }
		public boolean isSeparator(){ return isSeparator; }
		public DownloadItem getItem(){ return item; }
	}
	
	private void sendRequest() {
		JSONObject jsonObject;

		try {
			jsonObject = RestClient.connect(url, null, null);
			JSONArray jsonArray = jsonObject.getJSONArray("data");
			int nItems = jsonArray.length();
			downloadItems = new DownloadItem[nItems];
			Gson gson = new Gson();
			for (int i = 0; i < nItems; i++) {
				JSONObject json = jsonArray.getJSONObject(i);
				// Just for now use TranslationItem class..
				downloadItems[i] = gson.fromJson(json.toString(),
						TranslationItem.class);
				Log.i("QuranAndroid", "Load Translations: <jsonobject>\n"
						+ json.toString() + "\n</jsonobject>");
			}
		} catch (JSONException e) {
			// Show Error msg
			Log.d("JSON Exception", e.getMessage());
		} catch (NullPointerException e) {
			// Show Error msg
			Log.d("JSON Exception", "Empty message");
		}
	}

	private class LoadTranslationsTask extends
			AsyncTask<Object[], Object, Object> {

		public void onPreExecute() {
			super.onPreExecute();
			currentTask = this;
			progressDialog.show();
			String loadingStr = getString(R.string.loading_translations);
			progressDialog.setMessage(loadingStr);
		}

		public String doInBackground(Object[]... params) {
			sendRequest();
			if (downloadItems != null) {
				int nrecords = dba.deleteAllRecords();
				Log.i("Translations DB", "Deleted " + nrecords + " records");
				dba.save(downloadItems);
			}
			return null;
		}

		@Override
		public void onPostExecute(Object result) {
			super.onPostExecute(result);
			currentTask = null;
			progressDialog.hide();
			populateList();
		}
	}

	@Override
	protected void onFinishDownload() {
		super.onFinishDownload();
		setResult(RESULT_OK);
		populateList();
	}

	public void cancelDownload() {
		progressDialog.dismiss();
		currentTask.cancel(true);
	}
	
	public void showSDCardDialog(){
		AlertDialog.Builder builder =
			new AlertDialog.Builder(DownloadActivity.this);
		builder.setMessage(R.string.sdcard_required)
				.setIcon(android.R.drawable.alert_light_frame)
				.setCancelable(true);
		AlertDialog alert = builder.create();
		alert.show();
	}
	
	public void showDownloadItemDialog(final DownloadItem item){
		AlertDialog.Builder builder =
			new AlertDialog.Builder(DownloadActivity.this);
		String message = getString(R.string.download_a_translation,
				new Object[]{ item.getDisplayName() });
		builder.setMessage(message)
		       .setCancelable(true)
		       .setPositiveButton(R.string.download_button,
		    		   new DialogInterface.OnClickListener() {
		           		  public void onClick(DialogInterface dialog, int id) {
		           			  downloadTranslation(item.getFileUrl(),
		           					  item.getFileName());
		           			  dialog.dismiss();
		           		  }
		       		   });
		AlertDialog alert = builder.create();
		alert.show();
	}
	
	public void showRemoveItemDialog(final DownloadItem item){
		AlertDialog.Builder builder =
			new AlertDialog.Builder(DownloadActivity.this);
		String message = getString(R.string.translation_downloaded,
				new Object[]{ item.getDisplayName() });
		builder.setMessage(message)
		       .setCancelable(true)
		       .setPositiveButton(R.string.remove_button,
		    		   new DialogInterface.OnClickListener() {
		           		  public void onClick(DialogInterface dialog, int id) {
		           			QuranFileUtils.removeTranslation(item.getFileName());
		           			  populateList();
		           	   }
		       })
		       .setNegativeButton(R.string.set_active,
		    		   new DialogInterface.OnClickListener() {
		    	   		   public void onClick(DialogInterface dialog, int id){
		    	   			   QuranSettings.getInstance()
		    	   			   	  .setActiveTranslation(item.getFileName());
		    	   			   QuranSettings.save(prefs);
		    	   			   listAdapter.setActiveTranslation(
		    	   					   item.getFileName());
		    	   			   listAdapter.notifyDataSetChanged();
		    	   			   dialog.dismiss();
		    	   	   }
		       });
		AlertDialog alert = builder.create();
		alert.show();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		progressDialog.dismiss();
		if ((currentTask != null)
				&& (currentTask.getStatus() == Status.RUNNING))
			currentTask.cancel(true);
	}
}