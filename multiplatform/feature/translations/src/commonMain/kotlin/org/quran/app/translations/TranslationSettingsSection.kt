package org.quran.app.translations

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import org.quran.app.designsystem.QuranTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.quran.app.designsystem.PaperCard
import org.quran.app.designsystem.QuranStrings
import org.quran.app.designsystem.appString
import org.quran.app.model.AppLanguage
import org.quran.app.model.TranslationEdition

@Composable
fun TranslationSettingsSection(
    language: AppLanguage,
    editions: List<TranslationEdition>,
    selectedEditionId: String?,
    isLoading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val selectedEdition = editions.firstOrNull { it.id == selectedEditionId }

    PaperCard {
        Text(appString(QuranStrings.translationMeanings), style = MaterialTheme.typography.titleLarge)
        Text(selectedEdition?.title ?: appString(QuranStrings.arabicOnly))
        if (selectedEdition != null) {
            Text(appString(QuranStrings.translationEditionMetadata, selectedEdition.languageName, selectedEdition.translator, selectedEdition.version))
        }
        QuranTextButton(appString(QuranStrings.chooseEdition), onClick = { showPicker = true })
        if (isLoading) Text(appString(QuranStrings.translationListLoading))
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        QuranTextButton(appString(QuranStrings.refreshList), onClick = onRefresh)
        Text(
            appString(QuranStrings.translationInterpretation),
            style = MaterialTheme.typography.bodySmall,
        )
    }

    if (showPicker) {
        TranslationEditionPicker(
            language = language,
            editions = editions,
            selectedEditionId = selectedEditionId,
            onDismiss = { showPicker = false },
            onSelect = { editionId ->
                onSelect(editionId)
                showPicker = false
            },
        )
    }
}
