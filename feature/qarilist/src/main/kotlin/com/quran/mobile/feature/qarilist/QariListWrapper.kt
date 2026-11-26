package com.quran.mobile.feature.qarilist

import android.content.Context
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.common.audio.repository.CurrentQariManager
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.qarilist.di.QariListWrapperInjector
import com.quran.mobile.feature.qarilist.model.QariUiModel
import com.quran.mobile.feature.qarilist.presenter.QariListPresenter
import com.quran.mobile.feature.qarilist.ui.QariList
import dev.zacsweers.metro.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun normalizeQariSearchText(input: String): String {
  if (input.isEmpty()) return ""
  val sb = StringBuilder(input.length)
  for (ch in input) {
    val code = ch.code
    if ((code in 0x064B..0x065F) || code == 0x0670 || code == 0x0640) {
      continue
    }
    val mapped = when (ch) {
      '\u0622', '\u0623', '\u0625', '\u0671' -> '\u0627' // آ أ إ ٱ -> ا
      '\u0629' -> '\u0647' // ة -> ه
      '\u0649', '\u06CC' -> '\u064A' // ى ی -> ي
      else -> ch.lowercaseChar()
    }
    sb.append(mapped)
  }
  return sb.toString()
}

internal fun filterQaris(
  qaris: ImmutableList<QariUiModel>,
  query: String
): ImmutableList<QariUiModel> {
  if (query.isBlank()) {
    return qaris
  }
  val trimmed = query.trim()
  val normalizedQuery = normalizeQariSearchText(trimmed)
  if (normalizedQuery.isEmpty()) {
    return qaris
  }
  return qaris
    .filter { model ->
      model.qariItem.name.contains(trimmed, ignoreCase = true) ||
        normalizeQariSearchText(model.qariItem.name).contains(normalizedQuery)
    }
    .toImmutableList()
}

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
    val qariListFlow = remember(qariListPresenter, startAyah, endAyah) {
      qariListPresenter.qariList(startAyah, endAyah) { QariItem.fromQari(context, it) }
    }
    val qariListState = qariListFlow.collectAsStateWithLifecycle(persistentListOf())
    val currentQariFlow = remember(currentQariManager) { currentQariManager.flow() }
    val currentQariState = currentQariFlow.collectAsStateWithLifecycle(null)

    val coroutineScope: CoroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val closeDialog = remember(focusManager, coroutineScope, state) {
      {
        focusManager.clearFocus()
        coroutineScope.launch {
          state.bottomSheetState.hide()
        }
        Unit
      }
    }

    val onQariSelected = remember(closeDialog, currentQariManager) {
      { qari: QariItem ->
        closeDialog()
        currentQariManager.setCurrentQari(qari.id)
      }
    }

    var searchQuery by remember { mutableStateOf("") }
    val filteredQaris by remember(qariListState.value) {
      derivedStateOf {
        filterQaris(qariListState.value, searchQuery)
      }
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
        containerColor = Color.Transparent,
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetContentColor = MaterialTheme.colorScheme.contentColorFor(MaterialTheme.colorScheme.background),
        sheetContent = {
          Column(
            modifier = Modifier
              .fillMaxHeight()
              .imePadding()
          ) {
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
                    imageVector = QuranIcons.Close,
                    contentDescription = stringResource(R.string.qarilist_dismiss)
                  )
                }
              }
            )

            OutlinedTextField(
              value = searchQuery,
              onValueChange = { searchQuery = it },
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
              placeholder = { Text(stringResource(R.string.qarilist_search_hint)) },
              leadingIcon = {
                Icon(imageVector = QuranIcons.Search, contentDescription = null)
              },
              trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                  IconButton(onClick = { searchQuery = "" }) {
                    Icon(
                      imageVector = QuranIcons.Close,
                      contentDescription = stringResource(R.string.qarilist_clear_search)
                    )
                  }
                }
              },
              singleLine = true
            )

            if (searchQuery.isNotBlank() && filteredQaris.isEmpty()) {
              Box(
                modifier = Modifier
                  .weight(1f)
                  .fillMaxWidth()
                  .navigationBarsPadding()
                  .padding(24.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = stringResource(R.string.qarilist_no_results),
                  style = MaterialTheme.typography.bodyLarge,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            } else {
              QariList(
                filteredQaris,
                selectedQariId = currentQariState.value?.id ?: -1,
                onQariSelected = onQariSelected,
                contentPadding = WindowInsets.navigationBars
                  .exclude(WindowInsets.ime)
                  .only(WindowInsetsSides.Bottom)
                  .asPaddingValues(),
                modifier = Modifier.weight(1f)
              )
            }
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

        LaunchedEffect(hasExpanded.value, state.bottomSheetState.currentValue) {
          if (hasExpanded.value && state.bottomSheetState.currentValue == SheetValue.PartiallyExpanded) {
            closeDialog()
          }
        }
      }
    }
  }
}
