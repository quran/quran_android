package org.quran.app.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics

/** One labelled switch action: the entire row is the accessible touch target. */
@Composable
fun QuranSettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier.fillMaxWidth().sizeIn(minHeight = QuranSpacing.TouchTarget)
            .semantics(mergeDescendants = true) {}
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = QuranSpacing.Small),
        horizontalArrangement = Arrangement.spacedBy(QuranSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(QuranSpacing.ExtraSmall)) {
            QuranText(title, variant = QuranTextVariant.Title)
            QuranText(description, variant = QuranTextVariant.Supporting)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
