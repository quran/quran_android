package com.quran.mobile.feature.qarilist

import android.content.Context
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import kotlinx.coroutines.CoroutineScope
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
    val state = rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.HalfExpanded)
    val qariListFlow = qariListPresenter.qariList(startAyah, endAyah)
    val qariListState = qariListFlow.collectAsState(emptyList())
    val currentQariFlow = currentQariManager.flow()
    val currentQariState = currentQariFlow.collectAsState(null)

    val coroutineScope: CoroutineScope = rememberCoroutineScope()
    val closeDialog = {
      coroutineScope.launch {
        state.hide()
      }
    }

    val onQariSelected = { qari: QariItem ->
      closeDialog()
      currentQariManager.setCurrentQari(qari.id)
    }

    ModalBottomSheetLayout(
      sheetState = state,
      sheetShape = RoundedCornerShape(16.dp),
      sheetContent = {
        Column {
          TopAppBar(
            backgroundColor = MaterialTheme.colorScheme.primary,
            title = {
              Text(
                stringResource(R.string.qarilist_select_qari),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primaryContainer
              )
            },
            navigationIcon = {
              IconButton(onClick = { closeDialog() }) {
                Icon(
                  imageVector = Icons.Filled.Close,
                  contentDescription = stringResource(R.string.qarilist_dismiss),
                  tint = MaterialTheme.colorScheme.primaryContainer
                )
              }
            }
          )

          QariList(
            qariListState.value,
            selectedQariId = currentQariState.value?.id ?: -1,
            onQariSelected = onQariSelected,
            modifier = Modifier.fillMaxHeight(0.95f)
          )
        }
      },
    ) {
    }
  }
}
