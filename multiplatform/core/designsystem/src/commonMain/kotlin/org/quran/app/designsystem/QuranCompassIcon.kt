package org.quran.app.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Decorative navigation vector; the parent navigation item supplies its localized label. */
val QuranCompassIcon: ImageVector by lazy {
    ImageVector.Builder(name = "QuranCompassIcon", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(21f, 12f)
            curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
            curveTo(7.03f, 21f, 3f, 16.97f, 3f, 12f)
            curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f)
            curveTo(16.97f, 3f, 21f, 7.03f, 21f, 12f)
            close()
            moveTo(16f, 8f); lineTo(13.5f, 13.5f); lineTo(8f, 16f); lineTo(10.5f, 10.5f); close()
            moveTo(10.5f, 10.5f); lineTo(13.5f, 13.5f)
        }
    }.build()
}
