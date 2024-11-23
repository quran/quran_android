package com.quran.mobile.linebyline.ui.renderer.calculator

class SidelinesWrapperCalculator(
  private val actualCalculator: PageCalculator
) : PageCalculator {

  override fun calculate(widthWithoutPadding: Int, heightWithoutPadding: Int): Measurements {
    val widthAfterSidelines = (widthWithoutPadding * (1 - SIDELINES_WIDTH)).toInt()
    val measurements = actualCalculator.calculate(widthAfterSidelines, heightWithoutPadding)
    return measurements.copy(sidelinesWidth = (widthWithoutPadding - widthAfterSidelines))
  }

  companion object {
    private const val SIDELINES_WIDTH = 0.1f
  }
}
