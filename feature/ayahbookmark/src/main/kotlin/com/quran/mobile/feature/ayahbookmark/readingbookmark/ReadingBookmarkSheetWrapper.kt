package com.quran.mobile.feature.ayahbookmark.readingbookmark

import android.content.Context
import android.widget.FrameLayout
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.ComposeView
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import com.quran.data.core.ReadingBookmarkUpdater
import com.quran.data.model.bookmark.ReadingBookmarkTarget
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.ayahbookmark.readingbookmark.di.ReadingBookmarkSheetWrapperInjector
import com.quran.mobile.feature.ayahbookmark.readingbookmark.presenter.ReadingBookmarkSheetPresenter
import com.quran.mobile.feature.ayahbookmark.readingbookmark.state.ReadingBookmarkSheetEvent
import com.quran.mobile.feature.ayahbookmark.readingbookmark.ui.ReadingBookmarkSheet
import dev.zacsweers.metro.Inject

class ReadingBookmarkSheetWrapper(
  context: Context,
  private val target: ReadingBookmarkTarget,
  private val isNested: Boolean = false,
  private val onDismissed: () -> Unit
) : FrameLayout(context) {

  @Inject
  lateinit var readingBookmarkSheetPresenter: ReadingBookmarkSheetPresenter

  @Inject
  lateinit var readingBookmarkUpdater: ReadingBookmarkUpdater

  private var chosenAction: ReadingBookmarkAction? = null

  private val actionSink: (ReadingBookmarkAction) -> Unit = { chosenAction = it }

  init {
    (context as? ReadingBookmarkSheetWrapperInjector)?.injectReadingBookmarkSheetWrapper(this)

    val composeView = ComposeView(context).apply {
      setContent {
        QuranTheme {
          ReadingBookmarkBottomSheet()
        }
      }
    }
    addView(composeView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
  }

  @Composable
  private fun ReadingBookmarkBottomSheet() {
    val moleculeScope = rememberCoroutineScope()
    val stateFlow = remember {
      moleculeScope.launchMolecule(mode = RecompositionMode.ContextClock) {
        readingBookmarkSheetPresenter.present(target, isNested, actionSink)
      }
    }
    val state by stateFlow.collectAsState()

    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(state.isDismissed) {
      if (state.isDismissed) {
        sheetState.hide()
        finish()
      }
    }

    ModalBottomSheet(
      onDismissRequest = { state.eventSink(ReadingBookmarkSheetEvent.Dismiss) },
      sheetState = sheetState,
      dragHandle = null,
      shape = RectangleShape,
      containerColor = Color.Transparent,
      contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
      contentColor = MaterialTheme.colorScheme.onSurface
    ) {
      ReadingBookmarkSheet(state = state)
    }
  }

  private fun finish() {
    val action = chosenAction
    chosenAction = null
    onDismissed()
    when (action) {
      is ReadingBookmarkAction.Place ->
        readingBookmarkUpdater.placeReadingBookmark(action.slot, action.target)

      is ReadingBookmarkAction.Clear -> readingBookmarkUpdater.clearReadingBookmark(action.slot)
      null -> Unit
    }
  }
}
