package org.quran.app.translations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.LayoutDirection
import org.quran.app.designsystem.QuranTranslationText
import org.quran.app.designsystem.QuranStrings
import org.quran.app.designsystem.appString
import org.quran.app.model.ReadingTextSize
import org.quran.app.model.AppLanguage
import org.quran.app.model.TextDirection
import org.quran.app.model.TranslationEdition
import org.quran.app.model.VerseTranslation

@Composable
fun TranslationEditionHeader(
    language: AppLanguage,
    edition: TranslationEdition?,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
) {
    when {
        edition != null -> {
            val uriHandler = LocalUriHandler.current
            Column {
                Text(
                    appString(QuranStrings.translationEditionTitle, edition.title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(appString(QuranStrings.translationSourceMetadata, edition.translator, edition.version, edition.publisher))
                TextButton(onClick = { uriHandler.openUri(edition.sourceUrl) }) {
                    Text(appString(QuranStrings.sourceAndLicense))
                }
            }
        }
        isLoading -> Text(appString(QuranStrings.translationLoading))
        errorMessage != null -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(errorMessage, Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry) { Text(appString(QuranStrings.retry)) }
        }
        else -> Text(appString(QuranStrings.chooseTranslationHelp))
    }
}

@Composable
fun TranslationVerseText(
    language: AppLanguage,
    edition: TranslationEdition?,
    translation: VerseTranslation?,
    textSize: ReadingTextSize = ReadingTextSize.DEFAULT,
) {
    if (edition == null || translation == null) return
    var showFootnotes by remember(translation) { mutableStateOf(false) }
    val direction = if (edition.direction == TextDirection.RTL) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        Column {
            QuranTranslationText(translation.text, textSize = textSize)
            if (translation.footnotes.isNotEmpty()) {
                TextButton(onClick = { showFootnotes = !showFootnotes }) {
                    Text(appString(if (showFootnotes) QuranStrings.hideNotes else QuranStrings.showNotes))
                }
                if (showFootnotes) Text(translation.footnotes, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
