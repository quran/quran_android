package org.quran.app.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Decorative navigation vector; the parent navigation item supplies its localized label. */
val QuranBookIcon: ImageVector by lazy {
    ImageVector.Builder(name = "QuranBookIcon", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(12f, 5f)
            curveTo(9f, 3.5f, 5.5f, 3.5f, 2.5f, 5f)
            verticalLineTo(19f)
            curveTo(5.5f, 17.5f, 9f, 17.5f, 12f, 19f)
            curveTo(15f, 17.5f, 18.5f, 17.5f, 21.5f, 19f)
            verticalLineTo(5f)
            curveTo(18.5f, 3.5f, 15f, 3.5f, 12f, 5f)
            verticalLineTo(19f)
            moveTo(5.5f, 8f); curveTo(7f, 7.7f, 8.2f, 7.9f, 9f, 8.2f)
            moveTo(15f, 8.2f); curveTo(15.8f, 7.9f, 17f, 7.7f, 18.5f, 8f)
        }
    }.build()
}
