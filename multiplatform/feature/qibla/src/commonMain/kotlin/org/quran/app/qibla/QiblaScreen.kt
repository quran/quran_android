package org.quran.app.qibla

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import org.quran.app.designsystem.*
import org.quran.app.domain.CompassProvider
import org.quran.app.domain.QiblaCalculator
import org.quran.app.model.AppLanguage

@Composable
fun QiblaScreen(language: AppLanguage, compass: CompassProvider) {
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var bearing by remember { mutableStateOf<Double?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var heading by remember { mutableStateOf<Double?>(null) }
    var live by remember { mutableStateOf(false) }
    var unavailable by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    fun stopCompass() {
        compass.stop()
        live = false
        heading = null
        unavailable = false
    }

    DisposableEffect(compass, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) stopCompass()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            compass.stop()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ScreenTitle(label(language, "Face the Qibla", "اتجاه القبلة"), label(language, "Bearing from true north", "زاوية من الشمال الحقيقي"))
        PaperCard {
            Text(label(language, "Enter your current coordinates. Manual direction works without location permission.", "أدخل إحداثيات موقعك الحالي. حساب الاتجاه يدوياً لا يحتاج إذن الموقع."))
            OutlinedTextField(latitude, { stopCompass(); bearing = null; result = null; latitude = it }, label = { Text(label(language, "Latitude (−90 to 90)", "خط العرض (−٩٠ إلى ٩٠)")) })
            OutlinedTextField(longitude, { stopCompass(); bearing = null; result = null; longitude = it }, label = { Text(label(language, "Longitude (−180 to 180)", "خط الطول (−١٨٠ إلى ١٨٠)")) })
            Action(label(language, "Calculate direction", "حساب الاتجاه"), {
                stopCompass()
                result = runCatching {
                    bearing = QiblaCalculator.bearing(latitude.toDouble(), longitude.toDouble())
                    if (bearing == null) label(language, "Direction is undefined at this location.", "الاتجاه غير محدد في هذا الموقع.")
                    else "${(bearing!! * 10).toInt() / 10.0}° " + label(language, "clockwise from true north", "مع عقارب الساعة من الشمال الحقيقي")
                }.getOrElse {
                    bearing = null
                    label(language, "Enter valid decimal coordinates.", "أدخل إحداثيات عشرية صحيحة.")
                }
            })
            result?.let { Text(it, style = MaterialTheme.typography.headlineSmall) }
        }
        if (bearing != null) {
            PaperCard {
                Text(label(language, "Live compass", "البوصلة الحية"), style = MaterialTheme.typography.titleLarge)
                Text(label(language, "Keep your phone flat in portrait, away from metal. Move it in a figure eight to calibrate. On iOS, true north requires optional location access.", "أبق الهاتف مسطحاً بالطول وبعيداً عن المعادن. حركه على شكل ثمانية للمعايرة. يحتاج الشمال الحقيقي على iOS إذن موقع اختياري."))
                Action(label(language, if (live) "Stop compass" else "Start compass", if (live) "إيقاف البوصلة" else "تشغيل البوصلة"), {
                    if (live) stopCompass() else {
                        live = true
                        unavailable = false
                        compass.start(latitude.toDouble(), longitude.toDouble(),
                            onHeading = { heading = it; unavailable = false },
                            onUnavailable = { heading = null; unavailable = true },
                        )
                    }
                })
                val currentHeading = heading
                if (live && currentHeading != null) {
                    val turn = ((bearing!! - currentHeading + 540.0) % 360.0) - 180.0
                    Text("↑", Modifier.rotate(turn.toFloat()).padding(24.dp), style = MaterialTheme.typography.displayLarge)
                    Text(label(language, "Turn ${kotlin.math.abs(turn).toInt()}° ${if (turn < 0) "left" else "right"}", "اتجه ${kotlin.math.abs(turn).toInt()}° ${if (turn < 0) "يساراً" else "يميناً"}"))
                } else if (live) {
                    Text(label(language, if (unavailable) "Heading unavailable or uncalibrated. Use the manual bearing above." else "Waiting for a reliable true north reading…", if (unavailable) "الاتجاه غير متاح أو البوصلة تحتاج معايرة. استخدم الزاوية اليدوية أعلاه." else "بانتظار قراءة موثوقة للشمال الحقيقي…"))
                }
            }
        }
        Text(label(language, "Confirm direction with a map or calibrated compass when readings fluctuate.", "تحقق من الاتجاه بخريطة أو بوصلة معايرة عندما تتذبذب القراءات."))
    }
}
