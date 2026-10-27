package com.cumbre.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cumbre.app.ui.theme.CumbreTheme
import com.cumbre.app.ui.theme.Spacing

/** Barra superior con el verde montaña de la marca. */
@Composable
fun CumbreTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottom: @Composable ColumnScope.() -> Unit = {},
) {
    val extra = CumbreTheme.extra
    Column(
        modifier
            .fillMaxWidth()
            .background(extra.topBar)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = if (onBack != null) 4.dp else Spacing.l, end = 4.dp, top = 6.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(52.dp)) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver", tint = extra.onTopBar)
                }
            }
            Text(
                title,
                style = if (onBack != null) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                color = extra.onTopBar,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = if (onBack != null) 4.dp else 0.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        Column(Modifier.padding(end = Spacing.m - 4.dp, start = if (onBack != null) Spacing.m else 0.dp), content = bottom)
    }
}

/** Tarjeta base de la app: superficie blanca, radio moderado y sombra mínima. */
@Composable
fun CumbreCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    container: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = container)
    val elevation = CardDefaults.cardElevation(defaultElevation = if (CumbreTheme.isDark) 0.dp else 1.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.large, colors = colors, elevation = elevation, content = content)
    } else {
        Card(modifier = modifier, shape = MaterialTheme.shapes.large, colors = colors, elevation = elevation, content = content)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = Spacing.l, bottom = Spacing.s),
    )
}

/** Panel inferior del mapa con su "manija". */
@Composable
fun MapBottomPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.padding(start = Spacing.l, end = Spacing.l, top = 10.dp, bottom = Spacing.m)) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline),
            )
            Spacer(Modifier.height(Spacing.m))
            content()
        }
    }
}

/** Botón flotante discreto sobre el mapa. */
@Composable
fun MapControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = CumbreTheme.extra.onMapControl,
    active: Boolean = false,
) {
    val extra = CumbreTheme.extra
    Box(
        modifier
            .size(48.dp)
            .shadow(4.dp, MaterialTheme.shapes.medium)
            .clip(MaterialTheme.shapes.medium)
            .background(if (active) MaterialTheme.colorScheme.primaryContainer else extra.mapControl)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint)
    }
}

/** Fila de lista para menús de configuración. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = Spacing.l, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        if (icon != null) {
            Box(
                Modifier.size(40.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun IconBadge(icon: ImageVector, container: Color, tint: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(44.dp).clip(MaterialTheme.shapes.medium).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint)
    }
}
