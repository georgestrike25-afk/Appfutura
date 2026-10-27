package com.cumbre.app.ui.theme

import androidx.compose.ui.graphics.Color

/** Paleta oficial de Cumbre. */
object CumbreColors {
    val VerdeMontana = Color(0xFF0F403A)
    val VerdeBosque = Color(0xFF267D5B)
    val VerdeSuave = Color(0xFFA7C4B2)
    val Naranja = Color(0xFFFF8A00)
    val GrisOscuro = Color(0xFF1E1E1E)
    val GrisClaro = Color(0xFFF5F7F6)
    val Blanco = Color(0xFFFFFFFF)

    /** Línea del track: magenta, distinta del naranja de la posición y del verde del terreno. */
    val Track = Color(0xFFD6247F)
    val TrackHaloLight = Color(0xFFFFFFFF)
    val TrackHaloDark = Color(0xFF000000)
    /** Recorrido ya grabado durante la navegación. */
    val Recorded = Color(0xFF2F7DE1)

    // Colores semánticos (no se reutilizan los de marca para estados)
    val Error = Color(0xFFC62828)
    val ErrorContainer = Color(0xFFFDECEA)
    val OnErrorContainer = Color(0xFF7A1F1A)
    val Warning = Color(0xFFB26A00)
    val WarningContainer = Color(0xFFFFF3D6)
    val OnWarningContainer = Color(0xFF5C3D00)
    val Success = Color(0xFF2E7D32)
    val SuccessContainer = Color(0xFFE1EFE6)
    val OnSuccessContainer = Color(0xFF1E6B4B)

    // Superficies del modo oscuro
    val DarkBackground = Color(0xFF0F1716)
    val DarkSurface = Color(0xFF16201E)
    val DarkSurfaceHigh = Color(0xFF1D2826)
    val DarkOutline = Color(0xFF3A4744)
    val DarkPrimaryContainer = Color(0xFF24493F)
    val DarkOnSurface = Color(0xFFE9F2EE)
    val DarkOnSurfaceVariant = Color(0xFF9DAAA5)
}
