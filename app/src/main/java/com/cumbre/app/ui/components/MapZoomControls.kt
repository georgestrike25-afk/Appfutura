package com.cumbre.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cumbre.app.map.CumbreMapController

/** Botones + y − para acercar y alejar sin depender solo del gesto de pellizcar. */
@Composable
fun MapZoomControls(controller: CumbreMapController?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MapControlButton(Icons.Rounded.Add, "Acercar", onClick = { controller?.zoomIn() })
        MapControlButton(Icons.Rounded.Remove, "Alejar", onClick = { controller?.zoomOut() })
    }
}
