package com.openytmusic.app.ui.component

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.Image
import com.openytmusic.app.R
import com.openytmusic.app.constants.BotWallDetectedKey
import com.openytmusic.app.constants.InnerTubeCookieKey
import com.openytmusic.app.constants.SignedOutNoticeShownAtKey
import com.openytmusic.app.utils.rememberPreference
import kotlinx.coroutines.delay

/**
 * Aviso periodico y amable para quien NO ha iniciado sesion en YouTube Music.
 *
 * Por que existe: la forma de fondo de esquivar el muro anti-bot (y de tener las playlists y los
 * "Me gusta" sincronizados) es capturar la sesion. Mucha gente no lo hace porque nadie se lo pide,
 * asi que cada [INTERVAL_MILLIS] se le recuerda una vez, con tono tranquilo y sin bloquear nada.
 *
 * Reglas de aparicion (todas juntas):
 * 1. La app tiene que estar en primer plano ([enabled] lo controla desde MainActivity).
 * 2. El usuario NO debe tener sesion capturada ([InnerTubeCookieKey] vacio).
 * 3. Tiene que haber pasado [INTERVAL_MILLIS] desde el ultimo aviso.
 * 4. No debe estar ya en pantalla el aviso del muro anti-bot: ese es urgente y no se solapan.
 *
 * Nunca interrumpe la reproduccion: es un dialogo informativo que se cierra con "Ahora no".
 */
@Composable
fun SignedOutNotice(
    enabled: Boolean,
    onLogin: () -> Unit,
) {
    var cookie by rememberPreference(InnerTubeCookieKey, "")
    var lastShownAt by rememberPreference(SignedOutNoticeShownAtKey, 0L)
    var botWallPending by rememberPreference(BotWallDetectedKey, false)
    var visible by remember { mutableStateOf(false) }

    // Un solo efecto para todo el ciclo: al entrar en primer plano evalua si ya toca, y si no,
    // se duerme exactamente lo que falta. Asi el aviso tambien aparece cuando se cumplen las 4 h
    // con la app abierta, y no solo al abrirla. Se lee `lastShownAt` con .value (no como key) a
    // proposito: al registrarlo el efecto no debe re-dispararse, o se reprogramaria solo.
    LaunchedEffect(enabled, cookie, botWallPending) {
        if (!enabled || cookie.isNotBlank() || botWallPending) return@LaunchedEffect
        // `dueAt` vive en la corrutina a proposito. Leerlo de vuelta de la preferencia obligaria a
        // esperar el viaje a disco, y mientras tanto el bucle creeria que ya toca otra vez (y
        // reescribiria el aviso en cada vuelta). Aqui se calcula una vez y se avanza a mano.
        var dueAt = lastShownAt + INTERVAL_MILLIS
        while (true) {
            val now = System.currentTimeMillis()
            if (now >= dueAt) {
                // Se marca ANTES de mostrar: si el usuario mata la app con el aviso puesto,
                // tampoco se repite al volver a abrir.
                lastShownAt = now
                visible = true
                dueAt = now + INTERVAL_MILLIS
            }
            delay((dueAt - System.currentTimeMillis()).coerceAtLeast(1_000L))
        }
    }

    // Red de seguridad: si YouTube tira el muro mientras este aviso esta abierto, se retira solo
    // para dejar pasar el urgente (que si trae el contexto de "te bloquearon"). Al cambiar
    // `botWallPending` el efecto de arriba se reinicia solo y ya no vuelve a mostrarlo.
    LaunchedEffect(botWallPending) {
        if (botWallPending) visible = false
    }

    if (!visible) return

    Dialog(
        onDismissRequest = { visible = false },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 380.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 26.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Image(
                        painter = painterResource(R.drawable.openytmusic_logo),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                    )
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    text = stringResource(R.string.signed_out_notice_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.signed_out_notice_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(18.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BenefitRow(stringResource(R.string.signed_out_notice_benefit_1))
                    BenefitRow(stringResource(R.string.signed_out_notice_benefit_2))
                    BenefitRow(stringResource(R.string.signed_out_notice_benefit_3))
                }

                Spacer(Modifier.height(22.dp))

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = { visible = false }) {
                        Text(stringResource(R.string.bot_wall_dismiss))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            visible = false
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

/** Una linea de beneficio con su punto de color. Sin iconos: menos ruido, mas elegante. */
@Composable
private fun BenefitRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Cada cuanto se le recuerda al usuario sin sesion. Cuatro horas: suficiente para que se tope con
 * el aviso en cualquier sesion normal de uso, y lo bastante espaciado para no resultar pesado.
 */
private const val INTERVAL_MILLIS = 4L * 60L * 60L * 1000L
