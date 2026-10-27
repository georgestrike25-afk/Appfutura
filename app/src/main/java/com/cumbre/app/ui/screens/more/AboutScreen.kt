package com.cumbre.app.ui.screens.more

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.cumbre.app.BuildConfig
import com.cumbre.app.R
import com.cumbre.app.ui.components.CumbreCard
import com.cumbre.app.ui.components.CumbreTopBar
import com.cumbre.app.ui.components.SectionTitle
import com.cumbre.app.ui.theme.Spacing

private val licenses = listOf(
    "Mapsforge" to "Motor de mapas vectoriales offline. GNU LGPL 3.0.",
    "Datos de OpenStreetMap" to "Los mapas .map se generan con datos © colaboradores de OpenStreetMap, disponibles bajo la licencia ODbL.",
    "AndroidSVG" to "Renderizado de símbolos de los temas de mapa. Apache 2.0.",
    "Jetpack (Compose, Room, DataStore, Navigation, Lifecycle)" to "Apache 2.0.",
    "Material Symbols" to "Iconografía. Apache 2.0.",
    "Kotlin y kotlinx.coroutines" to "Apache 2.0.",
    "Montserrat e Inter" to "Tipografías bajo SIL Open Font License 1.1.",
)

@Composable
fun AboutScreen(nav: NavController) {
    Column(Modifier.fillMaxSize()) {
        CumbreTopBar(title = "Acerca de Cumbre", onBack = { nav.popBackStack() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(Spacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                Image(painterResource(R.drawable.cumbre_logo), contentDescription = null, modifier = Modifier.size(64.dp))
                Column {
                    Text("Cumbre", style = MaterialTheme.typography.headlineMedium)
                    Text("Tu camino, en tus manos.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Versión ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SectionTitle("Sobre los datos")
            Text(
                "Las distancias se calculan sobre el elipsoide terrestre a partir de los puntos del GPX. El desnivel usa un filtro de 5 m para elevaciones de archivo y de 10 m para altitud GPS en vivo. " +
                    "La duración estimada sigue la norma DIN 33466 (4 km/h, 300 m/h de subida y 500 m/h de bajada) y no incluye descansos.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Cumbre no genera indicaciones de giro: muestra el track original, tu posición, la distancia a la línea y los waypoints del archivo. Usala con criterio y llevá siempre un respaldo.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.s),
            )
            SectionTitle("Licencias y atribuciones")
            CumbreCard {
                Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    licenses.forEach { (name, text) ->
                        Column {
                            Text(name, style = MaterialTheme.typography.titleSmall)
                            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Text(
                "Wikiloc es una marca de sus respectivos titulares. Cumbre no está afiliada a Wikiloc.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.l),
            )
        }
    }
}
