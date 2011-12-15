package com.quran.labs.androidquran;


import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.HashMap;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.TextView;

import com.quran.labs.androidquran.common.QuranReader;
import com.quran.labs.androidquran.data.ApplicationConstants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.util.ArabicStyle;
import com.quran.labs.androidquran.util.QuranUtils;

public class AudioManagerActivity extends Activity{

	private class ReaderStatus{
		private QuranReader reader;
		private Integer totalAyasDownload = new Integer(0);
		private short getDownloadStatus(){
			if(totalAyasDownload == 0)
				return 0;
			if(totalAyasDownload == QuranInfo.getNumAyahs())
				return 1;
			return -1;  // partially downloaded
		}
	}
	
	private class SouraStatus{
		private Integer suraIndex = new Integer(0);
		private Integer ayasDownloaded = new Integer(0);
		private short getDownloadStatus(){
			if(ayasDownloaded == 0)
				return 0;
			if(ayasDownloaded == QuranInfo.getNumAyahs(suraIndex))
				return 1;
			return -1; // partially downloaded
		}
	}
	
	private ExpandableListView lst = null;
	private CheckReaderDownloadStatus task;
	private ProgressDialog dialog;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.audio_download_status);
        lst = (ExpandableListView) findViewById(R.id.expandableListView);
        dialog = new ProgressDialog(this);
        dialog.setTitle("Loading");
        dialog.setMessage("Please wait, while reading data from your sdcard");
        task = new CheckReaderDownloadStatus();
        dialog.show();
        task.execute();
	}
	
	
	private class CheckReaderDownloadStatus extends AsyncTask<Void, Void, Object[]>{

		@Override
		protected Object[] doInBackground(Void... params) {
			
			int[] readersIds = getResources().getIntArray(R.array.quran_readers_id);
			String[] readersNames = getResources().getStringArray(R.array.quran_readers_name);
			ArrayList<ReaderStatus>groups = new ArrayList<ReaderStatus>();
			HashMap<ReaderStatus, SouraStatus[]> statuses = new HashMap<ReaderStatus, SouraStatus[]>();
			for(int i = 0; i < readersIds.length; i++){
				ReaderStatus readerStatus = new ReaderStatus();
				groups.add(readerStatus);
				readerStatus.reader = new QuranReader(readersIds[i], readersNames[i], "");
				int readerId = readerStatus.reader.getId();
				SouraStatus[] sourasStatuses = new SouraStatus[ApplicationConstants.SURAS_COUNT];
				statuses.put(readerStatus, sourasStatuses);
				//int j = 0;
				for (int j = 0; j < sourasStatuses.length ; j++) {
					sourasStatuses[j] = new SouraStatus();
					sourasStatuses[j].suraIndex = new Integer(j+1);
					sourasStatuses[j].ayasDownloaded = new Integer(0);
				}
				File f = new File(QuranUtils.getReaderAudioDirectory(readerId));
				File[] directories = f.listFiles(new FileFilter() {

					@Override
					public boolean accept(File file) {
						if(file.isDirectory())
						try{
							int suraIndex = Integer.parseInt(file.getName());
							if(suraIndex > 0 && suraIndex <= 114)
								return true;
						}catch(Exception e){
							return false;
						}
						return false;
					}
				});
				readerStatus.totalAyasDownload = 0;
				if (directories != null)
					for (File file : directories) {
						int suraIndex = Integer.parseInt(file.getName());
						sourasStatuses[suraIndex - 1].ayasDownloaded 
						 = file.listFiles(new FilenameFilter() {

							@Override
							public boolean accept(File dir, String filename) {
								String[] fullName = filename.split("\\.");
								try {
									if (fullName.length == 2
											&& "mp3"
													.equalsIgnoreCase(fullName[1])
											&& Integer.parseInt(fullName[0]) > 0)
										return true;
									return false;
								} catch (Exception e) {
									return false;
								}
							}
						}).length;
						readerStatus.totalAyasDownload 
							+= sourasStatuses[suraIndex - 1].ayasDownloaded;
					}
			}
			return new Object[]{groups, statuses};
		}
		
		@SuppressWarnings("unchecked")
		protected void onPostExecute(Object[] result) {
			ExpandableListAdapter adapter = new ExpandableListAdapter(getBaseContext(), 
					(ArrayList<ReaderStatus>)result[0],(HashMap<ReaderStatus, SouraStatus[]>) result[1]);
			lst.setAdapter(adapter);
			dialog.dismiss();
		}
		
	}
	
	public class ExpandableListAdapter extends BaseExpandableListAdapter {

	    @Override
	    public boolean areAllItemsEnabled()
	    {
	        return true;
	    }

	    private Context context;

	    private ArrayList<ReaderStatus> readers;
	    private HashMap<ReaderStatus, SouraStatus[]> elements;

	    public ExpandableListAdapter(Context context, ArrayList<ReaderStatus> readers,
	    		HashMap<ReaderStatus, SouraStatus[]> elements) {	    	
	        this.context = context;
	        this.readers = readers;
	        this.elements = elements;
	    }

	    /**
	     * A general add method, that allows you to add a Vehicle to this list
	     * 
	     * Depending on if the category opf the vehicle is present or not,
	     * the corresponding item will either be added to an existing group if it 
	     * exists, else the group will be created and then the item will be added
	     * @param vehicle
	     */

	    @Override
	    public SouraStatus getChild(int groupPosition, int childPosition) {
	    	ReaderStatus reader = readers.get(groupPosition);
	    	SouraStatus[] souras = elements.get(reader);
	    	return souras[childPosition];
	    }

	    @Override
	    public long getChildId(int groupPosition, int childPosition) {
	        SouraStatus soura = getChild(groupPosition, childPosition);
	        return soura.suraIndex;
	    }
	    
	    // Return a child view. You can load your custom layout here.
	    @Override
	    public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
	            View convertView, ViewGroup parent) {
	    	SouraStatus soura = getChild(groupPosition, childPosition);
	        String suraName = QuranInfo.getSuraName(soura.suraIndex - 1);
	        if (convertView == null) {
	            LayoutInflater infalInflater = (LayoutInflater) context
	                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	            convertView = infalInflater.inflate(R.layout.soura_row, null);
	        }
	        TextView tv = (TextView) convertView.findViewById(R.id.suraName);
	        tv.setText(ArabicStyle.reshape(suraName));
	      
	        TextView status = (TextView) convertView.findViewById(R.id.suraDownloadStatus);
	        status.setText(""+soura.ayasDownloaded);
	        
	        View v = convertView.findViewById(R.id.souraRow);
	        switch (soura.getDownloadStatus()) {
			case -1:
				v.setBackgroundColor(0x6600FCEB);
				break;
			case 1:
				v.setBackgroundColor(0x3300EE00);
				break;
			case 0:
				v.setBackgroundColor(0x000000);
				break;
			default:
				break;
			}
	        
	        return convertView;
	    }

	    @Override
	    public int getChildrenCount(int groupPosition) {
	    	ReaderStatus reader = readers.get(groupPosition);
	    	SouraStatus[] souras = elements.get(reader);
	        return souras.length;
	    }

	    @Override
	    public ReaderStatus getGroup(int groupPosition) {
	        return readers.get(groupPosition);
	    }

	    @Override
	    public int getGroupCount() {
	        return readers.size();
	    }

	    @Override
	    public long getGroupId(int groupPosition) {
	    	return getGroup(groupPosition).reader.getId();
	    }

	    // Return a group view. You can load your custom layout here.
	    @Override
	    public View getGroupView(int groupPosition, boolean isExpanded, View convertView,
	            ViewGroup parent) {
	    	ReaderStatus readerStatus = getGroup(groupPosition);
	    	String readerName = readerStatus.reader.getName();
	        if (convertView == null) {
	            LayoutInflater infalInflater = (LayoutInflater) context
	                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	            convertView = infalInflater.inflate(R.layout.reader_row, null);
	        }
	        TextView tv = (TextView) convertView.findViewById(R.id.readerName);
	        tv.setText(readerName);
	        
	        TextView status = (TextView) convertView.findViewById(R.id.downloadStatus);
	        status.setText(""+readerStatus.totalAyasDownload);
	        
	        View v = convertView.findViewById(R.id.readerRow);
	        switch (readerStatus.getDownloadStatus()) {
	        case -1:
				v.setBackgroundColor(0x6600FCEB);
				break;
			case 1:
				v.setBackgroundColor(0x3300EE00);
				break;		
			case 0:
				v.setBackgroundColor(0x000000);
				break;
			}
	        return convertView;
	    }

	    @Override
	    public boolean hasStableIds() {
	        return true;
	    }

	    @Override
	    public boolean isChildSelectable(int arg0, int arg1) {
	        return true;
	    }

	}


}
