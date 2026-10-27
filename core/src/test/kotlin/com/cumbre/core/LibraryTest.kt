package com.cumbre.core

import com.cumbre.core.library.LibraryQuery
import com.cumbre.core.library.RangeFilter
import com.cumbre.core.library.RouteCategory
import com.cumbre.core.library.RouteLibrary
import com.cumbre.core.library.RouteSort
import com.cumbre.core.library.RouteSource
import com.cumbre.core.library.RouteSummary
import com.cumbre.core.library.WikilocLinks
import com.cumbre.core.permissions.LocationPermissionPolicy
import com.cumbre.core.permissions.PermissionAction
import com.cumbre.core.permissions.PermissionSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryTest {
    private val routes = listOf(
        RouteSummary(1, "Cerro Champaquí", RouteSource.WIKILOC, 18_400.0, 1_142.0, 26_400, true, 1_000, 5_000),
        RouteSummary(2, "Quebrada del Condorito", RouteSource.IMPORTED, 11_200.0, 486.0, 13_800, false, 2_000, null),
        RouteSummary(3, "Salida del domingo", RouteSource.RECORDED, 9_600.0, null, 18_600, true, 3_000, 4_000),
    )

    @Test
    fun `categorias de la biblioteca`() {
        fun ids(c: RouteCategory) = RouteLibrary.apply(routes, LibraryQuery(category = c)).map { it.id }
        assertEquals(listOf(3L, 2L, 1L).toSet(), ids(RouteCategory.ALL).toSet())
        assertEquals(listOf(3L), ids(RouteCategory.MINE))
        assertEquals(setOf(1L, 2L), ids(RouteCategory.IMPORTED).toSet())
        assertEquals(setOf(1L, 3L), ids(RouteCategory.FAVORITES).toSet())
        assertEquals(listOf(1L, 3L), ids(RouteCategory.RECENT))
    }

    @Test
    fun `busqueda sin tildes y filtros por rango`() {
        assertEquals(listOf(1L), RouteLibrary.apply(routes, LibraryQuery(text = "champaqui")).map { it.id })
        val q = LibraryQuery(distanceKm = RangeFilter(10.0, 20.0), sort = RouteSort.DISTANCE)
        assertEquals(listOf(2L, 1L), RouteLibrary.apply(routes, q).map { it.id })
        // Sin desnivel conocido no pasa un filtro de desnivel.
        assertEquals(listOf(1L), RouteLibrary.apply(routes, LibraryQuery(elevationGainM = RangeFilter(min = 1_000.0))).map { it.id })
        assertEquals(listOf(2L), RouteLibrary.apply(routes, LibraryQuery(durationHours = RangeFilter(max = 4.0))).map { it.id })
    }

    @Test
    fun `enlaces de Wikiloc`() {
        assertTrue(WikilocLinks.isWikilocUrl("https://es.wikiloc.com/rutas-senderismo/cerro-1234"))
        assertTrue(WikilocLinks.isWikilocUrl("https://www.wikiloc.com/wikiloc/view.do?id=1"))
        assertFalse(WikilocLinks.isWikilocUrl("https://wikiloc.com.ejemplo.net/"))
        assertFalse(WikilocLinks.isWikilocUrl("javascript:alert(1)"))
        assertFalse(WikilocLinks.isWikilocUrl(null))
        assertEquals(
            "https://es.wikiloc.com/rutas-senderismo/cerro-1234",
            WikilocLinks.extractUrl("Mirá esta ruta: https://es.wikiloc.com/rutas-senderismo/cerro-1234."),
        )
        assertNull(WikilocLinks.extractUrl("sin enlaces"))
    }

    @Test
    fun `permisos de ubicacion progresivos`() {
        fun d(fine: Boolean, coarse: Boolean, rationale: Boolean, asked: Boolean) =
            LocationPermissionPolicy.decide(PermissionSnapshot(fine, coarse, rationale, asked))
        assertEquals(PermissionAction.READY, d(true, true, false, true))
        assertEquals(PermissionAction.APPROXIMATE_ONLY, d(false, true, true, true))
        assertEquals(PermissionAction.EXPLAIN_THEN_REQUEST, d(false, false, false, false))
        assertEquals(PermissionAction.SHOW_RATIONALE, d(false, false, true, true))
        assertEquals(PermissionAction.OPEN_SETTINGS, d(false, false, false, true))
        assertTrue(LocationPermissionPolicy.canNavigate(PermissionAction.READY))
        assertFalse(LocationPermissionPolicy.canNavigate(PermissionAction.APPROXIMATE_ONLY))
        assertTrue(LocationPermissionPolicy.canShowPosition(PermissionAction.APPROXIMATE_ONLY))
    }
}
