package com.cumbre.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cumbre.app.R

/** Montserrat para títulos; Inter para textos, datos y controles (ambas con licencia SIL OFL). */
val Montserrat = FontFamily(
    Font(R.font.montserrat_semibold, FontWeight.SemiBold),
    Font(R.font.montserrat_bold, FontWeight.Bold),
    Font(R.font.montserrat_extrabold, FontWeight.ExtraBold),
)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

val CumbreTypography = Typography(
    displaySmall = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineLarge = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 27.sp),
    titleLarge = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 21.sp),
    titleSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp),
)

/** Estilos para cifras (estadísticas y panel de navegación). */
object DataText {
    val Hero = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp)
    val Large = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 28.sp)
    val Medium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp)
    val Small = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 17.sp)
}
