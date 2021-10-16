package com.quran.page.common.toolbar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Paint.Style.FILL_AND_STROKE
import android.graphics.Path
import android.graphics.Path.FillType.EVEN_ODD
import android.graphics.Point
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.quran.labs.androidquran.common.toolbar.R
import com.quran.page.common.toolbar.dao.SelectedAyahPlacementType

class AyahToolBarPip @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyle: Int = 0
) : View(context, attrs, defStyle) {

  private val paint: Paint

  private var path: Path? = null
  private var position: SelectedAyahPlacementType

  init {
    position = SelectedAyahPlacementType.BOTTOM
    paint = Paint().apply {
      isAntiAlias = true
      color = ContextCompat.getColor(context, R.color.toolbar_background)
      style = FILL_AND_STROKE
    }
  }

  fun ensurePosition(position: SelectedAyahPlacementType) {
    this.position = position
    updatePoints()
  }

  private fun updatePoints() {
    val width = width
    val height = height

    val pointA: Point
    val pointB: Point
    val pointC: Point
    if (position === SelectedAyahPlacementType.BOTTOM) {
      pointA = Point(width / 2, height)
      pointB = Point(0, 0)
      pointC = Point(width, 0)
    } else {
      pointA = Point(width / 2, 0)
      pointB = Point(0, height)
      pointC = Point(width, height)
    }

    path = Path().apply {
      fillType = EVEN_ODD
      moveTo(pointA.x.toFloat(), pointA.y.toFloat())
      lineTo(pointB.x.toFloat(), pointB.y.toFloat())
      lineTo(pointC.x.toFloat(), pointC.y.toFloat())
      close()
    }
    invalidate()
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    updatePoints()
  }

  override fun onDraw(canvas: Canvas) {
    val path = path ?: return
    canvas.drawPath(path, paint)
  }
}
