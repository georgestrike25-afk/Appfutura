package com.cumbre.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class MapStyle { DEFAULT, OSMARENDER }

data class Settings(
    val onboardingDone: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val offRouteAlertEnabled: Boolean = true,
    val offRouteThresholdMeters: Int = 50,
    val keepScreenOn: Boolean = true,
    val mapStyle: MapStyle = MapStyle.DEFAULT,
    /** Sombreado de relieve (requiere archivos de elevación instalados). */
    val showRelief: Boolean = true,
    val locationPermissionRequested: Boolean = false,
    val notificationPermissionRequested: Boolean = false,
    /** Enlace de Wikiloc recibido que se asociará a la próxima importación. */
    val pendingWikilocUrl: String? = null,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cumbre_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_done")
        val theme = stringPreferencesKey("theme_mode")
        val offRouteEnabled = booleanPreferencesKey("off_route_enabled")
        val offRouteThreshold = intPreferencesKey("off_route_threshold")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val mapStyle = stringPreferencesKey("map_style")
        val relief = booleanPreferencesKey("show_relief")
        val locationAsked = booleanPreferencesKey("location_asked")
        val notificationsAsked = booleanPreferencesKey("notifications_asked")
        val pendingWikiloc = stringPreferencesKey("pending_wikiloc_url")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            onboardingDone = p[Keys.onboarding] ?: false,
            themeMode = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            offRouteAlertEnabled = p[Keys.offRouteEnabled] ?: true,
            offRouteThresholdMeters = p[Keys.offRouteThreshold] ?: 50,
            keepScreenOn = p[Keys.keepScreenOn] ?: true,
            mapStyle = p[Keys.mapStyle]?.let { runCatching { MapStyle.valueOf(it) }.getOrNull() } ?: MapStyle.DEFAULT,
            showRelief = p[Keys.relief] ?: true,
            locationPermissionRequested = p[Keys.locationAsked] ?: false,
            notificationPermissionRequested = p[Keys.notificationsAsked] ?: false,
            pendingWikilocUrl = p[Keys.pendingWikiloc],
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setOnboardingDone() = context.dataStore.edit { it[Keys.onboarding] = true }
    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setOffRouteAlert(enabled: Boolean) = context.dataStore.edit { it[Keys.offRouteEnabled] = enabled }
    suspend fun setOffRouteThreshold(meters: Int) = context.dataStore.edit { it[Keys.offRouteThreshold] = meters }
    suspend fun setKeepScreenOn(enabled: Boolean) = context.dataStore.edit { it[Keys.keepScreenOn] = enabled }
    suspend fun setMapStyle(style: MapStyle) = context.dataStore.edit { it[Keys.mapStyle] = style.name }
    suspend fun setShowRelief(enabled: Boolean) = context.dataStore.edit { it[Keys.relief] = enabled }
    suspend fun setLocationPermissionRequested() = context.dataStore.edit { it[Keys.locationAsked] = true }
    suspend fun setNotificationPermissionRequested() = context.dataStore.edit { it[Keys.notificationsAsked] = true }
    suspend fun setPendingWikilocUrl(url: String?) = context.dataStore.edit {
        if (url == null) it.remove(Keys.pendingWikiloc) else it[Keys.pendingWikiloc] = url
    }

    /** Restablece las preferencias (excepto haber visto la bienvenida). */
    suspend fun resetAll() = context.dataStore.edit {
        val onboarding = it[Keys.onboarding] ?: false
        it.clear()
        it[Keys.onboarding] = onboarding
    }
}
