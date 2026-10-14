package com.quran.labs.androidquran.ui.bottomsheet

import android.app.Dialog
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.ThemeUtil

class PageThemeBottomSheet : BottomSheetDialogFragment() {
  private val themes = PageTheme.getAllThemes()
  private var recyclerView: RecyclerView? = null
  private var spinner: Spinner? = null
  private val settings get() = QuranSettings.getInstance(requireContext())
  private val preferencesListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
    if (key == Constants.PREF_PAGE_THEME || key == Constants.PREF_APP_THEME) {
      recyclerView?.adapter?.notifyDataSetChanged()
      updateGridColumns()
      updateAppearanceSelection()
    }
  }

  override fun getTheme(): Int = R.style.Quran_BottomSheetDialog

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
    object : BottomSheetDialog(requireContext(), theme) {
      override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Apply after Material's window setup. Floating dialogs on newer Android versions
        // otherwise retain navigation-bar fitting even with decor fitting disabled.
        window?.let { window ->
          WindowCompat.setDecorFitsSystemWindows(window, false)
          val darkUi = context.resources.configuration.uiMode and
              Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
          WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = !darkUi
          if (Build.VERSION.SDK_INT < 26) {
            // Older Android versions only support white navigation icons.
            window.navigationBarColor = Color.BLACK
          }
          if (Build.VERSION.SDK_INT >= 30) {
            window.attributes = window.attributes.apply { setFitInsetsTypes(0) }
          }
        }
        findViewById<View>(com.google.android.material.R.id.container)?.fitsSystemWindows = false
        findViewById<View>(com.google.android.material.R.id.coordinator)?.fitsSystemWindows = false
      }
    }

  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
  ): View = inflater.inflate(R.layout.bottom_sheet_page_theme, container, false)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    ViewCompat.setAccessibilityPaneTitle(view, getString(R.string.page_theme_title))
    view.findViewById<View>(R.id.close_theme_sheet).setOnClickListener { dismiss() }
    val content = view.findViewById<View>(R.id.theme_sheet_content)
    val initialBottom = content.paddingBottom
    val initialLeft = content.paddingLeft
    val initialRight = content.paddingRight
    ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
      val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
      content.updatePadding(
        left = initialLeft + bars.left,
        right = initialRight + bars.right,
        bottom = initialBottom + bars.bottom
      )
      insets
    }
    ViewCompat.requestApplyInsets(view)

    spinner = view.findViewById<Spinner>(R.id.app_theme_spinner).apply {
      val labels = listOf(R.string.theme_light, R.string.theme_dark, R.string.theme_system)
        .map { getString(it) }
      adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, labels).apply {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
      }
      onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>, v: View?, position: Int, id: Long) {
          val appearance = appearances[position]
          if (appearance != settings.currentTheme()) {
            settings.setAppTheme(appearance)
            ThemeUtil.setTheme(appearance, settings.pageTheme)
          }
        }
        override fun onNothingSelected(parent: AdapterView<*>) = Unit
      }
    }
    updateAppearanceSelection()

    recyclerView = view.findViewById<RecyclerView>(R.id.themes_recycler_view).apply {
      val grid = GridLayoutManager(requireContext(), 3)
      layoutManager = grid
      adapter = ThemeCardAdapter()
      addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
        // Changing spans inside RecyclerView's layout pass can leave the old column widths.
        view.post { updateGridColumns() }
      }
    }
    view.doOnLayout { recyclerView?.post { updateGridColumns() } }
  }

  private fun updateGridColumns() {
    val recycler = recyclerView ?: return
    val grid = recycler.layoutManager as GridLayoutManager
    val scale = recycler.resources.configuration.fontScale.coerceIn(1f, 1.5f)
    val minimumWidth = 104 * recycler.resources.displayMetrics.density * scale
    val columns = ((recycler.width - recycler.paddingLeft - recycler.paddingRight) / minimumWidth)
      .toInt().coerceIn(1, 3)
    if (grid.spanCount != columns) grid.spanCount = columns
  }

  override fun onStart() {
    super.onStart()
    settings.registerPreferencesListener(preferencesListener)
    (dialog as? BottomSheetDialog)?.behavior?.apply {
      maxHeight = (resources.displayMetrics.heightPixels * 0.85f).toInt()
      state = BottomSheetBehavior.STATE_EXPANDED
    }
    view?.let { ViewCompat.requestApplyInsets(it) }
  }

  override fun onStop() {
    settings.unregisterPreferencesListener(preferencesListener)
    super.onStop()
  }

  override fun onDestroyView() {
    recyclerView?.adapter = null
    recyclerView = null
    spinner = null
    super.onDestroyView()
  }

  private fun updateAppearanceSelection() {
    val index = appearances.indexOf(settings.currentTheme()).takeIf { it >= 0 } ?: 2
    spinner?.setSelection(index, false)
  }

  private inner class ThemeCardAdapter : RecyclerView.Adapter<ThemeCardViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeCardViewHolder =
      ThemeCardViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_page_theme_card, parent, false))

    override fun onBindViewHolder(holder: ThemeCardViewHolder, position: Int) = holder.bind(themes[position])
    override fun getItemCount(): Int = themes.size
  }

  private inner class ThemeCardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val card: CardView = itemView.findViewById(R.id.theme_card)
    private val preview: TextView = itemView.findViewById(R.id.arabic_preview)
    private val name: TextView = itemView.findViewById(R.id.theme_name)
    private val border: View = itemView.findViewById(R.id.selection_border)
    private val check: View = itemView.findViewById(R.id.selection_check)

    init {
      ViewCompat.setAccessibilityDelegate(card, object : AccessibilityDelegateCompat() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
          super.onInitializeAccessibilityNodeInfo(host, info)
          info.className = android.widget.RadioButton::class.java.name
          info.isCheckable = true
          info.isChecked = host.isSelected
        }
      })
    }

    fun bind(theme: PageTheme) {
      val palette = ThemeUtil.paletteContext(itemView.context, settings.currentTheme())
      val textColor = palette.getColor(theme.textColorRes)
      card.setCardBackgroundColor(palette.getColor(theme.backgroundColorRes))
      preview.setTextColor(textColor)
      name.setTextColor(textColor)
      name.text = theme.getDisplayName(itemView.context)
      card.contentDescription = theme.getDisplayName(itemView.context)
      card.isSelected = theme.storageKey == settings.pageTheme
      border.visibility = if (card.isSelected) View.VISIBLE else View.GONE
      check.visibility = border.visibility
      card.setOnClickListener {
        if (theme.storageKey != settings.pageTheme) {
          settings.pageTheme = theme.storageKey
          // AppCompat restores this DialogFragment when a change in app appearance recreates
          // the host. Palette-only changes update the reader through its preference listener.
          ThemeUtil.setTheme(settings.currentTheme(), theme.storageKey)
        }
      }
    }
  }

  companion object {
    const val TAG = "PageThemeBottomSheet"
    private val appearances = listOf(Constants.THEME_LIGHT, Constants.THEME_DARK, Constants.THEME_DEFAULT)
  }
}
