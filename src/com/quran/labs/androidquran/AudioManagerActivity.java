package com.quran.labs.androidquran;


import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnGroupExpandListener;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.TextView;

import com.quran.labs.androidquran.common.InternetActivity;
import com.quran.labs.androidquran.common.QuranReader;
import com.quran.labs.androidquran.data.ApplicationConstants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.util.ArabicStyle;
import com.quran.labs.androidquran.util.QuranUtils;

public class AudioManagerActivity extends InternetActivity implements OnCheckedChangeListener,
	android.widget.CompoundButton.OnCheckedChangeListener, OnClickListener{

	private static final int FILTER_SHOW_ALL = R.id.radioShowAll;
	private static final int FILTER_SHOW_DOWNALOADED = R.id.radioDownloaded;
	private static final int FILTER_SHOW_MISSING = R.id.radioMissing;
	private static final int FILTER_SHOW_PARTIALLY_DOWNLOADED = R.id.radioPartiallyDownloaded;
	
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
	private ExpandableListAdapter adapter;
	private CheckReaderDownloadStatus task;
	private ProgressDialog dialog;
	private RadioGroup radioFilterReaders = null;
	private RadioGroup radioFilterSuras = null;
	private CheckBox chkFilter = null;
	private Button btnDownload = null;
	private Button btnRemove = null;
	private View filterView = null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.audio_download_status);
        lst = (ExpandableListView) findViewById(R.id.expandableListView);
        
        radioFilterReaders = (RadioGroup) findViewById(R.id.radioFilterReader);
        radioFilterReaders.setOnCheckedChangeListener(this);
        
        radioFilterSuras = (RadioGroup) findViewById(R.id.radioFilterSuras);
        radioFilterSuras.setOnCheckedChangeListener(this);
        
        btnDownload = (Button) findViewById(R.id.btnDownload);
        btnRemove = (Button) findViewById(R.id.btnRemove);
        chkFilter = (CheckBox) findViewById(R.id.btnFilter);
        
        btnDownload.setOnClickListener(this);
        btnRemove.setOnClickListener(this);
        
        filterView = findViewById(R.id.filters);
        
        chkFilter.setOnCheckedChangeListener(this);
        
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
			final ExpandableListAdapter adapter = new ExpandableListAdapter(getBaseContext(), 
					(ArrayList<ReaderStatus>)result[0],(HashMap<ReaderStatus, SouraStatus[]>) result[1]);
			AudioManagerActivity.this.adapter = adapter;
			lst.setAdapter(adapter);
			lst.setOnGroupExpandListener(new OnGroupExpandListener() {
				
				@Override
				public void onGroupExpand(int groupPosition) {
					int groupCount = adapter.getGroupCount();
					for(int i = 0; i <groupCount ; i++ )
						if(i != groupPosition)
							lst.collapseGroup(i);
				}
			});
			dialog.dismiss();
		}
		
	}
	
	
	public class ExpandableListAdapter extends BaseExpandableListAdapter implements android.widget.CompoundButton.OnCheckedChangeListener {

		private HashMap<Integer, Boolean> checkedItems = 
				new HashMap<Integer, Boolean>();
	    @Override
	    public boolean areAllItemsEnabled()
	    {
	        return true;
	    }
	    
	    
	    public void filterReaders(int filter){
	    	switch (filter) {
			case FILTER_SHOW_ALL:	
				readers = allReaders;
				break;
			case FILTER_SHOW_MISSING:
				readers = missingReaders;
				break;
			case FILTER_SHOW_DOWNALOADED:
				readers = completedReaders;
				break;
			case FILTER_SHOW_PARTIALLY_DOWNLOADED:
				readers = partialReaders;
				break;
			default:
				break;
			}
	    	super.notifyDataSetChanged();
	    }
	    
	    public void filterSuras(int filter){
	    	switch (filter) {
			case FILTER_SHOW_ALL:	
				elements = allElements;
				break;
			case FILTER_SHOW_MISSING:
				elements = missingElements;
				break;
			case FILTER_SHOW_DOWNALOADED:
				elements = completedElements;
				break;
			case FILTER_SHOW_PARTIALLY_DOWNLOADED:
				elements = partialElements;
				break;
			default:
				break;
			}
	    	super.notifyDataSetChanged();	    	
	    }
	    
	    private Context context;

	    private ArrayList<ReaderStatus> readers;
	    private HashMap<ReaderStatus, SouraStatus[]> elements;

	    private ArrayList<ReaderStatus> allReaders;
	    private HashMap<ReaderStatus, SouraStatus[]> allElements;
	    
	    private ArrayList<ReaderStatus> completedReaders =
	    		new ArrayList<AudioManagerActivity.ReaderStatus>();
	    
	    private ArrayList<ReaderStatus> missingReaders = 
	    		new ArrayList<AudioManagerActivity.ReaderStatus>();
	    
	    private ArrayList<ReaderStatus> partialReaders =
	    		new ArrayList<AudioManagerActivity.ReaderStatus>();
	 
	    private HashMap<ReaderStatus, SouraStatus[]> completedElements = 
	    		new HashMap<AudioManagerActivity.ReaderStatus, AudioManagerActivity.SouraStatus[]>();
	 
	    private HashMap<ReaderStatus, SouraStatus[]> missingElements = 
	    		new HashMap<AudioManagerActivity.ReaderStatus, AudioManagerActivity.SouraStatus[]>();
	 
	   private HashMap<ReaderStatus, SouraStatus[]> partialElements =
			   new HashMap<AudioManagerActivity.ReaderStatus, AudioManagerActivity.SouraStatus[]>();
	    		
	    public ExpandableListAdapter(Context context, ArrayList<ReaderStatus> readers,
	    		HashMap<ReaderStatus, SouraStatus[]> elements) {	    	
	        this.context = context;
	        this.readers = readers;
	        this.elements = elements;
	        
	        this.allElements = elements;
	        this.allReaders = readers;
	        
	        for (ReaderStatus status : readers) {
	        	if(status.getDownloadStatus() == 1){
	        		completedReaders.add(status);
	        	} else if (status.getDownloadStatus() == 0){
	        		missingReaders.add(status);
	        	} else {// some downloaded and some missing
	        		partialReaders.add(status);
	        	}
	        	
	        	
        		ArrayList<SouraStatus> completed = new ArrayList<AudioManagerActivity.SouraStatus>();
        		ArrayList<SouraStatus> missing = new ArrayList<AudioManagerActivity.SouraStatus>();
        		ArrayList<SouraStatus> partially = new ArrayList<AudioManagerActivity.SouraStatus>();
        		for (SouraStatus s : allElements.get(status)) {
					if(s.getDownloadStatus() == 1){
						completed.add(s);
					} else if (s.getDownloadStatus() == 0){
						missing.add(s);
					} else{
						partially.add(s);
					}
				}
        		SouraStatus[] statuses = new SouraStatus[completed.size()];
        		int i = 0;
        		for (SouraStatus s : completed) {
					statuses[i++] = s;
				}
        		missingElements.put(status, missing.toArray(new SouraStatus[missing.size()]));
        		completedElements.put(status, completed.toArray(new SouraStatus[completed.size()]));
        		partialElements.put(status, partially.toArray(new SouraStatus[partially.size()]));
			}
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
	        status.setText(""+soura.ayasDownloaded + "/" + QuranInfo.getNumAyahs(soura.suraIndex));
	        
	        CheckBox checkbox = (CheckBox) convertView.findViewById(R.id.chkSelectSura);
	        int id = getGroup(groupPosition).reader.getId() * 1000 + soura.suraIndex;
	        checkbox.setTag(id);
	        checkbox.setOnCheckedChangeListener(this);
	        
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
	        status.setText(""+readerStatus.totalAyasDownload + "/" + QuranInfo.getNumAyahs());
	        
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


		@Override
		public void onCheckedChanged(CompoundButton chkButton,
				boolean isChecked) {
			try {
				int id = (Integer) chkButton.getTag();
				if (isChecked) {
					checkedItems.put(id, true);
				} else {
					checkedItems.remove(id);
				}
			} catch (Exception e) {
			}
		}
		
		public Collection<Integer> getCheckedItems(){
			return checkedItems.keySet();
		}
		
	}

	@Override
	public void onCheckedChanged(RadioGroup radio, int newId) {
		if(adapter == null) return;
		switch (radio.getId()) {
		case R.id.radioFilterReader:
			adapter.filterReaders(newId);			
			if((newId==FILTER_SHOW_ALL 
					|| newId == FILTER_SHOW_PARTIALLY_DOWNLOADED))
				radioFilterSuras.setVisibility(View.VISIBLE);
			else
				radioFilterSuras.setVisibility(View.GONE);
			break;
		case R.id.radioFilterSuras:
			adapter.filterSuras(newId);
			break;
		default:
			break;
		}		
	}

	@Override
	public void onCheckedChanged(CompoundButton chk, boolean isChecked) {
		if(chk.getId() == chkFilter.getId()){
			if(isChecked)
				filterView.setVisibility(View.VISIBLE);
			else
				filterView.setVisibility(View.GONE);
		}
	}

	@Override
	public void onClick(View v) {
		if(adapter == null)
			return;
		Collection<Integer> items = adapter.getCheckedItems();
		switch (v.getId()) {
		case R.id.btnDownload:
			for (Integer id : items) {
				int sura = id % 1000;
				int readerId = id / 1000;
				downloadSura(readerId, sura);
			}
			break;
		case R.id.btnRemove:			
			break;
		default:
			break;
		}
	}


}
