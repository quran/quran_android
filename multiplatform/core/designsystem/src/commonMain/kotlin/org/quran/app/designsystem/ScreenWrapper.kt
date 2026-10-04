package org.quran.app.designsystem

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection

/** Screens consume the supplied content insets once; this wrapper owns bars and safe areas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenWrapper(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    val backLabel = appString(QuranStrings.back)
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val titleModifier = if (onBack != null) Modifier.padding(start = QuranSpacing.Small) else Modifier
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { QuranText(title, titleModifier.semantics { heading() }, QuranTextVariant.Title) },
                navigationIcon = {
                    if (onBack != null) QuranTextButton(if (isRtl) "$backLabel ›" else "‹ $backLabel", onBack)
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        content = content,
    )
}
