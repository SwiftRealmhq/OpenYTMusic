package com.openytmusic.app.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.openytmusic.app.BuildConfig
import com.openytmusic.app.Kernel
import com.openytmusic.app.LocalPlayerAwareWindowInsets
import com.openytmusic.app.R
import com.openytmusic.app.ui.component.IconButton
import com.openytmusic.app.ui.utils.backToMain

/**
 * Acerca de OpenYTMusic: minimo y limpio — logo animado, nombre y version.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    // Animacion de aparicion del logo: aparece con fade + escala al entrar
    var logoVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        logoVisible = true
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(androidx.compose.foundation.layout.WindowInsetsSides.Horizontal + androidx.compose.foundation.layout.WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(androidx.compose.foundation.layout.WindowInsetsSides.Top)))

        Spacer(Modifier.height(24.dp))

        // --- Encabezado: solo el banner, con animacion de entrada ------------
        AnimatedVisibility(
            visible = logoVisible,
            enter = fadeIn(animationSpec = tween(durationMillis = 700)) +
                    scaleIn(initialScale = 0.85f, animationSpec = tween(durationMillis = 700))
        ) {
            Image(
                // Banner oficial (PNG puro, con transparencia).
                painter = painterResource(R.drawable.openytmusic_banner),
                contentDescription = Kernel.APP_NAME,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(28.dp))

        // --- Version ----------------------------------------------------------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            InfoRow(
                label = stringResource(R.string.about_app_version),
                value = "v${BuildConfig.VERSION_NAME}"
            )
        }

        Spacer(Modifier.height(32.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.about)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
