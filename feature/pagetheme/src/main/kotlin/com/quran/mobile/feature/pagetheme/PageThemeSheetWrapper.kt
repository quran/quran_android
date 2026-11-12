package com.quran.mobile.feature.pagetheme

import android.content.Context
import android.widget.FrameLayout
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.pagetheme.presenter.PageThemeSheetPresenter
import com.quran.mobile.feature.pagetheme.ui.PageThemeSheet
import kotlinx.coroutines.launch

class PageThemeSheetWrapper(
  context: Context,
  settings: PageThemeSettings,
  private val onDismissed: () -> Unit
) : FrameLayout(context) {

  private val presenter = PageThemeSheetPresenter(context, settings)

  init {
    val composeView = ComposeView(context).apply {
      setContent {
        PageThemeModalSheet()
      }
    }
    addView(composeView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
  }

  @Composable
  private fun PageThemeModalSheet() {
    val moleculeScope = rememberCoroutineScope()
    val stateFlow = remember {
      moleculeScope.launchMolecule(mode = RecompositionMode.ContextClock) {
        presenter.present()
      }
    }
    val state by stateFlow.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    QuranTheme(useDarkTheme = isSystemInDarkTheme() || state.isNightPage) {
      ModalBottomSheet(
        onDismissRequest = onDismissed,
        sheetState = sheetState,
        scrimColor = Color.Transparent
      ) {
        PageThemeSheet(
          state = state,
          onClose = {
            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismissed() }
          }
        )
      }
    }
  }
}
