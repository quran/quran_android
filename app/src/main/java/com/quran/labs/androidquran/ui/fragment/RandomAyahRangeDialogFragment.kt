package com.quran.labs.androidquran.ui.fragment

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.WindowManager.LayoutParams
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.appcompat.app.AlertDialog.Builder
import androidx.fragment.app.DialogFragment
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.ui.helpers.JumpDestination
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.view.ForceCompleteTextView
import dev.zacsweers.metro.Inject
import timber.log.Timber
import com.quran.mobile.common.ui.core.R as UiCoreR

/**
 * [DialogFragment] that lets the user pick a random ayah within a chosen range. The range type is
 * driven by [Mode]: a span of suras, an arbitrary sura/ayah span, or a span of juz'.
 */
class RandomAyahRangeDialogFragment : DialogFragment() {

  @Inject
  lateinit var quranInfo: QuranInfo

  enum class Mode { SURA, AYAH, JUZ }

  private val mode: Mode by lazy {
    Mode.valueOf(arguments?.getString(ARG_MODE) ?: Mode.SURA.name)
  }

  private lateinit var suras: List<String>

  @SuppressLint("InflateParams")
  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val activity: Activity = requireActivity()
    val inflater = activity.layoutInflater

    val layoutRes = when (mode) {
      Mode.SURA -> R.layout.random_ayah_sura_range_dialog
      Mode.AYAH -> R.layout.random_ayah_ayah_range_dialog
      Mode.JUZ -> R.layout.random_ayah_juz_range_dialog
    }
    val titleRes = when (mode) {
      Mode.SURA -> R.string.random_ayah_sura_range_title
      Mode.AYAH -> R.string.random_ayah_ayah_range_title
      Mode.JUZ -> R.string.random_ayah_juz_range_title
    }

    val layout = inflater.inflate(layoutRes, null)

    if (mode == Mode.SURA || mode == Mode.AYAH) {
      setupSuraInputs(activity, layout)
    }

    val builder = Builder(activity, R.style.QuranDialogTheme)
    builder.setTitle(titleRes)
    builder.setView(layout)
    builder.setPositiveButton(getString(R.string.dialog_ok)) { _, _ ->
      layout.requestFocus()
      dismiss()
      onSubmit(layout)
    }
    return builder.create()
  }

  private fun setupSuraInputs(activity: Activity, layout: View) {
    suras = activity.resources.getStringArray(UiCoreR.array.sura_names)
        .mapIndexed { index: Int, sura: String? ->
          QuranUtils.getLocalizedNumber(index + 1) + ". " + sura
        }
    setupSuraSpinner(activity, layout.findViewById(R.id.from_sura_spinner), 1)
    setupSuraSpinner(activity, layout.findViewById(R.id.to_sura_spinner), suras.size)
  }

  private fun setupSuraSpinner(
    activity: Activity,
    view: ForceCompleteTextView,
    defaultSura: Int
  ) {
    val adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, suras)
    view.setAdapter(adapter)
    view.tag = defaultSura
    view.setText(suras[defaultSura - 1])
    view.setOnForceCompleteListener { _: ForceCompleteTextView?, position: Int, _: Long ->
      val enteredText = view.text.toString()
      val sura = when {
        position >= 0 -> suras.indexOf(adapter.getItem(position)) + 1
        suras.contains(enteredText) -> suras.indexOf(enteredText) + 1
        else -> (view.tag as? Int) ?: defaultSura
      }.let { if (it < 1) defaultSura else it }
      view.tag = sura
      view.setText(suras[sura - 1])
    }
  }

  private fun onSubmit(layout: View) {
    try {
      val (start, end) = when (mode) {
        Mode.SURA -> suraRange(layout)
        Mode.AYAH -> ayahRange(layout)
        Mode.JUZ -> juzRange(layout)
      }
      val random = quranInfo.getRandomAyahInRange(start, end)
      val page = quranInfo.getPageFromSuraAyah(random.sura, random.ayah)
      (activity as? JumpDestination)?.jumpToAndHighlight(page, random.sura, random.ayah)
    } catch (e: Exception) {
      Timber.d(e, "Could not pick a random ayah in range")
    }
  }

  private fun suraRange(layout: View): Pair<SuraAyah, SuraAyah> {
    val s1 = layout.findViewById<View>(R.id.from_sura_spinner).tag as Int
    val s2 = layout.findViewById<View>(R.id.to_sura_spinner).tag as Int
    val low = minOf(s1, s2)
    val high = maxOf(s1, s2)
    return SuraAyah(low, 1) to SuraAyah(high, quranInfo.getNumberOfAyahs(high))
  }

  private fun ayahRange(layout: View): Pair<SuraAyah, SuraAyah> {
    val s1 = layout.findViewById<View>(R.id.from_sura_spinner).tag as Int
    val s2 = layout.findViewById<View>(R.id.to_sura_spinner).tag as Int
    val a1 = layout.findViewById<EditText>(R.id.from_ayah).text.toString().toIntOrNull()
        ?.coerceIn(1, quranInfo.getNumberOfAyahs(s1)) ?: 1
    val a2 = layout.findViewById<EditText>(R.id.to_ayah).text.toString().toIntOrNull()
        ?.coerceIn(1, quranInfo.getNumberOfAyahs(s2)) ?: quranInfo.getNumberOfAyahs(s2)
    return SuraAyah(s1, a1) to SuraAyah(s2, a2)
  }

  private fun juzRange(layout: View): Pair<SuraAyah, SuraAyah> {
    val j1 = layout.findViewById<EditText>(R.id.from_juz).text.toString().toIntOrNull()
        ?.coerceIn(1, NUMBER_OF_JUZS) ?: 1
    val j2 = layout.findViewById<EditText>(R.id.to_juz).text.toString().toIntOrNull()
        ?.coerceIn(1, NUMBER_OF_JUZS) ?: NUMBER_OF_JUZS
    val low = minOf(j1, j2)
    val high = maxOf(j1, j2)
    return quranInfo.getJuzStart(low) to juzEnd(high)
  }

  private fun juzEnd(juz: Int): SuraAyah {
    return if (juz >= NUMBER_OF_JUZS) {
      quranInfo.getSuraAyahFromAyahId(quranInfo.getNumberOfAyahsInQuran())
    } else {
      val next = quranInfo.getJuzStart(juz + 1)
      quranInfo.getSuraAyahFromAyahId(quranInfo.getAyahId(next.sura, next.ayah) - 1)
    }
  }

  override fun onAttach(context: Context) {
    super.onAttach(context)
    (context.applicationContext as QuranApplication).applicationComponent
        .inject(this)
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    dialog?.window?.setSoftInputMode(
        LayoutParams.SOFT_INPUT_STATE_VISIBLE or LayoutParams.SOFT_INPUT_ADJUST_PAN
    )
  }

  companion object {
    const val TAG = "RandomAyahRangeDialogFragment"
    private const val ARG_MODE = "mode"
    private const val NUMBER_OF_JUZS = 30

    fun newInstance(mode: Mode): RandomAyahRangeDialogFragment {
      return RandomAyahRangeDialogFragment().apply {
        arguments = Bundle().apply { putString(ARG_MODE, mode.name) }
      }
    }
  }
}
