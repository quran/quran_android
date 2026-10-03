@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package org.quran.app
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.*
import platform.Foundation.NSURL
import platform.darwin.NSObject
import org.quran.app.data.*
private class RecordingPicker(private val onSelected:(String)->Unit):NSObject(),UIDocumentPickerDelegateProtocol {
 override fun documentPicker(controller:UIDocumentPickerViewController,didPickDocumentsAtURLs:List<*>) { (didPickDocumentsAtURLs.firstOrNull() as? NSURL)?.absoluteString?.let(onSelected) }
}
fun MainViewController():UIViewController {
 val settings=platformSettingsStore();val player=platformAudioPlayer()
 lateinit var host:UIViewController
 var pickerDelegate:RecordingPicker?=null
 host=ComposeUIViewController {
 DisposableEffect(player) { onDispose { player.release() } }
 QuranApp(settings,player) { callback ->
 pickerDelegate=RecordingPicker(callback)
 val picker=UIDocumentPickerViewController(documentTypes=listOf("public.audio"),inMode=UIDocumentPickerMode.UIDocumentPickerModeImport)
 picker.delegate=pickerDelegate
 host.presentViewController(picker,animated=true,completion=null)
 }
 }
 return host
}
