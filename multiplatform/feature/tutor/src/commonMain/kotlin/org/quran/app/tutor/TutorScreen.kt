package org.quran.app.tutor
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.quran.app.model.*
import org.quran.app.designsystem.*
@Composable fun TutorScreen(verse:Verse, progress:StudyProgress) {
 var step by remember { mutableStateOf(0) }
 val steps = listOf(label(progress.language,"Read the verse slowly while following each word.","اقرأ الآية ببطء وتابع كل كلمة."), label(progress.language,"Recite the verse again. Notice where you pause and where you need practice.","كرر الآية وانتبه لمواضع الوقف والكلمات التي تحتاج تدريباً."),label(progress.language,"Open Practice and check your recall against the verified Arabic text.","افتح التدريب وراجع حفظك مع النص العربي الموثق."))
 Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) { ScreenTitle(label(progress.language,"Guided study","التعلم الموجّه"),"${verse.id.surah}:${verse.id.ayah}"); PaperCard { ArabicVerse(verse.arabic,progress.childMode); Text(verse.source) }; PaperCard { Text(steps[step]); Action(label(progress.language,if(step==steps.lastIndex)"Restart guidance" else "Next step",if(step==steps.lastIndex)"إعادة الخطوات" else "الخطوة التالية"),{step=(step+1)%steps.size}) }; PaperCard { Text(label(progress.language,"AI learning assistant","مساعد التعلم الذكي"),style=MaterialTheme.typography.titleLarge); Text(label(progress.language,if(progress.childMode)"Children mode uses local guided exercises. AI conversations are disabled." else "AI is not connected. A reviewed, source-grounded backend is required before explanations or recitation assessment can be offered.",if(progress.childMode)"وضع الأطفال يستخدم تمارين محلية موجهة. محادثات الذكاء الاصطناعي معطلة." else "الذكاء الاصطناعي غير متصل. يلزم خادم موثق ومراجع قبل تقديم الشروح أو تقييم التلاوة.")) } }
}
