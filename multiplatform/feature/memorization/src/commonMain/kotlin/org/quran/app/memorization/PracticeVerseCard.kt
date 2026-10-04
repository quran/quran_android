package org.quran.app.memorization

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.quran.app.designsystem.*
import org.quran.app.model.Verse

@Composable
fun PracticeVerseCard(verse: Verse, hidden: Boolean, onHiddenChanged: (Boolean) -> Unit) {
    PaperCard(Modifier.testTag("practice_verse_${verse.id.surah}_${verse.id.ayah}")) {
        if (hidden) QuranText(appString(QuranStrings.reciteFromMemory)) else ArabicVerse(verse.arabic, true)
        Action(appString(if (hidden) QuranStrings.revealAyah else QuranStrings.hideAyah), { onHiddenChanged(!hidden) })
    }
}
