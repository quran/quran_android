package com.quran.mobile.feature.qarilist

import android.annotation.SuppressLint
import android.content.Context
import android.widget.FrameLayout
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ModalBottomSheetDefaults
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
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
    val state = rememberModalBottomSheetState(
      initialValue = ModalBottomSheetValue.Expanded,
      skipHalfExpanded = true
    )
    val qariListFlow =
      qariListPresenter.qariList(startAyah, endAyah) { QariItem.fromQari(context, it) }
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

    // This is a bit of an intentional hack - if the screen has a display cut out,
    // the scrim should cover the entire screen (including "not-cut-out portions"
    // on the same line as the cutout), but the content should not be cut by the
    // display cut out.
    //
    // Setting a padding on the [ModalBottomSheetLayout] would result in a padding
    // on the scrim also. Setting a padding on the content would extend the sheet's
    // background color, looking odd. Setting a transparent sheet color and using
    // that with padding on the content leaves some artifacts due to a bug that
    // someone reported at https://issuetracker.google.com/issues/227270960.
    //
    // To work around this, the box is full size, containing the full size scrim,
    // and then the modal sheet with padding according to the display cut out.
    Box(modifier = Modifier.fillMaxSize()) {
      Scrim(color = ModalBottomSheetDefaults.scrimColor,
        onDismiss = {
          coroutineScope.launch { state.hide() }
        },
        visible = state.targetValue != ModalBottomSheetValue.Hidden
      )

      ModalBottomSheetLayout(
        sheetState = state,
        sheetShape = RoundedCornerShape(16.dp),
        scrimColor = Color.Unspecified,
        sheetBackgroundColor = MaterialTheme.colorScheme.surface,
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
              onQariSelected = onQariSelected,
              modifier = Modifier.fillMaxHeight(0.97f)
            )
          }
        },
        modifier = Modifier.displayCutoutPadding()
      ) {
      }
    }
  }

  // Scrim Composable taken from [ModalBottomSheetLayout]
  // Suppressing Lint for using the close_sheet string from androidx
  @SuppressLint("PrivateResource")
  @Composable
  fun Scrim(
    color: Color,
    onDismiss: () -> Unit,
    visible: Boolean
  ) {
    if (color.isSpecified) {
      val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = TweenSpec()
      )
      val closeSheet = stringResource(id = androidx.compose.ui.R.string.close_sheet)
      val dismissModifier = if (visible) {
        Modifier
          .pointerInput(onDismiss) { detectTapGestures { onDismiss() } }
          .semantics(mergeDescendants = true) {
            contentDescription = closeSheet
            onClick { onDismiss(); true }
          }
      } else {
        Modifier
      }

      Canvas(
        Modifier
          .fillMaxSize()
          .then(dismissModifier)
      ) {
        drawRect(color = color, alpha = alpha)
      }
    }
  }
}
