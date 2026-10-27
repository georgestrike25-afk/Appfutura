package com.cumbre.app.ui.screens.routes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.cumbre.app.ui.LocalAppContainer
import com.cumbre.app.ui.LocalGpxPicker
import com.cumbre.app.ui.components.ChipOption
import com.cumbre.app.ui.components.ChoiceChips
import com.cumbre.app.ui.components.CumbreTopBar
import com.cumbre.app.ui.components.EmptyState
import com.cumbre.app.ui.components.LoadingState
import com.cumbre.app.ui.components.PrimaryButton
import com.cumbre.app.ui.components.RouteCard
import com.cumbre.app.ui.components.SearchField
import com.cumbre.app.ui.components.SectionTitle
import com.cumbre.app.ui.cumbreViewModel
import com.cumbre.app.ui.navigation.Dest
import com.cumbre.app.ui.theme.CumbreTheme
import com.cumbre.app.ui.theme.Spacing
import com.cumbre.core.library.RouteCategory
import com.cumbre.core.library.RouteSort

private val categories = listOf(
    ChipOption(RouteCategory.ALL, "Todas"),
    ChipOption(RouteCategory.MINE, "Mis rutas"),
    ChipOption(RouteCategory.IMPORTED, "Importadas"),
    ChipOption(RouteCategory.FAVORITES, "Favoritas"),
    ChipOption(RouteCategory.RECENT, "Recientes"),
)

@Composable
fun RoutesScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val openPicker = LocalGpxPicker.current
    val vm = cumbreViewModel { RoutesViewModel(container.routes, container.maps) }
    val state by vm.state.collectAsStateWithLifecycle()
    val filters by vm.filters.collectAsStateWithLifecycle()
    var sortMenu by remember { mutableStateOf(false) }
    var filterDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            CumbreTopBar(
                title = "Rutas",
                actions = {
                    IconButton(onClick = { filterDialog = true }) {
                        BadgedBox(badge = { if (filters.activeFilters > 0) Badge { Text("${filters.activeFilters}") } }) {
                            Icon(Icons.Rounded.FilterList, contentDescription = "Filtros", tint = CumbreTheme.extra.onTopBar)
                        }
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Ordenar", tint = CumbreTheme.extra.onTopBar)
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            listOf(
                                RouteSort.RECENT_FIRST to "Más recientes",
                                RouteSort.NAME to "Nombre",
                                RouteSort.DISTANCE to "Distancia",
                                RouteSort.ELEVATION to "Mayor desnivel",
                            ).forEach { (sort, label) ->
                                DropdownMenuItem(
                                    text = { Text(if (filters.sort == sort) "✓  $label" else label) },
                                    onClick = { vm.setSort(sort); sortMenu = false },
                                )
                            }
                        }
                    }
                },
                bottom = {
                    Spacer(Modifier.height(4.dp))
                    SearchField(filters.text, vm::setText, "Buscar en mis rutas")
                },
            )
            ChoiceChips(categories, filters.category, vm::setCategory, Modifier.padding(top = Spacing.m, bottom = Spacing.xs))
            when {
                state.loading -> LoadingState("Cargando rutas…")
                state.totalRoutes == 0 -> EmptyState(
                    icon = Icons.Rounded.Route,
                    title = "Todavía no tenés rutas",
                    text = "Importá un archivo GPX descargado de Wikiloc o recibido de otra persona. También podés grabar tus propias salidas desde Actividad.",
                    action = { PrimaryButton("Importar GPX", onClick = openPicker, icon = Icons.Rounded.Add) },
                )
                state.items.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = "Sin resultados",
                    text = "No hay rutas que coincidan con la búsqueda o los filtros.",
                    action = { TextButton(onClick = { vm.clearFilters(); vm.setText(""); vm.setCategory(RouteCategory.ALL) }) { Text("Quitar filtros") } },
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, top = Spacing.s, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    item {
                        Text(
                            "${state.items.size} ${if (state.items.size == 1) "ruta" else "rutas"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(state.items, key = { it.route.id }) { item ->
                        RouteCard(item.route, item.coverage, onClick = { nav.navigate(Dest.routeDetail(item.route.id)) })
                    }
                }
            }
        }
        if (state.totalRoutes > 0) {
            ExtendedFloatingActionButton(
                onClick = openPicker,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Importar GPX", style = MaterialTheme.typography.labelLarge) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.l),
            )
        }
    }

    if (filterDialog) {
        AlertDialog(
            onDismissRequest = { filterDialog = false },
            title = { Text("Filtrar rutas") },
            text = {
                Column {
                    SectionTitle("Distancia", Modifier.padding(top = 0.dp))
                    ChoiceChips(DistanceFilter.entries.map { ChipOption(it, it.label) }, filters.distance, vm::setDistance, contentPadding = PaddingValues(0.dp))
                    SectionTitle("Desnivel positivo")
                    ChoiceChips(ElevationFilter.entries.map { ChipOption(it, it.label) }, filters.elevation, vm::setElevation, contentPadding = PaddingValues(0.dp))
                    SectionTitle("Duración estimada")
                    ChoiceChips(DurationFilter.entries.map { ChipOption(it, it.label) }, filters.duration, vm::setDuration, contentPadding = PaddingValues(0.dp))
                    Text(
                        "Las rutas sin desnivel o duración conocidos no aparecen al filtrar por esos datos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.m),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { filterDialog = false }) { Text("Listo") } },
            dismissButton = { TextButton(onClick = { vm.clearFilters() }) { Text("Quitar filtros") } },
        )
    }
}
