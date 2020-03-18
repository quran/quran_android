package com.quran.labs.androidquran.ui

import android.content.Context
import android.content.IntentFilter
import android.os.Bundle
import android.util.SparseBooleanArray
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.OnClickListener
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.view.ActionMode
import androidx.appcompat.view.ActionMode.Callback
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.R.drawable
import com.quran.labs.androidquran.R.id
import com.quran.labs.androidquran.R.layout
import com.quran.labs.androidquran.R.plurals
import com.quran.labs.androidquran.R.string
import com.quran.labs.androidquran.common.QariItem
import com.quran.labs.androidquran.data.QuranInfo
import com.quran.labs.androidquran.data.SuraAyah
import com.quran.labs.androidquran.service.QuranDownloadService
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver.SimpleDownloadListener
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier.ProgressIntent
import com.quran.labs.androidquran.service.util.ServiceIntentHelper
import com.quran.labs.androidquran.ui.SurahAudioManager
import com.quran.labs.androidquran.util.AudioManagerUtils
import com.quran.labs.androidquran.util.AudioUtils
import com.quran.labs.androidquran.util.QariDownloadInfo
import com.quran.labs.androidquran.util.QuranFileUtils
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.observers.DisposableSingleObserver
import java.io.File
import java.util.ArrayList
import java.util.Arrays
import java.util.HashMap
import java.util.Locale
import javax.inject.Inject

class SurahAudioManager : QuranActionBarActivity(), SimpleDownloadListener {
  private val compositeDisposable = CompositeDisposable()

  @Inject
  lateinit var quranInfo: QuranInfo

  @Inject
  lateinit var quranFileUtils: QuranFileUtils

  @Inject
  lateinit var audioUtils: AudioUtils

  private lateinit var progressBar: ProgressBar
  private lateinit var recyclerView: RecyclerView
  private lateinit var qariItems: List<QariItem>
  private lateinit var surahAdapter: SurahAdapter

  private var downloadReceiver: DefaultDownloadReceiver? = null
  private var basePath: String? = null
  private var sheikhPosition = -1
  private var actionMode: ActionMode? = null


  override fun onCreate(savedInstanceState: Bundle?) {
    val quranApp = application as QuranApplication
    quranApp.applicationComponent
        .inject(this)
    quranApp.refreshLocale(this, false)
    super.onCreate(savedInstanceState)
    val ab = supportActionBar
    if (ab != null) {
      ab.setTitle(string.audio_manager)
      ab.setDisplayHomeAsUpEnabled(true)
    }
    setContentView(layout.activity_surah_audio_manager)
    val intent = intent
    sheikhPosition = intent.getIntExtra(EXTRA_SHEIKH_POSITION, -1)
    recyclerView = findViewById(id.recycler_view)
    recyclerView.setHasFixedSize(true)
    recyclerView.layoutManager = LinearLayoutManager(this)
    recyclerView.itemAnimator = DefaultItemAnimator()
    progressBar = findViewById(id.progress)
    qariItems = audioUtils.getQariList(this)
    basePath = quranFileUtils.getQuranAudioDirectory(this)
    surahAdapter = SurahAdapter(qariItems, this)
    recyclerView.adapter = surahAdapter
    readShuyookhData()
  }

  override fun onResume() {
    super.onResume()
    downloadReceiver = DefaultDownloadReceiver(
        this,
        QuranDownloadService.DOWNLOAD_TYPE_AUDIO
    )
    downloadReceiver!!.setCanCancelDownload(true)
    LocalBroadcastManager.getInstance(this)
        .registerReceiver(
            downloadReceiver!!,
            IntentFilter(ProgressIntent.INTENT_NAME)
        )
    downloadReceiver!!.setListener(this)
  }

  override fun onPause() {
    downloadReceiver!!.setListener(null)
    LocalBroadcastManager.getInstance(this)
        .unregisterReceiver(downloadReceiver!!)
    super.onPause()
  }

  override fun onDestroy() {
    compositeDisposable.clear()
    super.onDestroy()
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.surah_audio_manager_menu, menu)
    return super.onCreateOptionsMenu(menu)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    val itemId = item.itemId
    when (itemId) {
      android.R.id.home -> {
        finish()
        return true
      }
      id.download_all -> {
        val info = surahAdapter!!.getSheikhInfoForPosition(sheikhPosition)
        if (info!!.downloadedSuras.size() != 114) {
          download(1, 114)
        }
      }
    }
    return super.onOptionsItemSelected(item)
  }

  private fun readShuyookhData() {
      compositeDisposable.clear()
      compositeDisposable.add(
          AudioManagerUtils.shuyookhDownloadObservable(quranInfo, basePath, qariItems)
              .observeOn(AndroidSchedulers.mainThread())
              .subscribe { downloadInfo ->
                progressBar.visibility = View.GONE
                surahAdapter.setDownloadInfo(downloadInfo)
                surahAdapter.notifyDataSetChanged()
              }
      )
    }

  private val actionModeCallback: Callback =
    object : Callback {
      override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.surah_audio_manager_contextual_menu, menu)
        return true
      }

      override fun onPrepareActionMode(
        mode: ActionMode,
        menu: Menu
      ): Boolean {
        val fullyDownloadedCount = surahAdapter.fullyDownloadedCheckedSurahCount
        val notFullyDownloadedCount = surahAdapter.notFullyDownloadedCheckedSurahCount
        val deleteButton = menu.findItem(id.cab_delete)
        val downloadButton = menu.findItem(id.cab_download)
        deleteButton.isVisible = fullyDownloadedCount > 0
        downloadButton.isVisible = notFullyDownloadedCount > 0
        return true
      }

      override fun onActionItemClicked(
        mode: ActionMode,
        item: MenuItem
      ): Boolean {
        when (item.itemId) {
          id.cab_download -> {
            downloadSelection()
            return true
          }
          id.cab_delete -> {
            val checkedSurahs = surahAdapter.checkedSurahs
            val toBeDeleted = checkedSurahs.first
            deleteSelection(toBeDeleted)
            return true
          }
        }
        return false
      }

      override fun onDestroyActionMode(mode: ActionMode) {
        surahAdapter.uncheckAll()
        actionMode = null
      }
    }

  private val isInActionMode: Boolean
    get() = actionMode != null

  private fun finishActionMode() {
    if (isInActionMode) {
      actionMode!!.finish()
    }
  }

  private val onLongClickListener = OnLongClickListener { view ->
    if (isInActionMode) {
      return@OnLongClickListener false
    }
    val position = recyclerView.getChildAdapterPosition(view)
    if (position == RecyclerView.NO_POSITION) {
      return@OnLongClickListener false
    }
    surahAdapter.setItemChecked(position, true)
    actionMode = startSupportActionMode(actionModeCallback)
    true
  }
  private val mOnClickListener =
    OnClickListener { v ->
      val position = recyclerView.getChildAdapterPosition(v)
      if (position == RecyclerView.NO_POSITION) {
        return@OnClickListener
      }
      if (isInActionMode) {
        surahAdapter.toggleItemChecked(position)
        actionMode!!.invalidate()
        return@OnClickListener
      }
      val info = surahAdapter.getSheikhInfoForPosition(sheikhPosition)
      val surah = position + 1
      val downloaded = info!!.downloadedSuras[surah]
      if (downloaded) {
        // TODO: show a confirmation dialog before deleting
        deleteSelection(ArrayList(Arrays.asList(surah)))
      } else {
        download(surah, surah)
      }
    }

  private fun deleteSurah(surah: Int): Boolean {
    val qariItem = qariItems[sheikhPosition]
    val baseUri = basePath + qariItem.path
    val fileUri = audioUtils.getLocalQariUri(this, qariItem)
    var deletionSuccessful = true
    if (qariItem.isGapless) {
      val fileName = String.format(Locale.US, fileUri!!, surah)
      val audioFile = File(fileName)
      deletionSuccessful = audioFile.delete()
    } else {
      val numAyahs = quranInfo.getNumAyahs(surah)
      for (i in 1..numAyahs) {
        val fileName = String.format(Locale.US, fileUri!!, surah, i)
        val ayahAudioFile = File(fileName)
        if (ayahAudioFile.exists()) {
          deletionSuccessful = deletionSuccessful && ayahAudioFile.delete()
        }
      }
    }
    return deletionSuccessful
  }

  private fun deleteSelection(toBeDeleted: List<Int>) {
    var successCount = 0
    var failureCount = 0
    for (surah in toBeDeleted) {
      val deleted = deleteSurah(surah)
      if (deleted) {
        successCount++
      } else {
        failureCount++
      }
    }
    val resultString: String
    resultString = if (failureCount > 0) {
      resources.getQuantityString(
          plurals.audio_manager_delete_surah_error, failureCount, failureCount
      )
    } else {
      resources.getQuantityString(
          plurals.audio_manager_delete_surah_success, successCount, successCount
      )
    }
    Toast.makeText(this, resultString, Toast.LENGTH_SHORT)
        .show()
    if (successCount > 0) {
      // refresh, if at least 1 file was deleted
      val qariItem = qariItems[sheikhPosition]
      AudioManagerUtils.clearCacheKeyForSheikh(qariItem)
      readShuyookhData()
      finishActionMode()
    }
  }

  private fun downloadSelection() {
    val checkedSurahs = surahAdapter.checkedSurahs
    val toBeDownloaded = checkedSurahs.second
    for (surah in toBeDownloaded) {
      download(surah, surah)
    }
    finishActionMode()
  }

  private fun download(startSurah: Int, endSurah: Int) {
    val qariItem = qariItems[sheikhPosition]
    val baseUri = basePath + qariItem.path
    val isGapless = qariItem.isGapless
    val sheikhName = qariItem.name
    val intent = ServiceIntentHelper.getDownloadIntent(
        this,
        audioUtils.getQariUrl(qariItem),
        baseUri, sheikhName, AUDIO_DOWNLOAD_KEY,
        QuranDownloadService.DOWNLOAD_TYPE_AUDIO
    )
    intent.putExtra(QuranDownloadService.EXTRA_START_VERSE, SuraAyah(startSurah, 1))
    intent.putExtra(
        QuranDownloadService.EXTRA_END_VERSE, SuraAyah(endSurah, quranInfo.getNumAyahs(endSurah))
    )
    intent.putExtra(QuranDownloadService.EXTRA_IS_GAPLESS, isGapless)
    startService(intent)
    AudioManagerUtils.clearCacheKeyForSheikh(qariItem)
  }

  override fun handleDownloadSuccess() {
    readShuyookhData()
  }

  override fun handleDownloadFailure(errId: Int) {
    readShuyookhData()
  }

  private inner class SurahAdapter internal constructor(
    private val qariItemList: List<QariItem>,
    private val context: Context
  ) : Adapter<SurahViewHolder>() {
    private val inflater: LayoutInflater = LayoutInflater.from(this@SurahAudioManager)
    private val downloadInfoMap: MutableMap<QariItem, QariDownloadInfo> = mutableMapOf()
    private val fullyDownloadedCheckedState = SparseBooleanArray()
    private val notFullyDownloadedCheckedState = SparseBooleanArray()

    fun setDownloadInfo(downloadInfo: List<QariDownloadInfo>) {
      for (info in downloadInfo) {
        downloadInfoMap[info.qariItem] = info
      }
    }

    override fun onCreateViewHolder(
      parent: ViewGroup,
      viewType: Int
    ): SurahViewHolder {
      return SurahViewHolder(inflater.inflate(layout.audio_manager_row, parent, false))
    }

    override fun onBindViewHolder(
      holder: SurahViewHolder,
      position: Int
    ) {
      holder.name.text = quranInfo.getSuraName(context, position + 1, true)
      val surahStatus: Int
      val surahStatusImage: Int
      if (isItemFullyDownloaded(position)) {
        surahStatus = string.audio_manager_surah_delete
        surahStatusImage = drawable.ic_cancel
      } else {
        surahStatus = string.audio_manager_surah_download
        surahStatusImage = drawable.ic_download
      }
      holder.status.text = getString(surahStatus)
      holder.image.setImageResource(surahStatusImage)
      holder.setChecked(isItemChecked(position))
    }

    fun getSheikhInfoForPosition(position: Int): QariDownloadInfo? {
      return downloadInfoMap[qariItemList[position]]
    }

    override fun getItemCount(): Int {
      return 114
    }

    private fun isItemFullyDownloaded(position: Int): Boolean {
      val info = getSheikhInfoForPosition(sheikhPosition) ?: return false
      return info.downloadedSuras[position + 1]
    }

    fun toggleItemChecked(position: Int) {
      val checked = isItemChecked(position)
      setItemChecked(position, !checked)
    }

    fun setItemChecked(position: Int, checked: Boolean) {
      val fullyDownloaded = isItemFullyDownloaded(position)
      val checkedState =
        if (fullyDownloaded) fullyDownloadedCheckedState else notFullyDownloadedCheckedState
      if (checked) {
        checkedState.put(position, true)
      } else {
        checkedState.delete(position)
      }
      notifyItemChanged(position)
    }

    fun isItemChecked(position: Int): Boolean {
      return (fullyDownloadedCheckedState[position, false]
          || notFullyDownloadedCheckedState[position, false])
    }

    fun uncheckAll() {
      fullyDownloadedCheckedState.clear()
      notFullyDownloadedCheckedState.clear()
      notifyDataSetChanged()
    }

    val fullyDownloadedCheckedSurahCount: Int
      get() = fullyDownloadedCheckedState.size()

    val notFullyDownloadedCheckedSurahCount: Int
      get() = notFullyDownloadedCheckedState.size()

    val checkedSurahs: Pair<List<Int>, List<Int>>
      get() {
        val fullyDownloaded: MutableList<Int> = ArrayList()
        val notFullyDownloaded: MutableList<Int> = ArrayList()
        for (i in 0 until fullyDownloadedCheckedState.size()) {
          val position = fullyDownloadedCheckedState.keyAt(i)
          fullyDownloaded.add(position + 1)
        }
        for (i in 0 until notFullyDownloadedCheckedState.size()) {
          val position = notFullyDownloadedCheckedState.keyAt(i)
          notFullyDownloaded.add(position + 1)
        }
        return fullyDownloaded to notFullyDownloaded
      }

  }

  private inner class SurahViewHolder internal constructor(val view: View) :
      ViewHolder(view) {
    val name: TextView = view.findViewById(id.name)
    val status: TextView = view.findViewById(id.quantity)
    val image: ImageView = view.findViewById(id.image)

    init {
      view.setOnClickListener(mOnClickListener)
      view.setOnLongClickListener(onLongClickListener)
    }

    fun setChecked(checked: Boolean) {
      view.isActivated = checked
    }
  }

  companion object {
    const val EXTRA_SHEIKH_POSITION = "SheikhPosition"
    private const val AUDIO_DOWNLOAD_KEY = "SurahAudioManager.DownloadKey"
  }
}
