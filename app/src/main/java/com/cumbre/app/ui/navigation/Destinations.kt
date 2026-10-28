package com.cumbre.app.ui.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Route
import androidx.compose.ui.graphics.vector.ImageVector

object Dest {
    const val MAP = "map"
    const val ROUTES = "routes"
    const val ACTIVITY = "activity"
    const val MORE = "more"
    const val ROUTE_DETAIL = "route/{id}"
    const val ROUTE_MAP = "route/{id}/map"
    const val IMPORT = "import?uri={uri}"
    const val TRACKING = "tracking"
    const val ACTIVITY_DETAIL = "activity/{id}"
    const val OFFLINE_MAPS = "maps"
    const val OFFLINE_MAPS_IMPORT = "maps/import?uri={uri}&kind={kind}"
    const val WIKILOC = "wikiloc?url={url}"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"
    const val ABOUT = "about"

    fun routeDetail(id: Long) = "route/$id"
    fun routeMap(id: Long) = "route/$id/map"
    fun activityDetail(id: Long) = "activity/$id"
    fun import(uri: Uri) = "import?uri=${Uri.encode(uri.toString())}"
    fun offlineMapsImport(uri: Uri, kind: String) = "maps/import?uri=${Uri.encode(uri.toString())}&kind=$kind"
    fun wikiloc(url: String? = null) = if (url == null) "wikiloc" else "wikiloc?url=${Uri.encode(url)}"
}

data class TopLevel(val route: String, val label: String, val icon: ImageVector)

val TopLevels = listOf(
    TopLevel(Dest.MAP, "Mapa", Icons.Rounded.Map),
    TopLevel(Dest.ROUTES, "Rutas", Icons.Rounded.Route),
    TopLevel(Dest.ACTIVITY, "Actividad", Icons.AutoMirrored.Rounded.DirectionsWalk),
    TopLevel(Dest.MORE, "Más", Icons.Rounded.MoreHoriz),
)

