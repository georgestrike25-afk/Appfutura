package com.cumbre.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cumbre.app.map.CumbreMapController
import com.cumbre.app.ui.theme.CumbreTheme

/** Botones + y − agrupados en un solo control, para acercar y alejar sin depender del pellizco. */
@Composable
fun MapZoomControls(controller: CumbreMapController?, modifier: Modifier = Modifier) {
    val extra = CumbreTheme.extra
    Surface(modifier = modifier, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = extra.mapControl, shadowElevation = 6.dp) {
        Column {
            Box(Modifier.size(52.dp).clickable(role = Role.Button, onClickLabel = "Acercar") { controller?.zoomIn() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, contentDescription = "Acercar", tint = extra.onMapControl)
            }
            HorizontalDivider(Modifier.size(width = 52.dp, height = 1.dp), color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.size(52.dp).clickable(role = Role.Button, onClickLabel = "Alejar") { controller?.zoomOut() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Remove, contentDescription = "Alejar", tint = extra.onMapControl)
            }
        }
    }
}
