package com.cumbre.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.cumbre.app.ui.theme.CumbreColors
import com.cumbre.app.ui.theme.Spacing

@Composable
private fun ButtonContent(text: String, icon: ImageVector?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** Acción principal (verde bosque). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.primary,
    content: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = Spacing.touchTarget),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) { ButtonContent(text, icon) }
}

/** Acción de lanzamiento destacada (naranja), usada con moderación. */
@Composable
fun AccentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) =
    PrimaryButton(text, onClick, modifier, icon, container = CumbreColors.Naranja, content = CumbreColors.GrisOscuro)

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = Spacing.touchTarget),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    ) { ButtonContent(text, icon) }
}

@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = Spacing.touchTarget),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) { ButtonContent(text, icon) }
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 44.dp)) { ButtonContent(text, icon) }
}
