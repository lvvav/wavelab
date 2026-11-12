package app.soundlife

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Цвета те же, что на сайте
val Bg = Color(0xFF0D0D11)
val Panel = Color(0xFF121217)
val Hover = Color(0xFF18181F)
val Line = Color(0xFF23232C)
val TextC = Color(0xFFECECF1)
val Mut = Color(0xFF8B8B99)
val Accent = Color(0xFFC6FF3D)
val OnAccent = Color(0xFF11160A)

@Composable
fun SoundLifeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = OnAccent,
            background = Bg,
            onBackground = TextC,
            surface = Panel,
            onSurface = TextC,
            surfaceVariant = Hover,
            onSurfaceVariant = Mut,
            outline = Line,
        ),
        content = content,
    )
}
