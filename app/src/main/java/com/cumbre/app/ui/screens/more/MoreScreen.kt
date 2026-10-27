package com.cumbre.app.ui.screens.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.cumbre.app.BuildConfig
import com.cumbre.app.ui.components.CumbreCard
import com.cumbre.app.ui.components.CumbreTopBar
import com.cumbre.app.ui.components.ListRow
import com.cumbre.app.ui.navigation.Dest
import com.cumbre.app.ui.theme.Spacing

@Composable
fun MoreScreen(nav: NavController) {
    Column(Modifier.fillMaxSize()) {
        CumbreTopBar(title = "Más")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(Spacing.l)) {
            CumbreCard {
                ListRow("Mapas sin conexión", subtitle = "Instalá y gestioná mapas .map de tu zona", icon = Icons.Rounded.DownloadForOffline, onClick = { nav.navigate(Dest.OFFLINE_MAPS) })
                ListRow("Rutas de Wikiloc", subtitle = "Cómo traer una ruta a Cumbre", icon = Icons.Rounded.Landscape, onClick = { nav.navigate(Dest.wikiloc()) })
            }
            Text("", Modifier.padding(top = Spacing.s))
            CumbreCard {
                ListRow("Ajustes", subtitle = "Tema, mapa, avisos de desvío y permisos", icon = Icons.Rounded.Settings, onClick = { nav.navigate(Dest.SETTINGS) })
                ListRow("Privacidad y datos", subtitle = "Exportá o eliminá tus datos", icon = Icons.Rounded.PrivacyTip, onClick = { nav.navigate(Dest.PRIVACY) })
                ListRow("Acerca de Cumbre", subtitle = "Versión ${BuildConfig.VERSION_NAME} · licencias y atribuciones", icon = Icons.Rounded.Info, onClick = { nav.navigate(Dest.ABOUT) })
            }
            Text(
                "Cumbre · Tu camino, en tus manos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xl),
            )
        }
    }
}
