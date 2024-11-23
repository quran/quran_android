package com.quran.labs.androidquran.extra.feature.linebyline

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

class QuranLineByLineFragment : Fragment() {

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    val context = requireContext()
    return QuranLineByLineWrapperView(context, currentPage())
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
