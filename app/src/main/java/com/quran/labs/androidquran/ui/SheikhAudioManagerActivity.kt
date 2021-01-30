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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ActionMode
import androidx.appcompat.view.ActionMode.Callback
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.audio.QariItem
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.service.QuranDownloadService
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver.SimpleDownloadListener
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier.ProgressIntent
import com.quran.labs.androidquran.service.util.ServiceIntentHelper
import com.quran.labs.androidquran.ui.fragment.BulkDownloadFragment
import com.quran.labs.androidquran.ui.util.ToastCompat
import com.quran.labs.androidquran.util.AudioManagerUtils
import com.quran.labs.androidquran.util.AudioUtils
import com.quran.labs.androidquran.util.QariDownloadInfo
import com.quran.labs.androidquran.util.QuranFileUtils
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import java.io.File
import java.util.ArrayList
import java.util.Locale
import javax.inject.Inject

class SheikhAudioManagerActivity : QuranActionBarActivity(), SimpleDownloadListener {
  private val compositeDisposable = CompositeDisposable()

  @Inject
  lateinit var quranInfo: QuranInfo

  @Inject
  lateinit var quranDisplayData: QuranDisplayData

  @Inject
  lateinit var quranFileUtils: QuranFileUtils

  @Inject
  lateinit var audioUtils: AudioUtils

  private lateinit var progressBar: ProgressBar
  private lateinit var recyclerView: RecyclerView
  private lateinit var surahAdapter: SurahAdapter
  private lateinit var qari: QariItem

  private var downloadReceiver: DefaultDownloadReceiver? = null
  private var basePath: String? = null
  private var actionMode: ActionMode? = null
  private var dialogConfirm: AlertDialog? = null


  override fun onCreate(savedInstanceState: Bundle?) {
    val quranApp = application as QuranApplication
    quranApp.applicationComponent
        .inject(this)
    quranApp.refreshLocale(this, false)
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_surah_audio_manager)

    val intent = intent
    val sheikh = intent.getParcelableExtra<QariItem>(EXTRA_SHEIKH)
    if (sheikh == null) {
      finish()
      return
    }
    qari = sheikh

    val ab = supportActionBar
    if (ab != null) {
      ab.title = qari.name
      ab.setDisplayHomeAsUpEnabled(true)
    }

    surahAdapter = SurahAdapter(this)

    recyclerView = findViewById(R.id.recycler_view)
    recyclerView.setHasFixedSize(true)
    recyclerView.layoutManager = LinearLayoutManager(this)
    recyclerView.itemAnimator = DefaultItemAnimator()
    recyclerView.adapter = surahAdapter

    progressBar = findViewById(R.id.progress)

    basePath = quranFileUtils.getQuranAudioDirectory(this)
    readShuyookhData()
  }

  override fun onResume() {
    super.onResume()
    val receiver = DefaultDownloadReceiver(
        this,
        QuranDownloadService.DOWNLOAD_TYPE_AUDIO
    )
    receiver.setCanCancelDownload(true)
    LocalBroadcastManager.getInstance(this)
        .registerReceiver(
            receiver,
            IntentFilter(ProgressIntent.INTENT_NAME)
        )
    receiver.setListener(this)
    downloadReceiver = receiver
  }

  override fun onPause() {
    downloadReceiver?.let {
      it.setListener(null)
      LocalBroadcastManager.getInstance(this)
          .unregisterReceiver(it)
    }
    downloadReceiver = null
    super.onPause()
  }

  override fun onDestroy() {
    compositeDisposable.clear()
    dialogConfirm?.dismiss()
    super.onDestroy()
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.surah_audio_manager_menu, menu)
    return super.onCreateOptionsMenu(menu)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      android.R.id.home -> {
        finish()
        return true
      }
      R.id.download_all -> {
        val info = surahAdapter.qariDownloadInfo ?: return true
        if (info.downloadedSuras.size() != 114) {
          val fm = supportFragmentManager
          val bulkDownloadDialog = BulkDownloadFragment()
          bulkDownloadDialog.show(fm, BulkDownloadFragment.TAG)
        }
      }
    }
    return super.onOptionsItemSelected(item)
  }

  private fun readShuyookhData() {
      compositeDisposable.clear()
      compositeDisposable.add(
          AudioManagerUtils.shuyookhDownloadObservable(quranInfo, basePath, listOf(qari))
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

      override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        val fullyDownloadedCount = surahAdapter.fullyDownloadedCheckedSurahCount
        val notFullyDownloadedCount = surahAdapter.notFullyDownloadedCheckedSurahCount
        val deleteButton = menu.findItem(R.id.cab_delete)
        val downloadButton = menu.findItem(R.id.cab_download)
        deleteButton.isVisible = fullyDownloadedCount > 0
        downloadButton.isVisible = notFullyDownloadedCount > 0
        return true
      }

      override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        when (item.itemId) {
          R.id.cab_download -> {
            downloadSelection()
            return true
          }
          R.id.cab_delete -> {
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

  private fun finishActionMode() {
    actionMode?.finish()
  }

  private val onLongClickListener = OnLongClickListener { view ->
    if (actionMode != null) {
      false
    } else {
      val position = recyclerView.getChildAdapterPosition(view)
      if (position == RecyclerView.NO_POSITION) {
        false
      } else {
        surahAdapter.setItemChecked(position, true)
        actionMode = startSupportActionMode(actionModeCallback)
        true
      }
    }
  }

  private val onClickListener =
    OnClickListener { v ->
      val position = recyclerView.getChildAdapterPosition(v)
      if (position != RecyclerView.NO_POSITION) {
        if (actionMode != null) {
          surahAdapter.toggleItemChecked(position)
          actionMode!!.invalidate()
        } else {
          val info = surahAdapter.qariDownloadInfo
          if (info != null) {
            val surah = position + 1
            val downloaded = info.downloadedSuras[surah]
            if (downloaded) {
              val surahName = quranDisplayData.getSuraName(this, surah, true)
              val msg = String.format(getString(R.string.audio_manager_remove_audio_msg), surahName)
              val builder = AlertDialog.Builder(this)
              builder.setTitle(R.string.audio_manager_remove_audio_title)
                  .setMessage(msg)
                  .setPositiveButton(R.string.remove_button
                  ) { _, _ ->
                    deleteSelection(ArrayList(listOf(surah)))
                  }
                  .setNegativeButton(R.string.cancel
                  ) { dialog, _ -> dialog.dismiss() }
              dialogConfirm = builder.show()
            } else {
              download(surah, surah)
            }
          }
        }
      }
    }

  private fun deleteSurah(surah: Int): Boolean {
    val fileUri = audioUtils.getLocalQariUri(this, qari) ?: return false
    var deletionSuccessful = true
    if (qari.isGapless) {
      val fileName = String.format(Locale.US, fileUri, surah)
      val audioFile = File(fileName)
      deletionSuccessful = audioFile.delete()
    } else {
      val numAyahs = quranInfo.getNumberOfAyahs(surah)
      for (i in 1..numAyahs) {
        val fileName = String.format(Locale.US, fileUri, surah, i)
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
    val resultString = if (failureCount > 0) {
      resources.getQuantityString(
          R.plurals.audio_manager_delete_surah_error, failureCount, failureCount
      )
    } else {
      resources.getQuantityString(
          R.plurals.audio_manager_delete_surah_success, successCount, successCount
      )
    }
    ToastCompat.makeText(this, resultString, Toast.LENGTH_SHORT).show()
    if (successCount > 0) {
      // refresh, if at least 1 file was deleted
      AudioManagerUtils.clearCacheKeyForSheikh(qari)
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

  fun downloadBulk(startSurah: Int, endSurah: Int){
      download(startSurah,endSurah)
  }

  private fun download(startSurah: Int, endSurah: Int) {
    val baseUri = basePath + qari.path
    val isGapless = qari.isGapless
    val sheikhName = qari.name
    val intent = ServiceIntentHelper.getDownloadIntent(
        this,
        audioUtils.getQariUrl(qari),
        baseUri, sheikhName, AUDIO_DOWNLOAD_KEY + qari.id + startSurah,
        QuranDownloadService.DOWNLOAD_TYPE_AUDIO
    )
    intent.putExtra(QuranDownloadService.EXTRA_START_VERSE,
        SuraAyah(startSurah, 1)
    )
    intent.putExtra(QuranDownloadService.EXTRA_END_VERSE,
        SuraAyah(endSurah, quranInfo.getNumberOfAyahs(endSurah))
    )
    intent.putExtra(QuranDownloadService.EXTRA_IS_GAPLESS, isGapless)
    startService(intent)
  }

  override fun handleDownloadSuccess() {
    AudioManagerUtils.clearCacheKeyForSheikh(qari)
    readShuyookhData()
  }

  override fun handleDownloadFailure(errId: Int) {
    AudioManagerUtils.clearCacheKeyForSheikh(qari)
    readShuyookhData()
  }

  private inner class SurahAdapter internal constructor(
    private val context: Context
  ) : Adapter<SurahViewHolder>() {
    var qariDownloadInfo: QariDownloadInfo? = null
    private val inflater: LayoutInflater = LayoutInflater.from(this@SheikhAudioManagerActivity)
    private val fullyDownloadedCheckedState = SparseBooleanArray()
    private val notFullyDownloadedCheckedState = SparseBooleanArray()

    fun setDownloadInfo(downloadInfo: List<QariDownloadInfo>) {
      qariDownloadInfo = downloadInfo.firstOrNull()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SurahViewHolder {
      return SurahViewHolder(inflater.inflate(R.layout.audio_manager_row, parent, false))
    }

    override fun onBindViewHolder(holder: SurahViewHolder, position: Int) {
      holder.name.text = quranDisplayData.getSuraName(context, position + 1, true)
      val surahStatus: Int
      val surahStatusImage: Int
      val surahStatusBackground: Int
      if (isItemFullyDownloaded(position)) {
        surahStatus = R.string.audio_manager_surah_delete
        surahStatusImage = R.drawable.ic_cancel
        surahStatusBackground = R.drawable.cancel_button_circle
      } else {
        surahStatus = R.string.audio_manager_surah_download
        surahStatusImage = R.drawable.ic_download
        surahStatusBackground = R.drawable.download_button_circle
      }
      holder.status.text = getString(surahStatus)
      holder.image.setImageResource(surahStatusImage)
      holder.image.setBackgroundResource(surahStatusBackground)
      holder.setChecked(isItemChecked(position))
    }

    override fun getItemCount() = 114

    private fun isItemFullyDownloaded(position: Int): Boolean {
      val info = qariDownloadInfo ?: return false
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

  private inner class SurahViewHolder internal constructor(val view: View) : ViewHolder(view) {
    val name: TextView = view.findViewById(R.id.name)
    val status: TextView = view.findViewById(R.id.quantity)
    val image: ImageView = view.findViewById(R.id.image)

    init {
      view.setOnClickListener(onClickListener)
      view.setOnLongClickListener(onLongClickListener)
    }

    fun setChecked(checked: Boolean) {
      view.isActivated = checked
    }
  }

  companion object {
    const val EXTRA_SHEIKH = "SurahAudioManager.EXTRA_SHEIKH"
    private const val AUDIO_DOWNLOAD_KEY = "SurahAudioManager.DownloadKey."
  }
}
