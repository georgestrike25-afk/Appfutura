# Cumbre — memoria del proyecto

App Android de trekking: rutas GPX (incl. Wikiloc por enlace/archivo), navegación GPS, grabación
de actividades y mapas offline Mapsforge. Sin Internet, sin cuenta, sin APIs pagas.
Documentación de usuario: `README.md` (español). Diseño aprobado: `design/` (ícono E).

## Tecnologías
Kotlin 2.0.21, Jetpack Compose (BOM 2024.12.01) + Material 3, MVVM, Coroutines/Flow, Room 2.6.1 (v1,
sin migraciones aún), DataStore, Navigation Compose 2.8.5, Mapsforge 0.25.0, AGP 8.7.3, Gradle 8.14.3,
compile/target 35, min 26. Fuentes Montserrat + Inter.

## Estructura
- `core/` — Kotlin JVM puro (testeable sin Android): gpx, analysis (incl. Difficulty), geo,
  navigation (RouteFollower, EtaEstimator), recording (filtro, stats, recuperación), maps, format.
- `app/` — Android: `data/` (Room, repos, SettingsRepository), `map/` (CumbreMapController,
  MarkersLayer), `tracking/` (TrackingManager singleton + TrackingService FGS location),
  `ui/` (theme, components incl. DesignSystem.kt, platform, navigation, screens/*).
- `app/src/androidTest/` — PersistenceTest, RecordingBackgroundTest, ScreenshotsTest, TestRoute.
- `scripts/` — emulador-gps.py (geo fix), emulador-pruebas.sh.
- `design/` — maquetas HTML y previews PNG.

## Decisiones clave
- Navegación: 5 pestañas (Inicio, Mapa, Rutas, Actividad, Más); inicio = `Dest.HOME`.
- Iniciar grabación SIEMPRE con `rememberRecordingStarter(nav)` (permiso + guía de batería), tanto
  libre (`null`) como siguiendo ruta (`routeId`).
- Blindaje de grabación: wake lock parcial mientras graba, servicio vivo en pausa (sin GPS),
  vigilante de GPS 90 s, pausar/reanudar desde notificación, `stopWithTask=false`, reinicio del
  proceso restaura grabando o en pausa. Permisos WAKE_LOCK y REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
  (verificados por `AppManifestPolicyTest`, que exige el set exacto de permisos).
- Sin permiso INTERNET (enlaces externos se abren en el navegador).
- Mapa/relieve de Argentina/Córdoba: release `mapas-argentina` (workflow `mapas.yml`).
- Debug usa `applicationIdSuffix ".debug"` → paquete `com.cumbre.app.debug` (test: `.debug.test`).
- Mapsforge `Layer.onTap` debe tener parámetros anulables (crash previo).

## Comandos
- Pruebas core: `cd core && ../gradlew test` (en este entorno: `-I <scratchpad>/central.init.gradle.kts`).
- APK: `./gradlew :app:assembleDebug` (Google Maven bloqueado en el entorno cloud: compilar en CI).
- CI: `android.yml` (tests + APK → release `debug-latest`), `emulador.yml` (pruebas instrumentadas
  en emulador API 34 + capturas → release `capturas-latest`).

## Estado
- Rediseño aprobado implementado (Inicio, Mapa, Rutas, Detalle, Importación, Navegación, Finalizada,
  Mapas offline, Ajustes). Core: 85 pruebas OK. CI Android: verde.
- Workflow emulador: primera corrida falló por nombre de paquete de test (corregido).

## Problemas conocidos / pendientes
- Ahorro de batería por fabricante (MIUI, One UI…) no reproducible en emulador; probar en teléfono.
- Textos de UI en código (no `strings.xml`). Datos sin cifrado propio.
- Pendiente opcional: ilustración de estado vacío de Rutas, hoja previa de permiso GPS con diseño.
