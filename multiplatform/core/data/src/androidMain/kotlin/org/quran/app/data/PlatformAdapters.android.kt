package org.quran.app.data
import android.content.Context
import org.quran.app.domain.*
private lateinit var appContext:Context
internal fun platformContext(): Context = appContext
fun initializePlatform(context:Context) { appContext=context.applicationContext }
actual fun platformSettingsStore():SettingsStore = object:SettingsStore {
 private val preferences=appContext.getSharedPreferences("quran.study",Context.MODE_PRIVATE)
 override fun get(key:String):String?=preferences.getString(key,null)
 override fun set(key:String,value:String) { check(preferences.edit().putString(key,value).commit()) { "Could not save study progress" } }
}
actual fun platformAudioPlayer():AudioPlayer = AndroidAudioPlayer(appContext)
