package com.cumbre.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cumbre.app.AppContainer
import com.cumbre.app.Incoming
import com.cumbre.app.data.Settings
import com.cumbre.app.ui.components.ConfirmDialog
import com.cumbre.app.ui.components.LocalMapControllerStore
import com.cumbre.app.ui.components.MapControllerStore
import com.cumbre.app.ui.navigation.Dest
import com.cumbre.app.ui.navigation.TopLevels
import com.cumbre.app.ui.screens.activity.ActivityDetailScreen
import com.cumbre.app.ui.screens.activity.ActivityListScreen
import com.cumbre.app.ui.screens.detail.RouteDetailScreen
import com.cumbre.app.ui.screens.detail.RouteMapScreen
import com.cumbre.app.ui.screens.home.HomeMapScreen
import com.cumbre.app.ui.screens.importer.ImportScreen
import com.cumbre.app.ui.screens.more.AboutScreen
import com.cumbre.app.ui.screens.more.MoreScreen
import com.cumbre.app.ui.screens.more.OfflineMapsScreen
import com.cumbre.app.ui.screens.more.PrivacyScreen
import com.cumbre.app.ui.screens.more.SettingsScreen
import com.cumbre.app.ui.screens.more.WikilocScreen
import com.cumbre.app.ui.screens.onboarding.OnboardingScreen
import com.cumbre.app.ui.screens.routes.RoutesScreen
import com.cumbre.app.ui.screens.tracking.TrackingScreen
import com.cumbre.core.recording.RecoveryDecision
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer no disponible") }
val LocalSettings = staticCompositionLocalOf { Settings() }

/** Muestra un mensaje breve (snackbar). */
val LocalMessenger = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Abre el selector de archivos del sistema para elegir un GPX. */
val LocalGpxPicker = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
fun CumbreRoot(container: AppContainer, settings: Settings) {
    val scope = rememberCoroutineScope()
    if (!settings.onboardingDone) {
        OnboardingScreen(onStart = { scope.launch { container.settings.setOnboardingDone() } })
        return
    }
    val store = remember { MapControllerStore() }
    DisposableEffect(store) { onDispose { store.destroyAll() } }
    CompositionLocalProvider(
        LocalMapControllerStore provides store,
        LocalAppContainer provides container,
        LocalSettings provides settings,
    ) {
        MainScaffold(container)
    }
}

@Composable
private fun MainScaffold(container: AppContainer) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = TopLevels.any { it.route == currentRoute }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val messenger: (String) -> Unit = remember { { msg -> scope.launch { snackbar.showSnackbar(msg) } } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) nav.navigate(Dest.import(uri))
    }
    val openPicker: () -> Unit = remember { { picker.launch(arrayOf("*/*")) } }

    // Contenido recibido desde otras apps.
    val incoming by container.incoming.pending.collectAsStateWithLifecycle()
    LaunchedEffect(incoming) {
        val item = incoming ?: return@LaunchedEffect
        when (item) {
            is Incoming.GpxFile -> nav.navigate(Dest.import(item.uri))
            is Incoming.WikilocLink -> nav.navigate(Dest.wikiloc(item.url))
            Incoming.OpenTracking -> if (container.trackingManager.state.value.isActive) nav.navigate(Dest.TRACKING) { launchSingleTop = true }
        }
        container.incoming.consume(item)
    }

    RecoveryHandler(container, onOpenTracking = { nav.navigate(Dest.TRACKING) { launchSingleTop = true } })

    CompositionLocalProvider(LocalMessenger provides messenger, LocalGpxPicker provides openPicker) {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = { if (showBottomBar) CumbreBottomBar(nav, currentRoute) },
        ) { padding ->
            CumbreNavHost(nav, Modifier.padding(bottom = padding.calculateBottomPadding()))
        }
    }
}

@Composable
private fun CumbreBottomBar(nav: NavHostController, currentRoute: String?) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        TopLevels.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        nav.navigate(item.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(item.label, style = MaterialTheme.typography.labelMedium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

@Composable
private fun CumbreNavHost(nav: NavHostController, modifier: Modifier) {
    NavHost(navController = nav, startDestination = Dest.MAP, modifier = modifier) {
        composable(Dest.MAP) { HomeMapScreen(nav) }
        composable(Dest.ROUTES) { RoutesScreen(nav) }
        composable(Dest.ACTIVITY) { ActivityListScreen(nav) }
        composable(Dest.MORE) { MoreScreen(nav) }
        composable(Dest.ROUTE_DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            RouteDetailScreen(nav, entry.arguments?.getLong("id") ?: 0L)
        }
        composable(Dest.ROUTE_MAP, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            RouteMapScreen(nav, entry.arguments?.getLong("id") ?: 0L)
        }
        composable(
            Dest.IMPORT,
            arguments = listOf(navArgument("uri") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            val uri = entry.arguments?.getString("uri")?.let { Uri.parse(it) }
            ImportScreen(nav, uri)
        }
        composable(Dest.TRACKING) { TrackingScreen(nav) }
        composable(Dest.ACTIVITY_DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            ActivityDetailScreen(nav, entry.arguments?.getLong("id") ?: 0L)
        }
        composable(Dest.OFFLINE_MAPS) { OfflineMapsScreen(nav) }
        composable(
            Dest.WIKILOC,
            arguments = listOf(navArgument("url") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry -> WikilocScreen(nav, entry.arguments?.getString("url")) }
        composable(Dest.SETTINGS) { SettingsScreen(nav) }
        composable(Dest.PRIVACY) { PrivacyScreen(nav) }
        composable(Dest.ABOUT) { AboutScreen(nav) }
    }
}

/** Ofrece recuperar una actividad que quedó abierta por un cierre inesperado. */
@Composable
private fun RecoveryHandler(container: AppContainer, onOpenTracking: () -> Unit) {
    val scope = rememberCoroutineScope()
    var decision by remember { mutableStateOf<RecoveryDecision?>(null) }
    var startedAt by remember { mutableStateOf<Long?>(null) }
    var confirmDiscard by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) {
        val d = container.trackingManager.checkRecovery()
        when (d) {
            is RecoveryDecision.DiscardEmpty -> container.trackingManager.discardStored(d.activityId)
            is RecoveryDecision.OfferResume -> {
                startedAt = container.activities.get(d.activityId)?.startedAt
                decision = d
            }
            is RecoveryDecision.OfferFinish -> {
                startedAt = container.activities.get(d.activityId)?.startedAt
                decision = d
            }
            RecoveryDecision.Nothing -> Unit
        }
    }
    val time = startedAt?.let { SimpleDateFormat("d/M 'a las' HH:mm", Locale.forLanguageTag("es-AR")).format(Date(it)) } ?: ""
    when (val d = decision) {
        is RecoveryDecision.OfferResume -> AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.AutoMirrored.Rounded.DirectionsWalk, contentDescription = null) },
            title = { Text("Actividad sin terminar") },
            text = {
                Text(
                    "Encontramos un recorrido iniciado el $time que se interrumpió" +
                        (if (d.wasRecording) " mientras grababa." else ".") +
                        " Los puntos registrados están guardados. ¿Querés continuarlo?",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    decision = null
                    scope.launch {
                        if (container.trackingManager.restore(d.activityId, continueRecording = false)) onOpenTracking()
                    }
                }) { Text("Continuar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    decision = null
                    scope.launch { container.trackingManager.finishStored(d.activityId) }
                }) { Text("Finalizar y guardar") }
            },
        )
        is RecoveryDecision.OfferFinish -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Actividad sin terminar") },
            text = { Text("Hay un recorrido del $time que quedó abierto. Podés guardarlo con los puntos registrados o descartarlo.") },
            confirmButton = {
                TextButton(onClick = {
                    decision = null
                    scope.launch { container.trackingManager.finishStored(d.activityId) }
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { decision = null; confirmDiscard = d.activityId }) { Text("Descartar") }
            },
        )
        else -> Unit
    }
    confirmDiscard?.let { id ->
        ConfirmDialog(
            title = "¿Descartar el recorrido?",
            text = "Se borrarán todos los puntos registrados. Esta acción no se puede deshacer.",
            confirmLabel = "Descartar",
            destructive = true,
            onConfirm = { confirmDiscard = null; scope.launch { container.trackingManager.discardStored(id) } },
            onDismiss = { confirmDiscard = null; scope.launch { container.trackingManager.finishStored(id) } },
            dismissLabel = "Guardar",
        )
    }
}
