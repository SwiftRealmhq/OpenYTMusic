package com.openytmusic.app.ui.theme

import android.graphics.Bitmap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import com.google.material.color.dynamiccolor.DynamicScheme
import com.google.material.color.hct.Hct
import com.google.material.color.scheme.SchemeFidelity
import com.google.material.color.scheme.SchemeTonalSpot
import com.google.material.color.score.Score

/**
 * Color de enfasis de la app: rojo YouTube Music.
 * Cuando no hay color dinamico de portada, este es el rojo que pinta la app.
 */
val DefaultThemeColor = Color(0xFFFF0000)

@Composable
fun LunaTheme(
    darkTheme: Boolean = true,
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    content: @Composable () -> Unit,
) {
    val colorScheme = remember(themeColor) {
        // Siempre oscuro. El acento sale del color semilla (rojo YT Music o el
        // extraido de la portada) pero saturado al maximo para que sea
        // pronunciado, no pastelon.
        val hct = Hct.fromInt(themeColor.toArgb())
        val base = SchemeFidelity(hct, true, 0.0).toColorScheme()
        val vivid = Color(Hct.from(hct.hue, (hct.chroma * 1.7).coerceAtMost(130.0), 68.0).toInt())
        base.copy(
            primary = vivid,
            surfaceTint = vivid,
            // Dark duro: negros y grises neutros, nada de superficies lavadas.
            background = Color(0xFF000000),
            surface = Color(0xFF000000),
            surfaceDim = Color(0xFF000000),
            surfaceBright = Color(0xFF2A2A2A),
            surfaceContainerLowest = Color(0xFF070707),
            surfaceContainerLow = Color(0xFF0E0E0E),
            surfaceContainer = Color(0xFF151515),
            surfaceContainerHigh = Color(0xFF1E1E1E),
            surfaceContainerHighest = Color(0xFF282828)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}

fun Bitmap.extractThemeColor(): Color {
    val colorsToPopulation = Palette.from(this)
        .maximumColorCount(8)
        .generate()
        .swatches
        .associate { it.rgb to it.population }
    val rankedColors = Score.score(colorsToPopulation)
    return Color(rankedColors.first())
}

fun DynamicScheme.toColorScheme() = ColorScheme(
    primary = Color(primary),
    onPrimary = Color(onPrimary),
    primaryContainer = Color(primaryContainer),
    onPrimaryContainer = Color(onPrimaryContainer),
    inversePrimary = Color(inversePrimary),
    secondary = Color(secondary),
    onSecondary = Color(onSecondary),
    secondaryContainer = Color(secondaryContainer),
    onSecondaryContainer = Color(onSecondaryContainer),
    tertiary = Color(tertiary),
    onTertiary = Color(onTertiary),
    tertiaryContainer = Color(tertiaryContainer),
    onTertiaryContainer = Color(onTertiaryContainer),
    background = Color(background),
    onBackground = Color(onBackground),
    surface = Color(surface),
    onSurface = Color(onSurface),
    surfaceVariant = Color(surfaceVariant),
    onSurfaceVariant = Color(onSurfaceVariant),
    surfaceTint = Color(primary),
    inverseSurface = Color(inverseSurface),
    inverseOnSurface = Color(inverseOnSurface),
    error = Color(error),
    onError = Color(onError),
    errorContainer = Color(errorContainer),
    onErrorContainer = Color(onErrorContainer),
    outline = Color(outline),
    outlineVariant = Color(outlineVariant),
    scrim = Color(scrim),
    surfaceBright = Color(surfaceBright),
    surfaceDim = Color(surfaceDim),
    surfaceContainer = Color(surfaceContainer),
    surfaceContainerHigh = Color(surfaceContainerHigh),
    surfaceContainerHighest = Color(surfaceContainerHighest),
    surfaceContainerLow = Color(surfaceContainerLow),
    surfaceContainerLowest = Color(surfaceContainerLowest)
)

fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(
        surface = Color.Black,
        background = Color.Black
    ) else this

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}
