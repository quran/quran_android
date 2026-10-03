package org.quran.app.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.quran.app.designsystem.Action
import org.quran.app.designsystem.PaperCard
import org.quran.app.designsystem.QuranStrings
import org.quran.app.designsystem.appString
import org.quran.app.model.VerseId

@Composable
internal fun ContinueReadingCard(lastRead: VerseId, onOpen: (Int, Int) -> Unit) {
    PaperCard {
        Text(appString(QuranStrings.continueReading), style = MaterialTheme.typography.titleLarge)
        Text("${lastRead.surah}:${lastRead.ayah}", style = MaterialTheme.typography.headlineSmall)
        Action(appString(QuranStrings.openLastRead), onClick = { onOpen(lastRead.surah, lastRead.ayah) })
    }
}
