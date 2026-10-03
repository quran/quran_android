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
import org.quran.app.designsystem.label
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
                    "Translation of the meanings · ${edition.title}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text("${edition.translator} · v${edition.version} · ${edition.publisher}")
                TextButton(onClick = { uriHandler.openUri(edition.sourceUrl) }) {
                    Text(label(language, "Source and license", "المصدر والترخيص"))
                }
            }
        }
        isLoading -> Text(label(language, "Loading selected translation…", "جارٍ تحميل الترجمة المختارة…"))
        errorMessage != null -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(errorMessage, Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry) { Text(label(language, "Retry", "إعادة المحاولة")) }
        }
        else -> Text(label(language, "Choose a translation of the meanings in Settings.", "اختر ترجمة للمعاني من الإعدادات."))
    }
}

@Composable
fun TranslationVerseText(
    language: AppLanguage,
    edition: TranslationEdition?,
    translation: VerseTranslation?,
) {
    if (edition == null || translation == null) return
    var showFootnotes by remember(translation) { mutableStateOf(false) }
    val direction = if (edition.direction == TextDirection.RTL) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        Column {
            Text(translation.text, style = MaterialTheme.typography.bodyLarge)
            if (translation.footnotes.isNotEmpty()) {
                TextButton(onClick = { showFootnotes = !showFootnotes }) {
                    Text(label(language, if (showFootnotes) "Hide notes" else "Show notes", if (showFootnotes) "إخفاء الحواشي" else "عرض الحواشي"))
                }
                if (showFootnotes) Text(translation.footnotes, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
