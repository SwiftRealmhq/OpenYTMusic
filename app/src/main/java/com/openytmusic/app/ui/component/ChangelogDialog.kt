package com.openytmusic.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.openytmusic.app.R

/**
 * Aviso de cambios recientes: aparece UNA vez por version (al actualizar o al
 * instalar por primera vez) y se puede desactivar para siempre desde el propio
 * dialogo. Los textos salen de [com.openytmusic.app.utils.ChangelogData].
 *
 * Se dibuja como una ficha con portada (version + firma del desarrollador) y una
 * tarjeta por cambio, con su icono: es lo primero que ve alguien que acaba de
 * instalar la app, asi que se cuida como una pantalla y no como un aviso.
 *
 * @param onDismiss se llama SIEMPRE al cerrar; [disableForever] indica si el
 * usuario marco "no volver a mostrar".
 */
@Composable
fun ChangelogDialog(
    version: String,
    highlights: List<String>,
    onDismiss: (disableForever: Boolean) -> Unit,
) {
    var disableForever by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { onDismiss(disableForever) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                ChangelogHeader(version = version)

                Spacer(Modifier.height(18.dp))

                Column(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    highlights.forEachIndexed { index, highlight ->
                        HighlightCard(index = index, text = highlight)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // "No volver a mostrar": fuera del area con desplazamiento, para
                // que quede siempre a la vista.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .toggleable(
                            value = disableForever,
                            onValueChange = { disableForever = it }
                        )
                ) {
                    Checkbox(checked = disableForever, onCheckedChange = null)
                    Text(
                        text = stringResource(R.string.changelog_dont_show_again),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = { onDismiss(disableForever) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = stringResource(R.string.changelog_ok),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/** Portada del aviso: el icono de la app sobre un halo y la version al frente. */
@Composable
private fun ChangelogHeader(version: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary,
                        )
                    )
                )
        ) {
            Icon(
                painterResource(R.drawable.music_note),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.changelog_title, version),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.changelog_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Un cambio de la version, en su tarjeta con icono. */
@Composable
private fun HighlightCard(index: Int, text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(
                    painterResource(HIGHLIGHT_ICONS[index % HIGHLIGHT_ICONS.size]),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Icono de cada tarjeta, por orden de aparicion (se repiten si hay mas cambios). */
private val HIGHLIGHT_ICONS = listOf(
    R.drawable.person,
    R.drawable.music_note,
    R.drawable.album,
    R.drawable.discord,
    R.drawable.bar_chart,
    R.drawable.info,
)
