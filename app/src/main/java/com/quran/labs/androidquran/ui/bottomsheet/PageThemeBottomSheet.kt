package com.quran.labs.androidquran.ui.bottomsheet

import android.app.Dialog
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.TooltipCompat
import androidx.cardview.widget.CardView
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.ThemeUtil

class PageThemeBottomSheet : BottomSheetDialogFragment() {
  private val themes = PageTheme.entries
  private var recyclerView: RecyclerView? = null
  private var appearanceMenu: MaterialButton? = null
  private var sectionHeader: LinearLayout? = null
  private val settings get() = QuranSettings.getInstance(requireContext())
  private val preferencesListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
    if (key == Constants.PREF_PAGE_THEME || key == Constants.PREF_PAGE_APPEARANCE ||
      key == Constants.PREF_APP_THEME) {
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
    listOf(R.id.theme_sheet_title, R.id.page_theme_label).forEach { id ->
      ViewCompat.setAccessibilityHeading(view.findViewById<View>(id), true)
    }
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

    appearanceMenu = view.findViewById<MaterialButton>(R.id.app_theme_menu).apply {
      TooltipCompat.setTooltipText(this, getString(R.string.appearance))
      setOnClickListener { anchor ->
        val popup = PopupMenu(anchor.context, anchor, Gravity.END)
        appearances.forEachIndexed { index, appearance ->
          popup.menu.add(1, index + 1, index, appearanceLabels[index]).isChecked =
            appearance == settings.pageAppearance
        }
        popup.menu.setGroupCheckable(1, true, true)
        popup.setOnMenuItemClickListener { item ->
          val appearance = appearances[item.itemId - 1]
          if (appearance != settings.pageAppearance) {
            settings.pageAppearance = appearance
          }
          true
        }
        popup.show()
      }
    }
    sectionHeader = view.findViewById<LinearLayout>(R.id.theme_section_header).apply {
      addOnLayoutChangeListener { header, _, _, _, _, _, _, _, _ ->
        header.post { updateSectionHeader() }
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
    appearanceMenu = null
    sectionHeader = null
    super.onDestroyView()
  }

  private fun updateAppearanceSelection() {
    val index = appearances.indexOf(settings.pageAppearance).takeIf { it >= 0 } ?: 0
    appearanceMenu?.apply {
      text = getString(appearanceLabels[index])
      contentDescription = "${getString(R.string.appearance)}: $text"
      post { updateSectionHeader() }
    }
  }

  private fun updateSectionHeader() {
    val header = sectionHeader ?: return
    val button = appearanceMenu ?: return
    if (header.width == 0) return
    val label = header.findViewById<TextView>(R.id.page_theme_label)
    button.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
    val spacing = (16 * resources.displayMetrics.density).toInt()
    val labelWidth = label.paint.measureText(label.text.toString()) + label.paddingLeft + label.paddingRight
    val availableWidth = header.width - header.paddingLeft - header.paddingRight
    val stacked = labelWidth + button.measuredWidth + spacing > availableWidth
    val orientation = if (stacked) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
    if (header.orientation == orientation) return
    header.orientation = orientation
    header.gravity = if (stacked) Gravity.START else Gravity.CENTER_VERTICAL
    label.layoutParams = (label.layoutParams as LinearLayout.LayoutParams).apply {
      width = if (stacked) ViewGroup.LayoutParams.MATCH_PARENT else 0
      weight = if (stacked) 0f else 1f
    }
    button.layoutParams = (button.layoutParams as LinearLayout.LayoutParams).apply {
      marginStart = if (stacked) 0 else spacing
    }
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
      val palette = ThemeUtil.paletteContext(itemView.context, settings.isNightMode)
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
        }
      }
    }
  }

  companion object {
    const val TAG = "PageThemeBottomSheet"
    private val appearances = listOf(
      Constants.PAGE_APPEARANCE_LIGHT,
      Constants.PAGE_APPEARANCE_DARK,
      Constants.PAGE_APPEARANCE_AUTOMATIC
    )
    private val appearanceLabels = listOf(R.string.theme_light, R.string.theme_dark, R.string.theme_automatic)
  }
}
