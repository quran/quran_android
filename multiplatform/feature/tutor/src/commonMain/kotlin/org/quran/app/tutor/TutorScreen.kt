package org.quran.app.tutor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.quran.app.designsystem.Action
import org.quran.app.designsystem.ArabicVerse
import org.quran.app.designsystem.PaperCard
import org.quran.app.designsystem.QuranStrings
import org.quran.app.designsystem.ScreenTitle
import org.quran.app.designsystem.appString
import org.quran.app.model.StudyProgress
import org.quran.app.model.Verse

@Composable
fun TutorScreen(verse: Verse, progress: StudyProgress) {
    var step by remember(verse.id) { mutableStateOf(0) }
    val steps = listOf(
        QuranStrings.studyReadStep,
        QuranStrings.studyReciteStep,
        QuranStrings.studyPracticeStep,
    )
    Column(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle(appString(QuranStrings.guidedStudy), "${verse.id.surah}:${verse.id.ayah}")
        PaperCard {
            ArabicVerse(verse.arabic, progress.childMode)
            Text(verse.source)
        }
        PaperCard {
            Text(appString(steps[step]))
            Action(
                appString(if (step == steps.lastIndex) QuranStrings.restartGuidance else QuranStrings.nextStep),
                { step = (step + 1) % steps.size },
            )
        }
        PaperCard {
            Text(appString(QuranStrings.aiLearningAssistant), style = MaterialTheme.typography.titleLarge)
            Text(appString(if (progress.childMode) QuranStrings.childrenGuidanceHelp else QuranStrings.aiDeferredHelp))
        }
    }
}
