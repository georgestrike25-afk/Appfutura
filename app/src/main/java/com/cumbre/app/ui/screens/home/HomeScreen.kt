package com.cumbre.app.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.cumbre.app.R
import com.cumbre.app.data.db.RouteEntity
import com.cumbre.app.tracking.TrackingState
import com.cumbre.app.tracking.TrackingStatus
import com.cumbre.app.ui.LocalAppContainer
import com.cumbre.app.ui.LocalGpxPicker
import com.cumbre.app.ui.LocalMessenger
import com.cumbre.app.ui.components.CumbreCard
import com.cumbre.app.ui.components.QuickActionTile
import com.cumbre.app.ui.components.RidgeSilhouette
import com.cumbre.app.ui.components.RidgeStyle
import com.cumbre.app.ui.components.SectionHeader
import com.cumbre.app.ui.components.StatusPill
import com.cumbre.app.ui.components.TrackingPillState
import com.cumbre.app.ui.components.TrackThumbnail
import com.cumbre.app.ui.navigation.Dest
import com.cumbre.app.ui.platform.rememberRecordingStarter
import com.cumbre.app.ui.theme.CumbreColors
import com.cumbre.app.ui.theme.CumbreTheme
import com.cumbre.app.ui.theme.DataText
import com.cumbre.app.ui.theme.Spacing
import com.cumbre.core.analysis.AnalysisCodec
import com.cumbre.core.format.Formatters
import com.cumbre.core.geo.PolylineCodec

/** Pantalla de inicio: la ruta para seguir, accesos rápidos y rutas recientes. */
@Composable
fun HomeScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val openPicker = LocalGpxPicker.current
    val messenger = LocalMessenger.current
    val tracking by container.trackingManager.state.collectAsStateWithLifecycle()
    val routes by remember { container.routes.observeRoutes() }.collectAsStateWithLifecycle(initialValue = null)
    val maps by remember { container.maps.observeMaps() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val startRecording = rememberRecordingStarter(nav)
    val activeMaps = maps.count { it.enabled }

    val recent = remember(routes) {
        routes.orEmpty().sortedByDescending { it.lastOpenedAt ?: it.createdAt }
    }
    val hero = recent.firstOrNull()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(bottom = Spacing.xl),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(painterResource(R.drawable.cumbre_logo), contentDescription = null, modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)))
            Text("Cumbre", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(start = 10.dp).weight(1f))
            OfflineChip(hasMaps = activeMaps > 0, onClick = { nav.navigate(Dest.OFFLINE_MAPS) })
        }

        Column(Modifier.padding(horizontal = 18.dp)) {
            when {
                tracking.isActive -> ActiveHero(tracking, onOpen = { nav.navigate(Dest.TRACKING) })
                hero != null -> RouteHero(
                    route = hero,
                    onStart = {
                        if (tracking.isActive) messenger("Ya hay un recorrido en curso.") else startRecording(hero.id)
                    },
                    onOpen = { nav.navigate(Dest.routeDetail(hero.id)) },
                )
                routes != null -> WelcomeHero(onImport = openPicker)
            }

            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionTile(Icons.Rounded.UploadFile, "Importar", "GPX o Wikiloc", onClick = openPicker, modifier = Modifier.weight(1f))
                QuickActionTile(
                    Icons.Rounded.RadioButtonChecked,
                    if (tracking.isActive) "Grabando" else "Grabar",
                    if (tracking.isActive) "Ver actividad" else "Actividad libre",
                    onClick = { if (tracking.isActive) nav.navigate(Dest.TRACKING) else startRecording(null) },
                    modifier = Modifier.weight(1f),
                    iconContainer = if (CumbreTheme.isDark) Color(0xFF4A2522) else Color(0xFFFCE7E5),
                    iconTint = if (CumbreTheme.isDark) Color(0xFFFFB4AB) else Color(0xFFB3261E),
                )
                QuickActionTile(
                    Icons.Rounded.DownloadForOffline,
                    "Mapas",
                    when (activeMaps) { 0 -> "Sin instalar"; 1 -> "1 instalado"; else -> "$activeMaps instalados" },
                    onClick = { nav.navigate(Dest.OFFLINE_MAPS) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        val others = recent.drop(if (tracking.isActive) 0 else 1).take(8)
        if (others.isNotEmpty()) {
            SectionHeader(
                "Rutas recientes",
                modifier = Modifier.padding(horizontal = 18.dp),
                action = "Ver todas",
                onAction = { nav.navigate(Dest.ROUTES) { launchSingleTop = true } },
            )
            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(others, key = { it.id }) { r -> RecentRouteCard(r, onClick = { nav.navigate(Dest.routeDetail(r.id)) }) }
            }
        }
    }
}

@Composable
private fun OfflineChip(hasMaps: Boolean, onClick: () -> Unit) {
    val extra = CumbreTheme.extra
    val bg = if (hasMaps) extra.successContainer else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (hasMaps) extra.onSuccessContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.height(32.dp).clip(CircleShape).background(bg).clickable(onClick = onClick).padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(if (hasMaps) Icons.Rounded.CloudOff else Icons.Rounded.DownloadForOffline, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        Text(if (hasMaps) "Listo sin conexión" else "Sin mapa offline", style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

private val HeroMuted = Color(0xFFB9D3C6)

@Composable
private fun HeroStat(value: String, unit: String?, label: String) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = DataText.Medium.copy(fontSize = 19.sp), color = Color.White, maxLines = 1)
            unit?.let { Text(" $it", style = MaterialTheme.typography.labelMedium, color = HeroMuted, modifier = Modifier.padding(bottom = 2.dp)) }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = HeroMuted)
    }
}

@Composable
private fun HeroButton(text: String, onClick: () -> Unit, modifier: Modifier, accent: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (accent) CumbreColors.Naranja else Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = if (accent) CumbreColors.GrisOscuro else Color.White, modifier = Modifier.size(20.dp).padding(end = 2.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (accent) CumbreColors.GrisOscuro else Color.White, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun HeroFrame(content: @Composable () -> Unit) {
    val bg = if (CumbreTheme.isDark) Color(0xFF16302B) else CumbreColors.VerdeMontana
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(bg)) { Column { content() } }
}

@Composable
private fun RouteHero(route: RouteEntity, onStart: () -> Unit, onOpen: () -> Unit) {
    val profile = remember(route.profile) { AnalysisCodec.decodeProfile(route.profile) }
    HeroFrame {
        Column(Modifier.clickable(onClick = onOpen).padding(start = 18.dp, end = 18.dp, top = 16.dp)) {
            Text(if (route.lastOpenedAt != null) "Seguí donde quedaste" else "Tu última ruta", style = MaterialTheme.typography.labelLarge, color = HeroMuted)
            Text(route.name, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                val (d, du) = Formatters.distanceParts(route.distanceMeters)
                HeroStat(d, du, "Distancia")
                route.elevationGainMeters?.let { HeroStat(Formatters.elevationValue(it), "m", "Desnivel +") }
                route.estimatedDurationSeconds?.let { HeroStat(Formatters.durationCompact(it), null, "Estimado") }
            }
        }
        if (profile.size >= 2) {
            RidgeSilhouette(profile, Modifier.fillMaxWidth().height(104.dp).padding(top = 6.dp), style = RidgeStyle.HERO)
        } else {
            Box(Modifier.height(16.dp))
        }
        Row(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroButton("Iniciar", onStart, Modifier.weight(1.3f), accent = true, icon = Icons.Rounded.Navigation)
            HeroButton("Ver ruta", onOpen, Modifier.weight(1f), accent = false)
        }
    }
}

@Composable
private fun ActiveHero(state: TrackingState, onOpen: () -> Unit) {
    val profile = state.route?.profile.orEmpty()
    val follow = state.follow
    HeroFrame {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp)) {
            StatusPill(if (state.status == TrackingStatus.PAUSED) TrackingPillState.PAUSED else TrackingPillState.RECORDING)
            Text(state.activityName, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                val (d, du) = Formatters.distanceParts(state.stats.distanceMeters)
                HeroStat(d, du, "Recorrido")
                HeroStat(Formatters.clock(state.elapsedMillis), null, "Tiempo")
                follow?.let {
                    val (r, ru) = Formatters.distanceParts(it.remainingMeters)
                    HeroStat(r, ru, "Te falta")
                }
            }
        }
        if (profile.size >= 2 && follow != null) {
            val total = profile.last().distanceMeters
            val fraction = if (total > 0) ((total - follow.remainingMeters) / total).toFloat() else 0f
            RidgeSilhouette(profile, Modifier.fillMaxWidth().height(96.dp).padding(top = 6.dp), style = RidgeStyle.HERO, progressFraction = fraction)
        } else {
            Box(Modifier.height(14.dp))
        }
        Row(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp, top = 6.dp)) {
            HeroButton("Volver a la navegación", onOpen, Modifier.fillMaxWidth(), accent = true, icon = Icons.AutoMirrored.Rounded.DirectionsWalk)
        }
    }
}

@Composable
private fun WelcomeHero(onImport: () -> Unit) {
    HeroFrame {
        Column(Modifier.padding(18.dp)) {
            Text("Tu camino, en tus manos.", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(
                "Importá un GPX (de Wikiloc u otra app) para seguirlo con GPS, ver su perfil y saber cuánto te falta. Todo funciona sin conexión.",
                style = MaterialTheme.typography.bodyMedium,
                color = HeroMuted,
                modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
            )
            HeroButton("Importar mi primera ruta", onImport, Modifier.fillMaxWidth(), accent = true, icon = Icons.Rounded.UploadFile)
        }
    }
}

@Composable
private fun RecentRouteCard(route: RouteEntity, onClick: () -> Unit) {
    val profile = remember(route.profile) { AnalysisCodec.decodeProfile(route.profile) }
    val points = remember(route.thumbnail) { PolylineCodec.decode(route.thumbnail) }
    CumbreCard(Modifier.width(200.dp), onClick = onClick) {
        if (profile.size >= 2) {
            Box(Modifier.fillMaxWidth().height(100.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                RidgeSilhouette(profile, Modifier.fillMaxSize().padding(top = 18.dp), style = RidgeStyle.MINI)
            }
        } else {
            TrackThumbnail(points, Modifier.fillMaxWidth().height(100.dp))
        }
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp)) {
            Text(route.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(Formatters.distance(route.distanceMeters), style = DataText.Small, maxLines = 1)
                route.elevationGainMeters?.let { Text("↑ ${Formatters.elevation(it)}", style = DataText.Small, color = MaterialTheme.colorScheme.primary, maxLines = 1) }
            }
        }
    }
}
