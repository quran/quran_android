package com.quran.labs.androidquran.ui.fragment

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.audio.model.playback.AudioRequest
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.ui.helpers.SlidingPagerAdapter
import com.quran.labs.androidquran.ui.util.TypefaceManager
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.view.QuranSpinner
import com.quran.mobile.di.AyahActionFragmentProvider
import com.shawnlin.numberpicker.NumberPicker
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject

class AyahPlaybackFragment : AyahActionFragment() {
  private val defaultVerseRepeat = 1
  private val defaultRangeRepeat = 1

  private var decidedStart: SuraAyah? = null
  private var decidedEnd: SuraAyah? = null
  private var shouldEnforce = false
  private var rangeRepeatCount = 0
  private var verseRepeatCount = 0
  private var currentSpeed = 1.0f

  private lateinit var applyButton: Button
  private lateinit var startSuraSpinner: QuranSpinner
  private lateinit var startAyahSpinner: QuranSpinner
  private lateinit var endingSuraSpinner: QuranSpinner
  private lateinit var endingAyahSpinner: QuranSpinner
  private lateinit var repeatVersePicker: NumberPicker
  private lateinit var repeatRangePicker: NumberPicker
  private lateinit var playbackSpeedPicker: NumberPicker
  private lateinit var restrictToRange: CheckBox

  private lateinit var startAyahAdapter: ArrayAdapter<CharSequence>
  private lateinit var endingAyahAdapter: ArrayAdapter<CharSequence>

  private var lastSeenAudioRequest: AudioRequest? = null
  private var isOpen: Boolean = false

  @Inject
  lateinit var quranInfo: QuranInfo

  object Provider : AyahActionFragmentProvider {
    override val order = SlidingPagerAdapter.AUDIO_PAGE
    override val iconResId = com.quran.labs.androidquran.common.toolbar.R.drawable.ic_play
    override fun newAyahActionFragment() = AyahPlaybackFragment()
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    val view = inflater.inflate(R.layout.audio_panel, container, false)
    view.setOnClickListener(onClickListener)
    startSuraSpinner = view.findViewById(R.id.start_sura_spinner)
    startAyahSpinner = view.findViewById(R.id.start_ayah_spinner)
    endingSuraSpinner = view.findViewById(R.id.end_sura_spinner)
    endingAyahSpinner = view.findViewById(R.id.end_ayah_spinner)
    restrictToRange = view.findViewById(R.id.restrict_to_range)
    applyButton = view.findViewById(R.id.apply)
    applyButton.setOnClickListener(onClickListener)
    repeatVersePicker = view.findViewById(R.id.repeat_verse_picker)
    repeatRangePicker = view.findViewById(R.id.repeat_range_picker)
    playbackSpeedPicker = view.findViewById(R.id.playback_speed_picker)

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      val speedArea = view.findViewById<View>(R.id.playback_speed_area)
      speedArea.visibility = View.GONE
    }

    val context = requireContext()
    val isArabicNames = QuranSettings.getInstance(context).isArabicNames
    val locale: Locale = if (isArabicNames) {
      Locale("ar")
    } else {
      Locale.getDefault()
    }
    val numberFormat = NumberFormat.getNumberInstance(locale)
    val values = arrayOfNulls<String>(MAX_REPEATS + 1)
    for (i in 1..MAX_REPEATS) {
      values[i - 1] = numberFormat.format(i.toLong())
    }
    values[MAX_REPEATS] = getString(R.string.infinity)
    if (isArabicNames) {
      listOf(repeatVersePicker, repeatRangePicker, playbackSpeedPicker).forEach {
        it.formatter = NumberPicker.Formatter { value: Int -> arFormat(value) }
        val typeface = TypefaceManager.getHeaderFooterTypeface(context)
        it.typeface = typeface
        it.setSelectedTypeface(typeface)
        // Use larger text size since KFGQPC font is small
        it.setSelectedTextSize(R.dimen.arabic_number_picker_selected_text_size)
        it.setTextSize(R.dimen.arabic_number_picker_text_size)
      }
    }
    repeatVersePicker.minValue = 1
    repeatVersePicker.maxValue = MAX_REPEATS + 1
    repeatRangePicker.minValue = 1
    repeatRangePicker.maxValue = MAX_REPEATS + 1
    repeatVersePicker.displayedValues = values
    repeatRangePicker.displayedValues = values
    repeatRangePicker.value = defaultRangeRepeat
    repeatVersePicker.value = defaultVerseRepeat
    playbackSpeedPicker.minValue = 1
    playbackSpeedPicker.maxValue = SPEEDS.size
    playbackSpeedPicker.displayedValues = SPEEDS.map { numberFormat.format(it) }.toTypedArray()
    playbackSpeedPicker.value = DEFAULT_SPEED_INDEX + 1
    repeatRangePicker.setOnValueChangedListener { _: NumberPicker?, _: Int, newVal: Int ->
      if (newVal > 1) {
        // whenever we want to repeat the range, we have to enable restrictToRange
        restrictToRange.isChecked = true
      }
    }
    startAyahAdapter = initializeAyahSpinner(context, startAyahSpinner)
    endingAyahAdapter = initializeAyahSpinner(context, endingAyahSpinner)
    initializeSuraSpinner(context, startSuraSpinner, startAyahAdapter)
    initializeSuraSpinner(context, endingSuraSpinner, endingAyahAdapter)

    val repeatOptions = context.resources.getStringArray(R.array.repeatValues)
    val rangeAdapter = ArrayAdapter<CharSequence>(context, ITEM_LAYOUT, repeatOptions)
    rangeAdapter.setDropDownViewResource(
      ITEM_DROPDOWN_LAYOUT
    )
    val verseAdapter = ArrayAdapter<CharSequence>(context, ITEM_LAYOUT, repeatOptions)
    verseAdapter.setDropDownViewResource(
      ITEM_DROPDOWN_LAYOUT
    )
    return view
  }

  private fun arFormat(value: Int): String {
    val numberFormat = NumberFormat.getNumberInstance(Locale("ar"))
    return numberFormat.format(value.toLong())
  }

  override fun onAttach(context: Context) {
    super.onAttach(context)
    (activity as? PagerActivity)?.pagerActivityComponent?.inject(this)
  }

  private val onClickListener = View.OnClickListener { v: View ->
    if (v.id == R.id.apply) {
      apply()
    }
  }

  private fun apply() {
    val context: Context? = activity
    if (context is PagerActivity) {
      val start = SuraAyah(
        startSuraSpinner.selectedItemPosition + 1,
        startAyahSpinner.selectedItemPosition + 1
      )
      val ending = SuraAyah(
        endingSuraSpinner.selectedItemPosition + 1,
        endingAyahSpinner.selectedItemPosition + 1
      )

      // force the correct order
      val currentStart: SuraAyah
      val currentEnding: SuraAyah
      if (ending.after(start)) {
        currentStart = start
        currentEnding = ending
      } else {
        currentStart = ending
        currentEnding = start
      }
      val page = quranInfo.getPageFromSuraAyah(currentStart.sura, currentStart.ayah)
      var repeatVerse = repeatVersePicker.value - 1
      var repeatRange = repeatRangePicker.value - 1
      if (repeatVerse == MAX_REPEATS) {
        repeatVerse = -1
      }
      if (repeatRange == MAX_REPEATS) {
        repeatRange = -1
      }

      // Overwrite if infinite checkbox is checked
      val verseRepeat = repeatVerse
      val rangeRepeat = repeatRange
      val enforceRange = restrictToRange.isChecked
      var updatedRange = false

      val speed = SPEEDS[playbackSpeedPicker.value - 1]
      if (currentStart != decidedStart || currentEnding != decidedEnd) {
        // different range or not playing, so make a new request
        updatedRange = true
        context.playFromAyah(
          currentStart, currentEnding, page, verseRepeat,
          rangeRepeat, enforceRange, speed
        )
      } else if (shouldEnforce != enforceRange || rangeRepeatCount != rangeRepeat || verseRepeatCount != verseRepeat || currentSpeed != speed) {
        // can just update repeat settings
        if (!context.updatePlayOptions(rangeRepeat, verseRepeat, enforceRange, speed)
        ) {
          // audio stopped in the process, let's start it
          context.playFromAyah(
            currentStart, currentEnding, page, verseRepeat, rangeRepeat, enforceRange, speed
          )
        }
      }

      context.endAyahMode()
      if (updatedRange) {
        context.toggleActionBarVisibility(true)
      }
    }
  }

  private fun initializeSuraSpinner(
    context: Context,
    spinner: QuranSpinner,
    ayahAdapter: ArrayAdapter<CharSequence>?
  ) {
    val suras = context.resources.getStringArray(R.array.sura_names)
    for (i in suras.indices) {
      suras[i] = QuranUtils.getLocalizedNumber(context, i + 1) +
          ". " + suras[i]
    }
    val adapter = ArrayAdapter<CharSequence>(context, ITEM_LAYOUT, suras)
    adapter.setDropDownViewResource(ITEM_DROPDOWN_LAYOUT)
    spinner.adapter = adapter
    spinner.onItemSelectedListener = object : OnItemSelectedListener {
      override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, rowId: Long) {
        val sura = position + 1
        val ayahCount = quranInfo.getNumberOfAyahs(sura)
        val ayahs: Array<CharSequence?> = arrayOfNulls(ayahCount)
        for (i in 0 until ayahCount) {
          ayahs[i] = QuranUtils.getLocalizedNumber(context, i + 1)
        }
        ayahAdapter!!.clear()
        for (i in 0 until ayahCount) {
          ayahAdapter.add(ayahs[i])
        }
      }

      override fun onNothingSelected(arg0: AdapterView<*>?) {}
    }
  }

  private fun initializeAyahSpinner(
    context: Context, spinner: QuranSpinner
  ): ArrayAdapter<CharSequence> {
    val ayahAdapter = ArrayAdapter<CharSequence>(context, ITEM_LAYOUT)
    ayahAdapter.setDropDownViewResource(ITEM_DROPDOWN_LAYOUT)
    // initialize the ayah spinner with a single item - "100" - without
    // doing this, measurement can't measure the item and makes the width
    // less than what it should be
    ayahAdapter.add(QuranUtils.getLocalizedNumber(context, 100))
    spinner.adapter = ayahAdapter
    return ayahAdapter
  }

  private fun updateAyahSpinner(
    spinner: QuranSpinner,
    adapter: ArrayAdapter<CharSequence>,
    maxAyah: Int,
    currentAyah: Int
  ) {
    val context: Context? = activity
    if (context != null) {
      val ayahs: Array<CharSequence?> = arrayOfNulls(maxAyah)

      for (i in 0 until maxAyah) {
        ayahs[i] = QuranUtils.getLocalizedNumber(context, i + 1)
      }
      adapter.clear()
      for (i in 0 until maxAyah) {
        adapter.add(ayahs[i])
      }
      spinner.setSelection(currentAyah - 1)
    }
  }

  override fun onToggleDetailsPanel(isVisible: Boolean) {
    isOpen = isVisible
    if (!isOpen) {
      refreshView()
    }
  }

  override fun refreshView() {
    val context: Context? = activity
    val selectionEnd = end
    val selectionStart = start

    var shouldReset = true
    if (context is PagerActivity && selectionStart != null && selectionEnd != null && !isOpen) {
      val lastRequest = context.lastAudioRequest
      val start: SuraAyah
      val ending: SuraAyah
      if (lastRequest != null) {
        // audio playback request is available
        start = lastRequest.start
        ending = lastRequest.end
        if (lastRequest != lastSeenAudioRequest) {
          verseRepeatCount = lastRequest.repeatInfo
          rangeRepeatCount = lastRequest.rangeRepeatInfo
          currentSpeed = lastRequest.playbackSpeed
          shouldEnforce = lastRequest.enforceBounds
        } else {
          shouldReset = false
        }
        decidedStart = start
        decidedEnd = ending
        applyButton.setText(R.string.play_apply)
      } else {
        // we have no last audio request, so we're not playing audio... yet
        start = selectionStart
        if (selectionStart == selectionEnd) {
          val startPage = quranInfo.getPageFromSuraAyah(start.sura, start.ayah)
          val pageBounds = quranInfo.getPageBounds(startPage)
          ending = SuraAyah(pageBounds[2], pageBounds[3])
          shouldEnforce = false
        } else {
          ending = selectionEnd
          shouldEnforce = true
        }
        rangeRepeatCount = 0
        verseRepeatCount = 0
        currentSpeed = 1.0f
        decidedStart = null
        decidedEnd = null
        applyButton.setText(R.string.play_apply_and_play)
      }
      lastSeenAudioRequest = lastRequest

      val maxAyat = quranInfo.getNumberOfAyahs(start.sura)
      if (maxAyat == -1) {
        return
      }
      updateAyahSpinner(startAyahSpinner, startAyahAdapter, maxAyat, start.ayah)
      val endAyat =
        if (ending.sura == start.sura) maxAyat else quranInfo.getNumberOfAyahs(ending.sura)
      updateAyahSpinner(
        endingAyahSpinner, endingAyahAdapter,
        endAyat, ending.ayah
      )
      startSuraSpinner.setSelection(start.sura - 1)
      endingSuraSpinner.setSelection(ending.sura - 1)
      if (shouldReset) {
        restrictToRange.isChecked = shouldEnforce
        repeatRangePicker.value = rangeRepeatCount + 1
        repeatVersePicker.value = verseRepeatCount + 1
        playbackSpeedPicker.value = SPEEDS.indexOf(currentSpeed) + 1
      }
    }
  }

  companion object {
    private val ITEM_LAYOUT = R.layout.sherlock_spinner_item
    private val ITEM_DROPDOWN_LAYOUT = R.layout.sherlock_spinner_dropdown_item
    private const val MAX_REPEATS = 25
    private val SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f)
    private const val DEFAULT_SPEED_INDEX = 2
  }
}
