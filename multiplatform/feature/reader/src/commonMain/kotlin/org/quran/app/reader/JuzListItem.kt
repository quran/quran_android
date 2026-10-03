package org.quran.app.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.quran.app.designsystem.PaperCard
import org.quran.app.designsystem.QuranButton
import org.quran.app.designsystem.QuranStrings
import org.quran.app.designsystem.QuranText
import org.quran.app.designsystem.QuranTextVariant
import org.quran.app.designsystem.appString
import org.quran.app.model.Juz

@Composable
internal fun JuzListItem(juz: Juz, onOpen: (Int, Int) -> Unit) {
    PaperCard {
        QuranText(appString(QuranStrings.juzTitle, juz.number), variant = QuranTextVariant.Title)
        QuranText(appString(QuranStrings.juzStartsAt, juz.start.surah, juz.start.ayah), variant = QuranTextVariant.Supporting)
        QuranButton(
            text = appString(QuranStrings.readJuz),
            onClick = { onOpen(juz.start.surah, juz.start.ayah) },
            modifier = Modifier.testTag("juz_open_${juz.number}"),
        )
    }
}
