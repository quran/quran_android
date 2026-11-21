package com.quran.labs.androidquran.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.common.audio.repository.CurrentQariManager
import com.quran.labs.androidquran.util.AudioUtils
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.view.QuranSpinner
import com.shawnlin.numberpicker.NumberPicker
import dev.zacsweers.metro.Inject
import java.text.NumberFormat
import com.quran.mobile.common.ui.core.R as UiCoreR

/**
 * Configures the playback settings of a single [AudioWidget]. Launched by the
 * launcher when the widget is added, and when tapping the widget's title.
 */
class AudioWidgetConfigureActivity : AppCompatActivity() {

  @Inject lateinit var quranInfo: QuranInfo
  @Inject lateinit var audioUtils: AudioUtils
  @Inject lateinit var currentQariManager: CurrentQariManager
  @Inject lateinit var audioWidgetPreferences: AudioWidgetPreferences
  @Inject lateinit var audioWidgetRequestFactory: AudioWidgetRequestFactory
  @Inject lateinit var audioWidgetUpdater: AudioWidgetUpdater

  private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
  private lateinit var qaris: List<QariItem>

  private lateinit var titleText: EditText
  private lateinit var qariSpinner: QuranSpinner
  private lateinit var startSuraSpinner: QuranSpinner
  private lateinit var startAyahSpinner: QuranSpinner
  private lateinit var endSuraSpinner: QuranSpinner
  private lateinit var endAyahSpinner: QuranSpinner
  private lateinit var repeatVersePicker: NumberPicker
  private lateinit var repeatRangePicker: NumberPicker
  private lateinit var playbackSpeedPicker: NumberPicker
  private lateinit var restrictToRange: CheckBox

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    (application as QuranApplication).applicationComponent.inject(this)

    widgetId = intent?.getIntExtra(
      AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
    ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

    // backing out of the configuration should cancel adding the widget
    setResult(RESULT_CANCELED, resultIntent())
    if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
      finish()
      return
    }

    enableEdgeToEdge()
    setContentView(R.layout.audio_widget_configure)
    val root = findViewById<ViewGroup>(R.id.root)
    ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
      val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.systemBars() or
            WindowInsetsCompat.Type.displayCutout() or
            WindowInsetsCompat.Type.ime()
      )
      root.updatePadding(
        left = insets.left, top = insets.top, right = insets.right, bottom = insets.bottom
      )
      windowInsets
    }

    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    toolbar.setTitle(R.string.audio_widget_configure_title)
    setSupportActionBar(toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)

    titleText = findViewById(R.id.widget_title)
    qariSpinner = findViewById(R.id.qari_spinner)
    startSuraSpinner = findViewById(R.id.start_sura_spinner)
    startAyahSpinner = findViewById(R.id.start_ayah_spinner)
    endSuraSpinner = findViewById(R.id.end_sura_spinner)
    endAyahSpinner = findViewById(R.id.end_ayah_spinner)
    repeatVersePicker = findViewById(R.id.repeat_verse_picker)
    repeatRangePicker = findViewById(R.id.repeat_range_picker)
    playbackSpeedPicker = findViewById(R.id.playback_speed_picker)
    restrictToRange = findViewById(R.id.restrict_to_range)
    findViewById<View>(R.id.apply).setOnClickListener { apply() }

    val config = audioWidgetPreferences.config(widgetId) ?: defaultConfig()
    titleText.setText(config.title.orEmpty())
    initializeQariSpinner(config.qariId)
    initializeRangeSpinners(startSuraSpinner, startAyahSpinner, config.start)
    initializeRangeSpinners(endSuraSpinner, endAyahSpinner, config.end)
    initializePickers(config)
  }

  private fun defaultConfig(): AudioWidgetConfig {
    // default to the first sura, matching the qari the user last picked in the app
    return AudioWidgetConfig(
      title = null,
      qariId = currentQariManager.currentQariId(),
      start = SuraAyah(1, 1),
      end = SuraAyah(1, quranInfo.getNumberOfAyahs(1)),
      verseRepeat = 0,
      rangeRepeat = 0,
      playbackSpeed = 1.0f,
      enforceBounds = true
    )
  }

  private fun initializeQariSpinner(selectedQariId: Int) {
    qaris = audioUtils.getQariList(this)
    val adapter = ArrayAdapter<CharSequence>(this, ITEM_LAYOUT, qaris.map { it.name })
    adapter.setDropDownViewResource(ITEM_DROPDOWN_LAYOUT)
    qariSpinner.adapter = adapter
    qariSpinner.setSelection(qaris.indexOfFirst { it.id == selectedQariId }.coerceAtLeast(0))
  }

  private fun initializeRangeSpinners(
    suraSpinner: QuranSpinner,
    ayahSpinner: QuranSpinner,
    selected: SuraAyah
  ) {
    val suras = resources.getStringArray(UiCoreR.array.sura_names)
      .mapIndexed { index, name -> QuranUtils.getLocalizedNumber(index + 1) + ". " + name }
    val suraAdapter = ArrayAdapter<CharSequence>(this, ITEM_LAYOUT, suras)
    suraAdapter.setDropDownViewResource(ITEM_DROPDOWN_LAYOUT)
    suraSpinner.adapter = suraAdapter

    // populate the ayahs up front so the spinner is measured with its content
    val ayahAdapter = ArrayAdapter<CharSequence>(this, ITEM_LAYOUT)
    ayahAdapter.setDropDownViewResource(ITEM_DROPDOWN_LAYOUT)
    ayahAdapter.addAll(ayahNumbers(selected.sura))
    ayahSpinner.adapter = ayahAdapter
    suraSpinner.setSelection(selected.sura - 1)
    ayahSpinner.setSelection(selected.ayah - 1)

    var populatedSura = selected.sura
    suraSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        val sura = position + 1
        if (sura == populatedSura) {
          return
        }
        populatedSura = sura

        // keep the selected ayah when switching suras, as long as it exists
        val ayahs = ayahNumbers(sura)
        val ayah = (ayahSpinner.selectedItemPosition + 1).coerceIn(1, ayahs.size)
        ayahAdapter.clear()
        ayahAdapter.addAll(ayahs)
        ayahSpinner.setSelection(ayah - 1)
      }

      override fun onNothingSelected(parent: AdapterView<*>?) {}
    }
  }

  private fun ayahNumbers(sura: Int): List<CharSequence> {
    return (1..quranInfo.getNumberOfAyahs(sura)).map { QuranUtils.getLocalizedNumber(it) }
  }

  private fun initializePickers(config: AudioWidgetConfig) {
    val numberFormat = NumberFormat.getNumberInstance(QuranUtils.getCurrentLocale())
    val repeatValues = (1..MAX_REPEATS).map { numberFormat.format(it.toLong()) } +
        getString(UiCoreR.string.infinity)

    listOf(repeatVersePicker, repeatRangePicker).forEach {
      it.minValue = 1
      it.maxValue = MAX_REPEATS + 1
      it.displayedValues = repeatValues.toTypedArray()
    }
    repeatVersePicker.value = repeatToPickerValue(config.verseRepeat)
    repeatRangePicker.value = repeatToPickerValue(config.rangeRepeat)
    repeatRangePicker.setOnValueChangedListener { _, _, newVal ->
      if (newVal > 1) {
        // repeating the range only makes sense when playback is restricted to it
        restrictToRange.isChecked = true
      }
    }

    playbackSpeedPicker.minValue = 1
    playbackSpeedPicker.maxValue = SPEEDS.size
    playbackSpeedPicker.displayedValues = SPEEDS.map { numberFormat.format(it) }.toTypedArray()
    val speedIndex = SPEEDS.indexOf(config.playbackSpeed).takeIf { it >= 0 } ?: DEFAULT_SPEED_INDEX
    playbackSpeedPicker.value = speedIndex + 1

    restrictToRange.isChecked = config.enforceBounds
  }

  private fun apply() {
    val qari = qaris.getOrNull(qariSpinner.selectedItemPosition) ?: return
    val config = AudioWidgetConfig(
      title = titleText.text?.toString()?.trim()?.ifEmpty { null },
      qariId = qari.id,
      start = SuraAyah(
        startSuraSpinner.selectedItemPosition + 1, startAyahSpinner.selectedItemPosition + 1
      ),
      end = SuraAyah(
        endSuraSpinner.selectedItemPosition + 1, endAyahSpinner.selectedItemPosition + 1
      ),
      verseRepeat = pickerValueToRepeat(repeatVersePicker.value),
      rangeRepeat = pickerValueToRepeat(repeatRangePicker.value),
      playbackSpeed = SPEEDS[playbackSpeedPicker.value - 1],
      enforceBounds = restrictToRange.isChecked
    )
    audioWidgetPreferences.save(widgetId, config)

    // gapless qaris need their timing database, which is quick to get while we're in the foreground
    val result = audioWidgetRequestFactory.build(config)
    if (result is AudioWidgetRequestFactory.Result.MissingTimingDatabase) {
      startService(result.downloadIntent)
    }

    audioWidgetUpdater.updateWidget(widgetId)
    setResult(RESULT_OK, resultIntent())
    finish()
  }

  private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)

  private fun repeatToPickerValue(repeat: Int) = if (repeat == -1) MAX_REPEATS + 1 else repeat + 1

  private fun pickerValueToRepeat(value: Int) = if (value == MAX_REPEATS + 1) -1 else value - 1

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == android.R.id.home) {
      finish()
      return true
    }
    return super.onOptionsItemSelected(item)
  }

  companion object {
    private val ITEM_LAYOUT = R.layout.sherlock_spinner_item
    private val ITEM_DROPDOWN_LAYOUT = R.layout.sherlock_spinner_dropdown_item
    private const val MAX_REPEATS = 25
    private val SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f)
    private const val DEFAULT_SPEED_INDEX = 2
  }
}
