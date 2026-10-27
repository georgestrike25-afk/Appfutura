package com.cumbre.app.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.cumbre.app.ui.LocalAppContainer
import com.cumbre.app.ui.LocalGpxPicker
import com.cumbre.app.ui.LocalSettings
import com.cumbre.app.ui.components.CumbreCard
import com.cumbre.app.ui.components.CumbreTopBar
import com.cumbre.app.ui.components.MessageBanner
import com.cumbre.app.ui.components.MessageKind
import com.cumbre.app.ui.components.PrimaryButton
import com.cumbre.app.ui.components.QuietButton
import com.cumbre.app.ui.components.SecondaryButton
import com.cumbre.app.ui.components.SectionTitle
import com.cumbre.app.ui.platform.openUrl
import com.cumbre.app.ui.theme.Spacing
import com.cumbre.core.library.WikilocLinks
import kotlinx.coroutines.launch

/**
 * Guía para traer rutas de Wikiloc. Cumbre no consulta Wikiloc ni descarga contenido por su
 * cuenta: abre la web oficial en el navegador y recibe el archivo GPX que el usuario descarga.
 */
@Composable
fun WikilocScreen(nav: NavController, sharedUrl: String?) {
    val container = LocalAppContainer.current
    val settings = LocalSettings.current
    val context = LocalContext.current
    val openPicker = LocalGpxPicker.current
    val scope = rememberCoroutineScope()
    val link = sharedUrl?.takeIf { WikilocLinks.isWikilocUrl(it) }

    LaunchedEffect(link) {
        // El enlace recibido se asociará automáticamente a la próxima importación.
        if (link != null) container.settings.setPendingWikilocUrl(link)
    }

    Column(Modifier.fillMaxSize()) {
        CumbreTopBar(title = "Rutas de Wikiloc", onBack = { nav.popBackStack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.l)) {
            val pending = link ?: settings.pendingWikilocUrl
            if (pending != null) {
                MessageBanner(
                    title = "Enlace recibido",
                    text = "Abrí la ruta en Wikiloc y descargá el archivo GPX. Cuando lo importes, Cumbre le asociará este enlace:\n$pending",
                    kind = MessageKind.INFO,
                )
                Spacer(Modifier.height(Spacing.m))
                PrimaryButton("Abrir esta ruta en Wikiloc", onClick = { context.openUrl(pending) }, icon = Icons.AutoMirrored.Rounded.OpenInNew, modifier = Modifier.fillMaxWidth())
                QuietButton("Olvidar este enlace", onClick = { scope.launch { container.settings.setPendingWikilocUrl(null) } })
                Spacer(Modifier.height(Spacing.m))
            }
            Text(
                "Wikiloc es un sitio donde la comunidad comparte rutas. Cumbre no tiene acceso a su contenido: vos descargás el GPX y Cumbre lo prepara para usarlo sin conexión.",
                style = MaterialTheme.typography.bodyMedium,
            )
            SectionTitle("Cómo traer una ruta")
            CumbreCard {
                Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                    Step(1, "Buscá la ruta", "En la web o la app de Wikiloc, encontrá el recorrido que te interesa.")
                    Step(2, "Descargá el GPX", "Usá la opción «Descargar» y elegí el formato GPX. Wikiloc puede pedirte iniciar sesión o tener una suscripción para descargar; eso depende de sus condiciones.")
                    Step(3, "Abrilo con Cumbre", "Tocá el archivo descargado y elegí «Abrir con Cumbre», o compartilo con Cumbre. También podés importarlo desde acá.")
                    Step(4, "Prepará la ruta", "Revisá distancia, desnivel y avisos de calidad, y guardala. Antes de salir, comprobá que tengas el mapa offline de la zona.")
                }
            }
            Spacer(Modifier.height(Spacing.l))
            SecondaryButton("Abrir Wikiloc en el navegador", onClick = { context.openUrl(WikilocLinks.HOME_URL) }, icon = Icons.AutoMirrored.Rounded.OpenInNew, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Spacing.s))
            PrimaryButton("Importar GPX descargado", onClick = openPicker, icon = Icons.Rounded.UploadFile, modifier = Modifier.fillMaxWidth())
            Text(
                "Respetá la licencia y las condiciones de uso de cada ruta y de Wikiloc. Cumbre guarda el enlace de origen para que puedas volver a la ficha original.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.l),
            )
        }
    }
}

@Composable
private fun Step(n: Int, title: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary),
            contentAlignment = Alignment.Center,
        ) { Text("$n", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondary) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
