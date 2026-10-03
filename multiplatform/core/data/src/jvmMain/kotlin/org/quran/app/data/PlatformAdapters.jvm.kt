package org.quran.app.data
import org.quran.app.domain.*
actual fun platformSettingsStore():SettingsStore=object:SettingsStore { private val values=mutableMapOf<String,String>();override fun get(key:String)=values[key];override fun set(key:String,value:String){values[key]=value} }
internal actual fun platformTranslationHttpClient() = io.ktor.client.HttpClient(io.ktor.client.engine.cio.CIO) {
 expectSuccess = true
 install(io.ktor.client.plugins.HttpTimeout) {
  requestTimeoutMillis = 15_000
  connectTimeoutMillis = 10_000
  socketTimeoutMillis = 15_000
 }
}
actual fun platformAudioPlayer():AudioPlayer=object:AudioPlayer { override fun loadLocal(uri:String){error("Native audio requires Android or iOS")};override fun play(onCompleted:()->Unit,onError:(String)->Unit){onError("Native audio requires Android or iOS")};override fun pause(){};override fun release(){} }
