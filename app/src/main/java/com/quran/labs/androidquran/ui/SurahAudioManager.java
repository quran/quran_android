package com.quran.labs.androidquran.ui;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.view.ActionMode;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.quran.labs.androidquran.QuranApplication;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.QariItem;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.service.QuranDownloadService;
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver;
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier;
import com.quran.labs.androidquran.service.util.ServiceIntentHelper;
import com.quran.labs.androidquran.util.AudioManagerUtils;
import com.quran.labs.androidquran.util.AudioUtils;
import com.quran.labs.androidquran.util.QariDownloadInfo;
import com.quran.labs.androidquran.util.QuranFileUtils;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.inject.Inject;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.observers.DisposableSingleObserver;

public class SurahAudioManager extends QuranActionBarActivity
    implements DefaultDownloadReceiver.SimpleDownloadListener{

  public static final String EXTRA_SHEIKH_POSITION = "SheikhPosition";

  private static final String AUDIO_DOWNLOAD_KEY = "SurahAudioManager.DownloadKey";

  private ProgressBar progressBar;
  private Disposable disposable;
  private RecyclerView recyclerView;
  private DefaultDownloadReceiver downloadReceiver;
  private String basePath;
  private List<QariItem> qariItems;
  private SurahAdapter surahAdapter;
  private int sheikhPosition = -1;
  private ActionMode actionMode;


  @Inject
  QuranInfo quranInfo;
  @Inject
  QuranFileUtils quranFileUtils;
  @Inject
  AudioUtils audioUtils;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    QuranApplication quranApp = (QuranApplication) getApplication();
    quranApp.getApplicationComponent().inject(this);
    quranApp.refreshLocale(this, false);

    super.onCreate(savedInstanceState);
    final ActionBar ab = getSupportActionBar();
    if (ab != null) {
      ab.setTitle(R.string.audio_manager);
      ab.setDisplayHomeAsUpEnabled(true);
    }

    setContentView(R.layout.activity_surah_audio_manager);

    Intent intent = getIntent();
    sheikhPosition = intent.getIntExtra(EXTRA_SHEIKH_POSITION, -1);

    recyclerView = findViewById(R.id.recycler_view);
    recyclerView.setHasFixedSize(true);
    recyclerView.setLayoutManager(new LinearLayoutManager(this));
    recyclerView.setItemAnimator(new DefaultItemAnimator());

    progressBar = findViewById(R.id.progress);
    qariItems = audioUtils.getQariList(this);
    basePath = quranFileUtils.getQuranAudioDirectory(this);
    surahAdapter = new SurahAdapter(qariItems, this);
    recyclerView.setAdapter(surahAdapter);
    getShuyookhData();
  }

  @Override
  protected void onResume() {
    super.onResume();
    downloadReceiver = new DefaultDownloadReceiver(this,
        QuranDownloadService.DOWNLOAD_TYPE_AUDIO);
    downloadReceiver.setCanCancelDownload(true);
    LocalBroadcastManager.getInstance(this).registerReceiver(downloadReceiver,
        new IntentFilter(QuranDownloadNotifier.ProgressIntent.INTENT_NAME));
    downloadReceiver.setListener(this);
  }

  @Override
  protected void onPause() {
    downloadReceiver.setListener(null);
    LocalBroadcastManager.getInstance(this).unregisterReceiver(downloadReceiver);
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    disposable.dispose();
    super.onDestroy();
  }

  private DisposableSingleObserver<List<QariDownloadInfo>> downloadInfoObserver;

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.surah_audio_manager_menu, menu);
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int itemId = item.getItemId();
    switch(itemId) {
      case android.R.id.home:
        finish();
        return true;

      case R.id.download_all:
        QariDownloadInfo info = surahAdapter.getSheikhInfoForPosition(sheikhPosition);
        if(info.downloadedSuras.size() != 114) {
          download(1, 114);
        }
    }
    return super.onOptionsItemSelected(item);
  }

  private void getShuyookhData() {
    if (disposable != null) {
      disposable.dispose();
    }
    downloadInfoObserver =
        new DisposableSingleObserver<List<QariDownloadInfo>>() {
          @Override
          public void onSuccess(List<QariDownloadInfo> downloadInfo) {
            progressBar.setVisibility(View.GONE);
            surahAdapter.setDownloadInfo(downloadInfo);
            surahAdapter.notifyDataSetChanged();
          }

          @Override
          public void onError(Throwable e) {
          }
        };
    disposable = AudioManagerUtils.shuyookhDownloadObservable(quranInfo, basePath, qariItems)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeWith(downloadInfoObserver);
  }

  private ActionMode.Callback actionModeCallback = new ActionMode.Callback() {
    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
      mode.getMenuInflater().inflate(R.menu.surah_audio_manager_contextual_menu, menu);
      return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
      return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
      switch(item.getItemId()) {
        case R.id.cab_download:
          return true;

        case R.id.cab_delete:
          return true;
      }
      return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
      surahAdapter.uncheckAll();
      actionMode = null;
    }
  };

  private boolean isInActionMode() {
    return actionMode != null;
  }

  private void finishActionMode() {
    if(isInActionMode()) {
      actionMode.finish();
    }
  }

  private View.OnLongClickListener onLongClickListener = new View.OnLongClickListener() {

    @Override
    public boolean onLongClick(View view) {
      if(isInActionMode()) {
        return false;
      }

      int position = recyclerView.getChildAdapterPosition(view);
      if(position == RecyclerView.NO_POSITION) {
        return false;
      }

      actionMode = startSupportActionMode(actionModeCallback);

      surahAdapter.setItemChecked(position, true);
      return true;
    }
  };

  private View.OnClickListener mOnClickListener = new View.OnClickListener() {
    @Override
    public void onClick(View v) {
      int position = recyclerView.getChildAdapterPosition(v);
      if(position == RecyclerView.NO_POSITION) {
        return;
      }

      if(isInActionMode()) {
        surahAdapter.toggleItemChecked(position);
        return;
      }

      QariDownloadInfo info = surahAdapter.getSheikhInfoForPosition(sheikhPosition);
      int surah = position + 1;
      boolean downloaded = info.downloadedSuras.get(surah);
      if(downloaded) {
        // TODO: show a confirmation dialog before deleting
        delete(surah);
      } else {
        download(surah, surah);
      }
    }
  };

  private void delete(int surah) {
    QariItem qariItem = qariItems.get(sheikhPosition);
    String baseUri = basePath + qariItem.getPath();
    String fileUri = audioUtils.getLocalQariUri(this, qariItem);

    boolean deletionSuccessful = true;

    if(qariItem.isGapless()) {
      String fileName = String.format(Locale.US, fileUri, surah);
      File audioFile = new File(fileName);
      deletionSuccessful = audioFile.delete();
    } else {
      int numAyahs = quranInfo.getNumAyahs(surah);
      for(int i=1; i<= numAyahs; ++i) {
        String fileName = String.format(Locale.US, fileUri, surah, i);
        File ayahAudioFile = new File(fileName);
        if(ayahAudioFile.exists()) {
          deletionSuccessful = deletionSuccessful && ayahAudioFile.delete();
        }
      }
    }

    String resultString;
    if(deletionSuccessful) {
      resultString = getString(R.string.audio_manager_delete_surah_success);
      AudioManagerUtils.clearCacheKeyForSheikh(qariItem);
      getShuyookhData();
    } else {
      resultString = getString(R.string.audio_manager_delete_surah_error);
    }
    Toast.makeText(this, resultString, Toast.LENGTH_SHORT).show();
  }

  private void download(int startSurah, int endSurah) {
    QariItem qariItem = qariItems.get(sheikhPosition);
    String baseUri = basePath + qariItem.getPath();
    boolean isGapless = qariItem.isGapless();

    String sheikhName = qariItem.getName();
    Intent intent = ServiceIntentHelper.getDownloadIntent(this,
        audioUtils.getQariUrl(qariItem),
        baseUri, sheikhName, AUDIO_DOWNLOAD_KEY, QuranDownloadService.DOWNLOAD_TYPE_AUDIO);
    intent.putExtra(QuranDownloadService.EXTRA_START_VERSE, new SuraAyah(startSurah, 1));
    intent.putExtra(QuranDownloadService.EXTRA_END_VERSE, new SuraAyah(endSurah, quranInfo.getNumAyahs(endSurah)));
    intent.putExtra(QuranDownloadService.EXTRA_IS_GAPLESS, isGapless);
    startService(intent);

    AudioManagerUtils.clearCacheKeyForSheikh(qariItem);
  }

  @Override
  public void handleDownloadSuccess() {
    getShuyookhData();
  }

  @Override
  public void handleDownloadFailure(int errId) {
    getShuyookhData();
  }

  private class SurahAdapter extends RecyclerView.Adapter<SurahAudioManager.SurahViewHolder> {
    private final LayoutInflater inflater;
    private final List<QariItem> qariItemList;
    private final Map<QariItem, QariDownloadInfo> downloadInfoMap;
    private final Context context;
    private SparseBooleanArray checkedState = new SparseBooleanArray();

    SurahAdapter(List<QariItem> items, Context context) {
      qariItemList = items;
      downloadInfoMap = new HashMap<>();
      inflater = LayoutInflater.from(SurahAudioManager.this);
      this.context = context;
    }

    void setDownloadInfo(List<QariDownloadInfo> downloadInfo) {
      for (QariDownloadInfo info : downloadInfo) {
        downloadInfoMap.put(info.qariItem, info);
      }
    }

    @Override
    public SurahAudioManager.SurahViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
      return new SurahAudioManager.SurahViewHolder(inflater.inflate(R.layout.audio_manager_row, parent, false));
    }

    @Override
    public void onBindViewHolder(SurahAudioManager.SurahViewHolder holder, int position) {
      holder.name.setText(quranInfo.getSuraName(context, position+1, true));

      QariDownloadInfo info = getSheikhInfoForPosition(sheikhPosition);
      if(info == null) {
        return;
      }
      boolean fullyDownloaded = info.downloadedSuras.get(position + 1);
      int surahStatus, surahStatusImage;
      if(fullyDownloaded) {
        surahStatus = R.string.audio_manager_surah_delete;
        surahStatusImage = R.drawable.ic_cancel;
      } else {
        surahStatus = R.string.audio_manager_surah_download;
        surahStatusImage = R.drawable.ic_download;
      }
      holder.status.setText(getString(surahStatus));
      holder.image.setImageResource(surahStatusImage);

      holder.setChecked(isItemChecked(position));
    }

    QariDownloadInfo getSheikhInfoForPosition(int position) {
      return downloadInfoMap.get(qariItemList.get(position));
    }

    @Override
    public int getItemCount() {
      return 114;
    }

    public void toggleItemChecked(int position) {
      boolean checked = isItemChecked(position);
      setItemChecked(position, !checked);
    }

    public void setItemChecked(int position, boolean checked) {
      checkedState.put(position, checked);
      notifyItemChanged(position);
    }

    public boolean isItemChecked(int position) {
      boolean checked = checkedState.get(position, false);
      return checked;
    }

    public void uncheckAll() {
      checkedState.clear();
      notifyDataSetChanged();
    }
  }


  private class SurahViewHolder extends RecyclerView.ViewHolder {
    public final TextView name;
    public final TextView status;
    public final ImageView image;
    public final View view;

    SurahViewHolder(View itemView) {
      super(itemView);
      view = itemView;
      name = itemView.findViewById(R.id.name);
      status = itemView.findViewById(R.id.quantity);

      image = itemView.findViewById(R.id.image);
      itemView.setOnClickListener(mOnClickListener);
      itemView.setOnLongClickListener(onLongClickListener);
    }

    public void setChecked(boolean checked) {
      view.setActivated(checked);
    }
  }
}
