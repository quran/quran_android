package com.quran.labs.androidquran.extra.feature.linebyline

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.quran.labs.androidquran.common.ui.core.CleanablePageFragment

class QuranLineByLineFragment : Fragment(), CleanablePageFragment {
  private var wrapperView: QuranLineByLineWrapperView? = null

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    val context = requireContext()
    return QuranLineByLineWrapperView(context, currentPage()).also {
      wrapperView = it
    }
  }

  override fun cleanup() {
    wrapperView?.cleanup()
    wrapperView = null
  }

  override fun onDestroyView() {
    cleanup()
    super.onDestroyView()
  }

  private fun currentPage(): Int {
    return arguments?.getInt(PARAM_PAGE_NUMBER) ?: 1
  }

  companion object {
    private const val PARAM_PAGE_NUMBER = "pageNumber"

    fun newInstance(page: Int): QuranLineByLineFragment {
      val bundle = Bundle().apply { putInt(PARAM_PAGE_NUMBER, page) }
      return QuranLineByLineFragment().apply { arguments = bundle }
    }
  }
}
