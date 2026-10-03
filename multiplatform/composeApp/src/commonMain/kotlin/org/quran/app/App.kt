package org.quran.app
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.navigation3.runtime.*
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.*
import org.quran.app.model.*
import org.quran.app.domain.*
import org.quran.app.data.*
import org.quran.app.designsystem.*
import org.quran.app.reader.*
import org.quran.app.memorization.*
import org.quran.app.qibla.*
import org.quran.app.tutor.*
@Serializable data object Library : NavKey
@Serializable data class Reader(val surah:Int,val ayah:Int=1):NavKey
@Serializable data class Practice(val surah:Int,val ayah:Int):NavKey
@Serializable data class Study(val surah:Int,val ayah:Int):NavKey
@Serializable data object Qibla:NavKey
@Serializable data object Settings:NavKey
private val navigationConfig = SavedStateConfiguration {
 serializersModule=SerializersModule { polymorphic(NavKey::class) {
 subclass(Library::class,Library.serializer()); subclass(Reader::class,Reader.serializer()); subclass(Practice::class,Practice.serializer()); subclass(Study::class,Study.serializer()); subclass(Qibla::class,Qibla.serializer()); subclass(Settings::class,Settings.serializer())
 } }
}
@Composable fun QuranApp(settings:SettingsStore, audioPlayer:AudioPlayer, importAudio:((String)->Unit)->Unit) {
 val repository=remember { BundledQuranRepository() }
 val progressRepository=remember(settings) { StoredProgressRepository(settings) }
 var progress by remember { mutableStateOf(progressRepository.read()) }
 fun save(next:StudyProgress) { progressRepository.save(next);progress=next }
 val stack=rememberNavBackStack(navigationConfig,Library)
 val language=progress.language
 val compass=remember { platformCompassProvider() }
 QuranTheme { CompositionLocalProvider(LocalLayoutDirection provides if(language.isRtl)LayoutDirection.Rtl else LayoutDirection.Ltr) {
 Scaffold(containerColor=Ivory,topBar={ Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),horizontalArrangement=Arrangement.SpaceBetween) { TextButton({if(stack.size>1)stack.removeAt(stack.lastIndex)}){Text(if(stack.size>1)label(language,"‹ Back","رجوع ›") else label(language,"QURAN","القرآن"))}; Text(label(language,"Read with intention","اقرأ بقلب حاضر"),Modifier.padding(top=14.dp)) } },bottomBar={ NavigationBar(containerColor=Ivory) {
 listOf(Library to label(language,"Read","قراءة"),Qibla to label(language,"Qibla","القبلة"),Settings to label(language,"You","حسابك")).forEach { (key,title) -> NavigationBarItem(selected=stack.last()==key,onClick={stack.clear();stack.add(key)},icon={Text(when(key){Library->"۞";Qibla->"↗";else->"◉"})},label={Text(title)}) }
 } }) { padding -> Box(Modifier.padding(padding).padding(horizontal=20.dp).fillMaxSize()) {
 NavDisplay(backStack=stack,onBack={if(stack.size>1)stack.removeAt(stack.lastIndex)},entryProvider=entryProvider {
 entry<Library> { LibraryScreen(repository.chapters(),progress,{surah,ayah->stack.add(Reader(surah,ayah))}) }
 entry<Reader> { key -> ReaderScreen(repository.chapters()[key.surah-1],repository.verses(key.surah,language),key.ayah,progress,onRead={save(progress.copy(lastRead=it))},onBookmark={id->save(progress.copy(bookmarks=if(id in progress.bookmarks)progress.bookmarks-id else progress.bookmarks+id))},onPractice={stack.add(Practice(it.surah,it.ayah))},onStudy={stack.add(Study(it.surah,it.ayah))}) }
 entry<Practice> { key -> MemorizationScreen(repository.verses(key.surah,language)[key.ayah-1],progress,{save(progress.copy(memorized=progress.memorized+it))},audioPlayer,importAudio) }
 entry<Study> { key -> TutorScreen(repository.verses(key.surah,language)[key.ayah-1],progress) }
 entry<Qibla> { QiblaScreen(language,compass) }
 entry<Settings> { SettingsScreen(progress,{save(it)},{stack.add(Reader(it.surah,it.ayah))}) }
 })
 } }
 } }
}
@Composable private fun SettingsScreen(progress:StudyProgress,onChange:(StudyProgress)->Unit,onOpen:(VerseId)->Unit) {
 val language=progress.language
 val uriHandler=LocalUriHandler.current
 LazyColumn(verticalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(bottom=24.dp)) {
 item { ScreenTitle(label(language,"Your reading space","مساحتك للقراءة"),label(language,"Preferences and saved verses","الإعدادات والآيات المحفوظة")) }
 item { PaperCard { Text(label(language,"Language","اللغة"),style=MaterialTheme.typography.titleLarge); Row { TextButton({onChange(progress.copy(language=AppLanguage.ENGLISH))}){Text("English")};TextButton({onChange(progress.copy(language=AppLanguage.ARABIC))}){Text("العربية")} }; Row { Switch(progress.childMode,{onChange(progress.copy(childMode=it))}); Text(label(language,"Children mode","وضع الأطفال")) }; Text(label(language,"Larger verses, three repetitions to begin, and guided exercises. Progress stays on this device.","آيات أكبر وثلاثة تكرارات كبداية وتمارين موجهة. يبقى تقدمك على هذا الجهاز.")); Text(label(language,"${progress.memorized.size} verses marked memorized","${progress.memorized.size} آية تم تحديدها محفوظة")) } }
 item { Text(label(language,"Saved verses","الآيات المحفوظة"),style=MaterialTheme.typography.titleLarge) }
 if(progress.bookmarks.isEmpty())item { Text(label(language,"Save a verse while reading to find it here.","احفظ آية أثناء القراءة لتجدها هنا.")) }
 items(progress.bookmarks.sortedWith(compareBy({it.surah},{it.ayah}))) { id -> PaperCard { Text("${id.surah}:${id.ayah}"); Action(label(language,"Open verse","فتح الآية"),{onOpen(id)});TextButton({onChange(progress.copy(bookmarks=progress.bookmarks-id))}){Text(label(language,"Remove bookmark","إزالة العلامة"))} } }
 item { Text(label(language,"Memorized verses","الآيات التي حفظتها"),style=MaterialTheme.typography.titleLarge) }
 if(progress.memorized.isEmpty())item { Text(label(language,"Mark a verse memorized after checking your recitation.","حدد الآية محفوظة بعد مراجعة تلاوتك.")) }
 items(progress.memorized.sortedWith(compareBy({it.surah},{it.ayah})),key={"memorized-${it.surah}-${it.ayah}"}) { id -> PaperCard { Text("${id.surah}:${id.ayah}"); Action(label(language,"Review verse","مراجعة الآية"),{onOpen(id)});TextButton({onChange(progress.copy(memorized=progress.memorized-id))}){Text(label(language,"Needs more practice","تحتاج مزيداً من التدريب"))} } }
 item { PaperCard { Text(label(language,"Text source","مصدر النص"),style=MaterialTheme.typography.titleLarge);Text("Tanzil Quran Text (Uthmani 1.1) © 2007–2026 Tanzil Project. CC BY 3.0. Verbatim text; modification prohibited."); TextButton({uriHandler.openUri("https://tanzil.net")}){Text(label(language,"Tanzil source and updates","مصدر تنزيل والتحديثات"))};Text(label(language,"No Quran translation is bundled. English and Arabic are interface languages. AI, cloud sync, and downloadable reciter audio require further integration.","لا توجد ترجمة للقرآن ضمن التطبيق. الإنجليزية والعربية لغتا الواجهة. الذكاء الاصطناعي والمزامنة وتنزيل تلاوات القراء تحتاج ربطاً إضافياً.")) } }
 }
}
