package com.quran.mobile.feature.qarilist

import android.content.Context
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.common.audio.repository.CurrentQariManager
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.qarilist.di.QariListWrapperInjector
import com.quran.mobile.feature.qarilist.presenter.QariListPresenter
import com.quran.mobile.feature.qarilist.ui.QariList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

class QariListWrapper(
  context: Context,
  private val startAyah: SuraAyah,
  private val endAyah: SuraAyah,
): FrameLayout(context) {

  @Inject
  lateinit var qariListPresenter: QariListPresenter

  @Inject
  lateinit var currentQariManager: CurrentQariManager

  init {
    (context as? QariListWrapperInjector)?.injectQariListWrapper(this)

    val composeView = ComposeView(context).apply {
      setContent {
        QuranTheme {
          QariListBottomSheet()
        }
      }
    }
    addView(composeView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
  }

  @Composable
  fun QariListBottomSheet() {
    val state = rememberBottomSheetScaffoldState(
      bottomSheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.Hidden,
        skipHiddenState = false
      )
    )
    val qariListFlow =
      qariListPresenter.qariList(startAyah, endAyah) { QariItem.fromQari(context, it) }
    val qariListState = qariListFlow.collectAsState(persistentListOf())
    val currentQariFlow = currentQariManager.flow()
    val currentQariState = currentQariFlow.collectAsState(null)

    val coroutineScope: CoroutineScope = rememberCoroutineScope()
    val closeDialog = {
      coroutineScope.launch {
        state.bottomSheetState.hide()
      }
    }

    val onQariSelected = { qari: QariItem ->
      closeDialog()
      currentQariManager.setCurrentQari(qari.id)
    }

    Box(modifier = Modifier
      .fillMaxSize()
      .displayCutoutPadding()
    ) {
      BottomSheetScaffold(
        scaffoldState = state,
        sheetPeekHeight = 0.dp,
        sheetDragHandle = null,
        sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetContentColor = MaterialTheme.colorScheme.contentColorFor(MaterialTheme.colorScheme.background),
        sheetContent = {
          Column {
            TopAppBar(
              title = {
                Text(
                  stringResource(R.string.qarilist_select_qari),
                  style = MaterialTheme.typography.titleLarge
                )
              },
              navigationIcon = {
                IconButton(onClick = { closeDialog() }) {
                  Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.qarilist_dismiss)
                  )
                }
              }
            )

            QariList(
              qariListState.value,
              selectedQariId = currentQariState.value?.id ?: -1,
              onQariSelected = onQariSelected
            )
          }
        }
      ) {
        val hasExpanded = remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
          // delay so sheet animates smoothly, otherwise it just snaps open
          delay(50)
          state.bottomSheetState.expand()
          hasExpanded.value = true
        }

        if (hasExpanded.value && state.bottomSheetState.currentValue == SheetValue.PartiallyExpanded) {
          closeDialog()
        }
      }
    }
  }
}
