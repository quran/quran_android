package org.quran.app.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Decorative navigation vector; the parent navigation item supplies its localized label. */
val QuranSettingsIcon: ImageVector by lazy {
    ImageVector.Builder(name = "QuranSettingsIcon", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(10f, 3f); lineTo(14f, 3f); lineTo(14.7f, 5.6f)
            lineTo(16f, 6.4f); lineTo(18.6f, 5.7f); lineTo(20.6f, 9.1f)
            lineTo(18.7f, 11f); verticalLineTo(13f); lineTo(20.6f, 14.9f)
            lineTo(18.6f, 18.3f); lineTo(16f, 17.6f); lineTo(14.7f, 18.4f)
            lineTo(14f, 21f); lineTo(10f, 21f); lineTo(9.3f, 18.4f)
            lineTo(8f, 17.6f); lineTo(5.4f, 18.3f); lineTo(3.4f, 14.9f)
            lineTo(5.3f, 13f); verticalLineTo(11f); lineTo(3.4f, 9.1f)
            lineTo(5.4f, 5.7f); lineTo(8f, 6.4f); lineTo(9.3f, 5.6f); close()
            moveTo(15f, 12f)
            curveTo(15f, 13.66f, 13.66f, 15f, 12f, 15f)
            curveTo(10.34f, 15f, 9f, 13.66f, 9f, 12f)
            curveTo(9f, 10.34f, 10.34f, 9f, 12f, 9f)
            curveTo(13.66f, 9f, 15f, 10.34f, 15f, 12f); close()
        }
    }.build()
}
