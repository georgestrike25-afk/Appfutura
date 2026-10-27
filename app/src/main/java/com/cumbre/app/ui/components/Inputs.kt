package com.cumbre.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.cumbre.app.ui.theme.Spacing

/** Campo de búsqueda sobre la barra verde. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onDark: Boolean = true,
) {
    val bg = if (onDark) Color.White.copy(alpha = 0.13f) else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (onDark) Color.White else MaterialTheme.colorScheme.onSurface
    val hint = if (onDark) Color(0xFFC9DDD2) else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.fillMaxWidth().height(46.dp).clip(MaterialTheme.shapes.medium).background(bg).padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = hint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = hint)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = fg),
                cursorBrush = SolidColor(fg),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValueChange("") }) { Icon(Icons.Rounded.Close, contentDescription = "Borrar búsqueda", tint = hint) }
        }
    }
}

data class ChipOption<T>(val value: T, val label: String)

/** Fila desplazable de chips de selección única. */
@Composable
fun <T> ChoiceChips(
    options: List<ChipOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = Spacing.l),
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        options.forEach { opt ->
            FilterChip(
                selected = opt.value == selected,
                onClick = { onSelect(opt.value) },
                label = { Text(opt.label, style = MaterialTheme.typography.labelMedium) },
                shape = MaterialTheme.shapes.small,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondary,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    }
}
