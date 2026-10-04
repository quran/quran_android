package org.quran.app.data
import org.quran.app.domain.*
actual fun platformSettingsStore():SettingsStore=object:SettingsStore { private val values=mutableMapOf<String,String>();override fun get(key:String)=values[key];override fun set(key:String,value:String){values[key]=value} }

actual fun platformAudioPlayer():AudioPlayer=object:AudioPlayer { override fun loadLocal(uri:String){error("Native audio requires Android or iOS")};override fun play(onCompleted:()->Unit,onError:(String)->Unit,onPlaybackChanged:(Boolean)->Unit){onError("Native audio requires Android or iOS")};override fun pause(){};override fun clearLocal(){};override fun release(){} }
