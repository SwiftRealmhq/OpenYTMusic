package com.openytmusic.app.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openytmusic.app.R
import com.openytmusic.app.constants.BotWallDetectedKey
import com.openytmusic.app.utils.rememberPreference

/**
 * Cartel que avisa que YouTube respondio con el muro anti-bot (*"Sign in to confirm you're not a
 * bot"*), sin importar por que via salio: lo enciende [com.openytmusic.app.playback.MusicService]
 * tanto si el rescate con PoToken salvo la reproduccion como si no.
 *
 * Por que en preferencias y no en memoria: la deteccion ocurre en el servicio, que puede estar
 * corriendo sin UI. Asi el aviso sigue ahi cuando el usuario abra la app (y sobrevive al proceso).
 *
 * Es un cartel, no un dialogo: se puede seguir usando la app con el a la vista, y el usuario decide
 * si inicia sesion o lo descarta. Descartarlo no cambia nada de la reproduccion.
 */
@Composable
fun BotWallNotice(
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pending by rememberPreference(BotWallDetectedKey, false)

    AnimatedVisibility(
        visible = pending,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.security),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.bot_wall_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.bot_wall_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = { pending = false }) {
                        Text(stringResource(R.string.bot_wall_dismiss))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            pending = false
                            onLogin()
                        }
                    ) {
                        Text(stringResource(R.string.bot_wall_login))
                    }
                }
            }
        }
    }
}
