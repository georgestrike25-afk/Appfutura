package com.cumbre.app.ui.screens.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cumbre.app.R
import com.cumbre.app.ui.components.AccentButton
import com.cumbre.app.ui.theme.CumbreColors
import com.cumbre.app.ui.theme.Spacing

@Composable
fun OnboardingScreen(onStart: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(CumbreColors.VerdeMontana)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Image(
            painter = painterResource(R.drawable.onboarding_mountains),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(340f / 330f),
        )
        Column(Modifier.padding(horizontal = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.cumbre_mark), contentDescription = null, modifier = Modifier.size(52.dp))
                Spacer(Modifier.width(Spacing.m))
                Text("Cumbre", style = MaterialTheme.typography.displaySmall, color = Color.White)
            }
            Spacer(Modifier.height(18.dp))
            Text("Tu camino, en tus manos.", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Spacer(Modifier.height(10.dp))
            Text(
                "Importá rutas GPX de Wikiloc o de tus amigos, preparalas y seguilas con GPS y mapas sin conexión, aunque no haya señal.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFC9DDD2),
            )
            Spacer(Modifier.height(Spacing.xxl))
            AccentButton("Comenzar", onClick = onStart, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Spacing.m))
            Text(
                "Sin cuentas ni registro. Tus datos quedan en tu teléfono.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9FBFB0),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}
