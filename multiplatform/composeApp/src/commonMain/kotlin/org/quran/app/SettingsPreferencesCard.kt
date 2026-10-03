package org.quran.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import org.quran.app.designsystem.*
import org.quran.app.model.AppLanguage
import org.quran.app.model.StudyProgress

@Composable
internal fun SettingsPreferencesCard(progress: StudyProgress, onProgressChange: (StudyProgress) -> Unit) {
    PaperCard {
        QuranText(appString(QuranStrings.language), variant = QuranTextVariant.Title)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(QuranSpacing.Small)) {
            QuranChoiceChip(appString(QuranStrings.english), progress.language == AppLanguage.ENGLISH,
                onClick = { onProgressChange(progress.copy(language = AppLanguage.ENGLISH)) })
            QuranChoiceChip(appString(QuranStrings.arabic), progress.language == AppLanguage.ARABIC,
                onClick = { onProgressChange(progress.copy(language = AppLanguage.ARABIC)) })
        }
        QuranSettingSwitch(
            title = appString(QuranStrings.childrenMode),
            description = appString(QuranStrings.childrenModeDetail),
            checked = progress.childMode,
            onCheckedChange = { onProgressChange(progress.copy(childMode = it)) },
        )
        QuranText(appString(QuranStrings.localProgress), variant = QuranTextVariant.Supporting)
        QuranText(appString(QuranStrings.memorizedCount, progress.memorized.size))
    }
}
