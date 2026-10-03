package org.quran.app.designsystem
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.quran.app.model.AppLanguage
val Forest = Color(0xFF173E2F)
val Ivory = Color(0xFFF6F2E9)
val Brass = Color(0xFFC39B4B)
@Composable fun QuranTheme(content: @Composable () -> Unit) {
 MaterialTheme(colorScheme = lightColorScheme(primary=Forest, background=Ivory, surface=Ivory, secondary=Color(0xFF73561D), onPrimary=Color.White, onBackground=Forest, onSurface=Forest), content=content)
}
fun label(language: AppLanguage, english: String, arabic: String) = if(language==AppLanguage.ARABIC) arabic else english
@Composable fun ScreenTitle(title: String, subtitle: String) { Column(Modifier.padding(vertical=16.dp)) { Text(title, style=MaterialTheme.typography.headlineLarge); Text(subtitle, style=MaterialTheme.typography.bodyMedium) } }
@Composable fun PaperCard(content: @Composable ColumnScope.() -> Unit) { Card(Modifier.fillMaxWidth(), colors=CardDefaults.cardColors(containerColor=Color.White.copy(alpha=.65f))) { Column(Modifier.padding(18.dp), verticalArrangement=Arrangement.spacedBy(12.dp), content=content) } }
@Composable fun Action(text: String, onClick: () -> Unit, enabled: Boolean = true) { Button(onClick, enabled=enabled, modifier=Modifier.heightIn(min=48.dp)) { Text(text) } }
@Composable fun ArabicVerse(text: String, large: Boolean=false) { Text(text, modifier=Modifier.fillMaxWidth(), fontSize=if(large) 34.sp else 28.sp, lineHeight=if(large) 56.sp else 48.sp, textAlign=androidx.compose.ui.text.style.TextAlign.Right, style=androidx.compose.ui.text.TextStyle(textDirection=androidx.compose.ui.text.style.TextDirection.Rtl)) }
