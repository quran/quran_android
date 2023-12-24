package com.quran.page.common.toolbar

import android.content.Context
import android.util.AttributeSet
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MenuItem.OnMenuItemClickListener
import android.view.View
import android.view.View.OnClickListener
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import com.quran.data.model.selection.SelectionIndicator
import com.quran.labs.androidquran.common.toolbar.R
import com.quran.page.common.toolbar.dao.SelectedAyahPlacementType
import com.quran.page.common.toolbar.di.AyahToolBarInjector
import com.quran.page.common.toolbar.extension.toInternalPosition
import javax.inject.Inject

class AyahToolBar @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyle: Int = 0
) : ViewGroup(context, attrs, defStyle), OnClickListener, OnLongClickListener, AyahSelectionReactor {

  private var menu: Menu
  private val pipWidth: Int
  private val pipHeight: Int
  private val itemWidth: Int
  private val ayahMenu = R.menu.ayah_menu
  private val menuLayout: LinearLayout
  private val toolBarPip: AyahToolBarPip
  private val toolBarTotalHeight: Int

  private var pipOffset = 0f
  private var pipPosition: SelectedAyahPlacementType
  private var currentMenu: Menu? = null
  private var itemSelectedListener: OnMenuItemClickListener? = null

  var isShowing = false
    private set

  var flavor: String = ""
  var longPressLambda: ((CharSequence) -> Unit) = {}
  var isRecitationEnabled = false

  @Inject
  lateinit var ayahToolBarPresenter: AyahToolBarPresenter

  init {
    val resources = context.resources
    itemWidth = resources.getDimensionPixelSize(R.dimen.toolbar_item_width)
    val toolBarHeight = resources.getDimensionPixelSize(R.dimen.toolbar_height)
    pipHeight = resources.getDimensionPixelSize(R.dimen.toolbar_pip_height)
    pipWidth = resources.getDimensionPixelSize(R.dimen.toolbar_pip_width)
    val background = ContextCompat.getColor(context, R.color.toolbar_background)

    toolBarTotalHeight = resources.getDimensionPixelSize(R.dimen.toolbar_total_height)

    menuLayout = LinearLayout(context).apply {
      layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, toolBarHeight)
      setBackgroundColor(background)
    }
    addView(menuLayout)

    pipPosition = SelectedAyahPlacementType.BOTTOM
    toolBarPip = AyahToolBarPip(context)
    toolBarPip.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, pipHeight)
    addView(toolBarPip)

    // used to use MenuBuilder, but now it has @RestrictTo, so using this clever trick from
    // StackOverflow - PopupMenu generates a new MenuBuilder internally, so this just lets us
    // get that menu and do whatever we want with it.
    menu = PopupMenu(this.context, this).menu
    val inflater = MenuInflater(this.context)
    inflater.inflate(ayahMenu, menu)
    showMenu(menu)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    // inject the present and bind
    (context as AyahToolBarInjector).injectToolBar(this)
    ayahToolBarPresenter.bind(this)
  }

  override fun onDetachedFromWindow() {
    ayahToolBarPresenter.unbind(this)
    super.onDetachedFromWindow()
  }

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    val totalWidth = measuredWidth
    val pipWidth = toolBarPip.measuredWidth
    val pipHeight = toolBarPip.measuredHeight
    val menuWidth = menuLayout.measuredWidth
    val menuHeight = menuLayout.measuredHeight
    var pipLeft = pipOffset.toInt()
    if (pipLeft + pipWidth > totalWidth) {
      pipLeft = totalWidth / 2 - pipWidth / 2
    }

    // overlap the pip and toolbar by 1px to avoid occasional gap
    if (pipPosition == SelectedAyahPlacementType.TOP) {
      toolBarPip.layout(pipLeft, 0, pipLeft + pipWidth, pipHeight + 1)
      menuLayout.layout(0, pipHeight, menuWidth, pipHeight + menuHeight)
    } else {
      toolBarPip.layout(pipLeft, menuHeight - 1, pipLeft + pipWidth, menuHeight + pipHeight)
      menuLayout.layout(0, 0, menuWidth, menuHeight)
    }
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    measureChild(menuLayout, widthMeasureSpec, heightMeasureSpec)
    val width = menuLayout.measuredWidth
    var height = menuLayout.measuredHeight
    measureChild(
      toolBarPip,
      MeasureSpec.makeMeasureSpec(pipWidth, MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(pipHeight, MeasureSpec.EXACTLY)
    )
    height += toolBarPip.measuredHeight
    setMeasuredDimension(
      resolveSize(width, widthMeasureSpec),
      resolveSize(height, heightMeasureSpec)
    )
  }

  private fun showMenu(menu: Menu, force: Boolean = false) {
    if (currentMenu === menu && !force) {
      // no need to re-draw
      return
    }

    // disable sharing for warsh and qaloon
    val menuItem = menu.findItem(R.id.cab_share_ayah)
    if (menuItem != null && flavor == "qaloon") {
      menuItem.isVisible = false
    }

    // If recitation is enabled, show it in the menu
    if (isRecitationEnabled) {
      menu.findItem(R.id.cab_recite_from_here)?.apply { isVisible = true }
    }

    menuLayout.removeAllViews()
    val count = menu.size()
    for (i in 0 until count) {
      val item = menu.getItem(i)
      if (item.isVisible) {
        val view = getMenuItemView(item)
        menuLayout.addView(view)
      }
    }
    currentMenu = menu
  }

  private fun getMenuItemView(item: MenuItem): View {
    return ImageButton(context).apply {
      setImageDrawable(item.icon)
      setBackgroundResource(R.drawable.toolbar_button)
      id = item.itemId
      layoutParams = LayoutParams(itemWidth, LayoutParams.MATCH_PARENT)
      setOnClickListener(this@AyahToolBar)
      setOnLongClickListener(this@AyahToolBar)
    }
  }

  // relying on getWidth() may give us the width of a shorter
  // submenu instead of the actual menu
  private val toolBarWidth: Int
    get() = menu.size() * itemWidth

  fun setBookmarked(bookmarked: Boolean) {
    val bookmarkItem = menu.findItem(R.id.cab_bookmark_ayah)
    bookmarkItem.setIcon(if (bookmarked) R.drawable.ic_favorite else R.drawable.ic_not_favorite)
    val bookmarkButton = findViewById<ImageButton>(R.id.cab_bookmark_ayah)
    bookmarkButton?.setImageDrawable(bookmarkItem.icon)
  }

  override fun onSelectionChanged(selectionIndicator: SelectionIndicator, reset: Boolean) {
    if (reset) {
      resetMenu()
    }

    if (selectionIndicator is SelectionIndicator.None ||
        selectionIndicator is SelectionIndicator.ScrollOnly) {
      hideMenu()
    } else {
      updatePosition(selectionIndicator)
      showMenu()
    }
  }

  override fun updateBookmarkStatus(isBookmarked: Boolean) {
    setBookmarked(isBookmarked)
  }

  private fun updatePosition(position: SelectionIndicator) {
    val parentView = parent as View
    val internalPosition = position.toInternalPosition(
      parentView.width, parentView.height, toolBarWidth, toolBarTotalHeight
    )

    if (internalPosition != null) {
      val needsLayout =
        internalPosition.pipPosition != pipPosition || pipOffset != internalPosition.pipOffset
      ensurePipPosition(internalPosition.pipPosition)
      pipOffset = internalPosition.pipOffset
      val x = internalPosition.x
      val y = internalPosition.y
      setPosition(x, y)
      if (needsLayout) {
        requestLayout()
      }
    }
  }

  private fun setPosition(x: Float, y: Float) {
    translationX = x
    translationY = y
  }

  private fun ensurePipPosition(position: SelectedAyahPlacementType) {
    pipPosition = position
    toolBarPip.ensurePosition(position)
  }

  private fun resetMenu(force: Boolean = false) {
    showMenu(menu, force)
  }

  private fun showMenu() {
    showMenu(menu)
    visibility = VISIBLE
    isShowing = true
  }

  private fun hideMenu() {
    isShowing = false
    visibility = GONE
  }

  fun setOnItemSelectedListener(listener: OnMenuItemClickListener?) {
    itemSelectedListener = listener
  }

  override fun onClick(v: View) {
    val item = menu.findItem(v.id) ?: return
    val subMenu = if (item.hasSubMenu()) item.subMenu else null
    if (subMenu != null) {
      showMenu(subMenu)
    } else {
      itemSelectedListener?.onMenuItemClick(item)
    }
  }

  override fun onLongClick(v: View): Boolean {
    val item = menu.findItem(v.id)
    val title = item?.title
    if (title != null) {
      longPressLambda(title)
      return true
    }
    return false
  }

  fun setMenuItemVisibility(itemId: Int, isVisible: Boolean) {
    val item = menu.findItem(itemId) ?: return
    if (item.isVisible != isVisible) {
      item.isVisible = isVisible
      resetMenu(true)
    }
  }
}
