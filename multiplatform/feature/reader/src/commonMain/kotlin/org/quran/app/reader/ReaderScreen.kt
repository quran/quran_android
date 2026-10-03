package org.quran.app.reader
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.quran.app.model.*
import org.quran.app.designsystem.*
@Composable fun LibraryScreen(chapters:List<Chapter>, progress:StudyProgress, onOpen:(Int,Int)->Unit) {
 var query by remember { mutableStateOf("") }
 LazyColumn(Modifier.fillMaxSize(), verticalArrangement=Arrangement.spacedBy(12.dp), contentPadding=PaddingValues(bottom=24.dp)) {
 item { ScreenTitle(label(progress.language,"Your daily companion","رفيقك اليومي"),label(progress.language,"Read, reflect, and remember","اقرأ وتدبر واحفظ")) }
 item { PaperCard { Text(label(progress.language,"Continue reading","متابعة القراءة"),style=MaterialTheme.typography.titleLarge); Text("${progress.lastRead.surah}:${progress.lastRead.ayah}"); Action(label(progress.language,"Open verse","فتح الآية"),{onOpen(progress.lastRead.surah,progress.lastRead.ayah)}) } }
 item { OutlinedTextField(query,{query=it},label={Text(label(progress.language,"Find a surah","ابحث عن سورة"))},modifier=Modifier.fillMaxWidth()) }
 items(chapters.filter { it.englishName.contains(query,true)||it.arabicName.contains(query)||it.number.toString()==query },key={it.number}) { chapter -> PaperCard { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("${chapter.number}",style=MaterialTheme.typography.titleLarge); Text(chapter.arabicName,style=MaterialTheme.typography.titleLarge) }; Text(chapter.englishName); Text(label(progress.language,"${chapter.verseCount} verses · Offline","${chapter.verseCount} آية · دون إنترنت")); Action(label(progress.language,"Read","قراءة"),{onOpen(chapter.number,1)}) } }
 }
}
@Composable fun ReaderScreen(chapter:Chapter, verses:List<Verse>, initialAyah:Int, progress:StudyProgress, onRead:(VerseId)->Unit, onBookmark:(VerseId)->Unit, onPractice:(VerseId)->Unit, onStudy:(VerseId)->Unit) {
 val list = rememberLazyListState(initialFirstVisibleItemIndex=initialAyah.coerceAtLeast(1))
 LazyColumn(state=list,modifier=Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(bottom=24.dp)) {
 item { ScreenTitle(chapter.arabicName,chapter.englishName); Text(label(progress.language,"Tanzil Uthmani 1.1 · Arabic text available offline. English translation is not bundled yet.","نص تنزيل العثماني ١٫١ · متاح دون إنترنت. الترجمة الإنجليزية غير متوفرة حالياً.")) }
 items(verses,key={it.id.ayah}) { verse -> PaperCard { Text("${verse.id.surah}:${verse.id.ayah}"); ArabicVerse(verse.arabic,progress.childMode); if(verse.translation.isNotEmpty()) Text(verse.translation); Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { TextButton({onRead(verse.id)}) { Text(label(progress.language,"Read here","موضع القراءة")) }; TextButton({onBookmark(verse.id)}) { Text(label(progress.language,if(verse.id in progress.bookmarks)"Saved" else "Save",if(verse.id in progress.bookmarks)"محفوظة" else "حفظ")) } }; Row { TextButton({onPractice(verse.id)}) { Text(label(progress.language,"Practice","تدرب")) }; TextButton({onStudy(verse.id)}) { Text(label(progress.language,"Study","تعلم")) } } } }
 }
}
