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
    var resultError by remember { mutableStateOf<org.jetbrains.compose.resources.StringResource?>(null) }
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
        ScreenTitle(appString(QuranStrings.faceQibla), appString(QuranStrings.trueNorthBearing))
        PaperCard {
            Text(appString(QuranStrings.manualCoordinatesHelp))
            QuranTextField(latitude, { stopCompass(); bearing = null; resultError = null; latitude = it }, label = appString(QuranStrings.latitude))
            QuranTextField(longitude, { stopCompass(); bearing = null; resultError = null; longitude = it }, label = appString(QuranStrings.longitude))
            Action(appString(QuranStrings.calculateDirection), {
                stopCompass()
                resultError = null
                runCatching {
                    bearing = QiblaCalculator.bearing(latitude.toDouble(), longitude.toDouble())
                    if (bearing == null) resultError = QuranStrings.undefinedDirection
                }.onFailure {
                    bearing = null
                    resultError = QuranStrings.invalidCoordinates
                }
            })
            resultError?.let { Text(appString(it), style = MaterialTheme.typography.headlineSmall) }
            bearing?.let { value ->
                Text(
                    appString(QuranStrings.bearingResult, (value * 10).toInt() / 10.0),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }
        if (bearing != null) {
            PaperCard {
                Text(appString(QuranStrings.liveCompass), style = MaterialTheme.typography.titleLarge)
                Text(appString(QuranStrings.compassCalibrationHelp))
                Action(appString(if (live) QuranStrings.stopCompass else QuranStrings.startCompass), {
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
                    Text(appString(if (turn < 0) QuranStrings.turnLeft else QuranStrings.turnRight, kotlin.math.abs(turn).toInt()))
                } else if (live) {
                    Text(appString(if (unavailable) QuranStrings.headingUnavailable else QuranStrings.headingWaiting))
                }
            }
        }
        Text(appString(QuranStrings.confirmDirectionHelp))
    }
}
