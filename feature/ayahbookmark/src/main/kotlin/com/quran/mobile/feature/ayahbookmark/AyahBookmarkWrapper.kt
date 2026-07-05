package com.quran.mobile.feature.ayahbookmark

import android.content.Context
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.ayahbookmark.di.AyahBookmarkWrapperInjector
import com.quran.mobile.feature.ayahbookmark.presenter.AyahBookmarkPresenter
import com.quran.mobile.feature.ayahbookmark.state.AyahBookmarkEvent
import com.quran.mobile.feature.ayahbookmark.ui.AyahBookmark
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

class AyahBookmarkWrapper(
  context: Context,
  private val currentAyah: SuraAyah,
  private val onDismissed: (isBookmarked: Boolean) -> Unit
) : FrameLayout(context) {

  @Inject
  lateinit var ayahBookmarkPresenterFactory: AyahBookmarkPresenter.Factory

  init {
    (context as? AyahBookmarkWrapperInjector)?.injectAyahBookmarkWrapper(this)

    val composeView = ComposeView(context).apply {
      setContent {
        QuranTheme {
          AyahBookmarkBottomSheet()
        }
      }
    }
    addView(composeView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
  }

  @Composable
  private fun AyahBookmarkBottomSheet() {
    val presenter = remember(currentAyah) { ayahBookmarkPresenterFactory.create(currentAyah) }
    val moleculeScope = rememberCoroutineScope()
    val stateFlow = remember {
      moleculeScope.launchMolecule(mode = RecompositionMode.ContextClock) {
        presenter.present()
      }
    }
    val state by stateFlow.collectAsState()

    val scaffoldState = rememberBottomSheetScaffoldState(
      bottomSheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.Hidden,
        skipHiddenState = false
      )
    )
    val hasOpened = remember { mutableStateOf(false) }
    val peekHeight = (LocalConfiguration.current.screenHeightDp / 2).dp

    LaunchedEffect(Unit) {
      // delay so the sheet animates smoothly, otherwise it just snaps open
      delay(50.milliseconds)
      // peeked by default - the user can drag it up to fully expand
      scaffoldState.bottomSheetState.partialExpand()
      hasOpened.value = true
    }

    // presenter-driven dismiss (Done, or the remove-bookmark undo window expiring)
    LaunchedEffect(state.isDismissed) {
      if (state.isDismissed) {
        scaffoldState.bottomSheetState.hide()
      }
    }

    // fires once for either dismiss path above, or the user swiping the sheet away directly
    LaunchedEffect(hasOpened.value, scaffoldState.bottomSheetState.currentValue) {
      if (hasOpened.value && scaffoldState.bottomSheetState.currentValue == SheetValue.Hidden) {
        if (!state.isDismissed) {
          // this is an auto-save sheet - a swipe-to-dismiss still needs to persist
          // pending changes, same as tapping Done would
          state.eventSink(AyahBookmarkEvent.Done)
        }
        onDismissed(state.isReadingBookmarkEnabled || state.collections.any { it.isChecked })
      }
    }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .displayCutoutPadding()
    ) {
      BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = peekHeight,
        sheetDragHandle = null,
        sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = Color.Transparent,
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetContentColor = MaterialTheme.colorScheme.contentColorFor(MaterialTheme.colorScheme.background),
        sheetContent = {
          AyahBookmark(
            state = state,
            modifier = Modifier
              .fillMaxHeight()
              .imePadding()
          )
        }
      ) {
        // no content behind the sheet - this wrapper only exists to host it
      }
    }
  }
}
