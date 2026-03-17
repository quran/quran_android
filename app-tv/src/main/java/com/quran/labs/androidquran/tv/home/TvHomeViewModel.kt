package com.quran.labs.androidquran.tv.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TvHomeViewModel : ViewModel() {

  private val _lastPage = MutableStateFlow<Int?>(null)
  val lastPage: StateFlow<Int?> = _lastPage.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  init {
    loadRecentPage()
  }

  private fun loadRecentPage() {
    _isLoading.value = true
    viewModelScope.launch {
      // TODO: Load from recent page storage
      // For now, set to 604 (a common starting page for Madani)
      _lastPage.value = 604
      _isLoading.value = false
    }
  }

  fun getHomeCards(): List<HomeCard> {
    return homeCards
  }
}
