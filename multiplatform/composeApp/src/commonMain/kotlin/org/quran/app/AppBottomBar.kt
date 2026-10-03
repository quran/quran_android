package org.quran.app

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import androidx.compose.material3.MaterialTheme
import org.quran.app.designsystem.QuranStrings
import org.quran.app.designsystem.appString

@Composable
internal fun AppBottomBar(
    currentDestination: NavKey,
    onNavigate: (NavKey) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        listOf(
            Library to appString(QuranStrings.library),
            Qibla to appString(QuranStrings.qibla),
            Settings to appString(QuranStrings.settings),
        ).forEach { (destination, title) ->
            NavigationBarItem(
                selected = currentDestination == destination ||
                    (destination == Library && (currentDestination is Reader || currentDestination is Practice || currentDestination is Study)),
                onClick = { onNavigate(destination) },
                icon = { Text(if (destination == Library) "۞" else if (destination == Qibla) "↗" else "◉") },
                label = { Text(title) },
            )
        }
    }
}
