package com.theveloper.pixelplay.data.datasource

import android.graphics.Color
import com.theveloper.pixelplay.data.model.Genre
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object GenreDataSource {

    /**
     * Calcula la luminancia relativa de un color (sRGB), un componente clave para el cálculo del contraste WCAG.
     */
    private fun getRelativeLuminance(color: Int): Double {
        val rLinear = convertToLinear(Color.red(color))
        val gLinear = convertToLinear(Color.green(color))
        val bLinear = convertToLinear(Color.blue(color))
        return 0.2126 * rLinear + 0.7152 * gLinear + 0.0722 * bLinear
    }

    /**
     * Convierte un componente de color (0-255) a su valor lineal para el cálculo de luminancia.
     */
    private fun convertToLinear(component: Int): Double {
        val c = component / 255.0
        return if (c <= 0.03928) (c) / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }

    /**
     * Calcula la relación de contraste WCAG 2.1 entre dos colores.
     * Una relación de 4.5:1 es generalmente el mínimo para texto normal.
     */
    private fun getContrastRatio(color1: Int, color2: Int): Double {
        val luminance1 = getRelativeLuminance(color1)
        val luminance2 = getRelativeLuminance(color2)
        val l1 = max(luminance1, luminance2)
        val l2 = min(luminance1, luminance2)
        return (l1 + 0.05) / (l2 + 0.05)
    }

    /**
     * Genera un color 'on-color' tintado para un color base dado,
     * determinando el color principal de contraste (blanco o negro)
     * basado en si el objetivo es un tema oscuro o claro.
     *
     * @param baseColorHex El color hexadecimal del fondo al que se le aplicará el 'on-color'.
     * @param isDarkThemeTarget Booleano que indica si este 'on-color' es para un tema oscuro.
     * @param tintAmount La cantidad de tinte del color base a aplicar (0.0 a 1.0).
     * Un valor más pequeño mantiene el color más cerca del blanco/negro puro.
     * @return El color hexadecimal del 'on-color' tintado.
     */
    private fun getTintedOnColorHex(baseColorHex: String, isDarkThemeTarget: Boolean, tintAmount: Float = 0.20f): String {
        val baseColor = Color.parseColor(baseColorHex)
        val primaryOnColor: Int

        // Forzamos el color principal de contraste según el modo de tema objetivo
        if (isDarkThemeTarget) {
            // En tema oscuro, el 'on-color' debe ser CLARO (tendiendo a blanco)
            primaryOnColor = Color.WHITE
        } else {
            // En tema claro, el 'on-color' debe ser OSCURO (tendiendo a negro)
            primaryOnColor = Color.BLACK
        }

        // Realizar la mezcla lineal de los componentes RGB para aplicar el tinte
        val blendedR = (Color.red(primaryOnColor) * (1 - tintAmount) + Color.red(baseColor) * tintAmount).roundToInt().coerceIn(0, 255)
        val blendedG = (Color.green(primaryOnColor) * (1 - tintAmount) + Color.green(baseColor) * tintAmount).roundToInt().coerceIn(0, 255)
        val blendedB = (Color.blue(primaryOnColor) * (1 - tintAmount) + Color.blue(baseColor) * tintAmount).roundToInt().coerceIn(0, 255)

        return String.format("#%02X%02X%02X", blendedR, blendedG, blendedB)
    }

    /**
     * Oscurece un color hexadecimal dado multiplicando sus componentes RGB.
     * Este método es simple y produce una versión más oscura del color original,
     * adecuada para usar como color base en modo oscuro.
     *
     * @param colorHex El color hexadecimal a oscurecer.
     * @param factor El factor de oscurecimiento (ej. 0.6 para 60% del brillo original).
     * @return El color hexadecimal oscurecido.
     */
    private fun darkenColorHex(colorHex: String, factor: Float = 0.6f): String {
        val colorInt = Color.parseColor(colorHex)
        val r = (Color.red(colorInt) * factor).roundToInt().coerceIn(0, 255)
        val g = (Color.green(colorInt) * factor).roundToInt().coerceIn(0, 255)
        val b = (Color.blue(colorInt) * factor).roundToInt().coerceIn(0, 255)
        return String.format("#%02X%02X%02X", r, g, b)
    }

    val staticGenres = listOf(
        // Reds & Pinks - Muted and Tonal
        Genre(
            id = "rock", name = "Rock",
            lightColorHex = "#C56262", // Muted Red
            onLightColorHex = getTintedOnColorHex("#C56262", isDarkThemeTarget = false), // Para modo claro, on-color será oscuro tintado
            darkColorHex = darkenColorHex("#C56262"),
            onDarkColorHex = getTintedOnColorHex(darkenColorHex("#C56262"), isDarkThemeTarget = true) // Para modo oscuro, on-color será claro tintado
        ),
        Genre(
            id = "pop", name = "Pop",
            lightColorHex = "#F080AC", // Softer Pink
            onLightColorHex = getTintedOnColorHex("#F080AC", isDarkThemeTarget = false),
            darkColorHex = darkenColorHex("#F080AC"),
            onDarkColorHex = getTintedOnColorHex(darkenColorHex("#F080AC"), isDarkThemeTarget = true)
        ),
        Genre(
            id = "punk", name = "Punk",
            lightColorHex = "#D96676", // Muted Crimson
            onLightColorHex = getTintedOnColorHex("#D96676", isDarkThemeTarget = false),
            darkColorHex = darkenColorHex("#D96676"),
            onDarkColorHex = getTintedOnColorHex(darkenColorHex("#D96676"), isDarkThemeTarget = true)
        ),
        Genre(
            id = "reggaeton", name = "Reggaeton",
            lightColorHex = "#E97EA8", // Softer Deep Pink
            onLightColorHex = getTintedOnColorHex("#E97EA8", isDarkThemeTarget = false),
            darkColorHex = darkenColorHex("#E97EA8"),
            onDarkColorHex = getTintedOnColorHex(darkenColorHex("#E97EA8"), isDarkThemeTarget = true)
        ),
        Genre(
            id = "salsa", name = "Salsa",
            lightColorHex = "#F28C6C", // Muted Tomato/OrangeRed
            onLightColorHex = getTintedOnColorHex("#F28C6C", isDarkThemeTarget = false),
            darkColorHex = darkenColorHex("#F28C6C"),
            onDarkColorHex = getTintedOnColorHex(darkenColorHex("#F28C6C"), isDarkThemeTarget = true)
        ),
        Genre(
            id = "bachata", name = "Bachata",
            lightColorHex = "#DB7093", // Paler Violet Red
            onLightColorHex = getTintedOnColorHex("#DB7093", isDarkThemeTarget = false),
            darkColorHex = darkenColorHex("#DB7093"),
            onDarkColorHex = getTintedOnColorHex(darkenColorHex("#DB7093"), isDarkThemeTarget = true)
        ),

        // Oranges & Yellows - Tonal and Warm
        Genre(
            id = "country", name = "Country",
            lightColorHex = "#D8894E", // Muted Brownish Orange
            onLightColorHex = getTintedOnColorHex("#D8894E", isDarkThemeTarget = false),
            darkColorHex = darkenColorHex("#D8894E"),
            onDarkColorHex = getTintedOnColorHex(darkenColorHex("#D8894E"), isDarkThemeTarget = true)
        ),
        Genre(
            id = "indie", name = "Indie",
            lightColorHex = "#FA8A5F", // Softer Coral
            onLightColorHex = getTintedOnColorHex("#FA8A5F", isDarkThemeTarget = false),
            darkColorHex = darkenColorHex("#FA8A5F"),
            onDarkColorHex = getTintedOnColorHex(darkenColorHex("#FA8A5F"), isDarkThemeTarget = true)
        ),
        Genre(
            id = "latin", name = "Latin",
            lightColorHex = "#FFA040", // Softer Orange Red
            onLightColorHex = getTintedOnColorHex("#FFA040", isDarkThemeTarget = false),
            darkColorHex = darkenColorHex("#FFA040"),
            onDarkColorHex = getTintedOnColorHex(darkenColorHex("#FFA040"), isDarkThemeTarget = true)
        ),
        Genre(
            id = "merengue", name = "Merengue",
            lightColorHex = "#FFBB60", // Muted Orange
            onLightColorHex = getTintedOnColorHex("#FFBB60", isDarkThemeTarget = false),
            darkColorHex = darkenColorHex("#FFBB60"),
            onDarkColorHex = getTintedOnColorHex(darkenColorHex("#FFBB60"), isDarkThemeTarget = true)
        ),

        // Greens - Natural and Tonal
        Genre(
            id = "hip_hop", name = "Hip Hop",
            lightColorHex = "#67C067", // Softer Lime Green
            onLightColorHex = getTintedOnColorHex("#67C067", isDarkThemeTarget = false),
            darkColorHex = darkenColorHex("#67C067"),
            onDarkColorHex = getTintedOnColorHex(darkenColorHex("#67C067"), isDarkThemeTarget = true)
        ),
        Genre(
            id = "reggae", name = "Reggae",
            lightColorHex = "#58A058", // Muted Forest Green
            onLightColorHex = getTintedOnColorHex("#58A058", isDarkThemeTarget = false),
            darkColorHex = darkenColorHex("#58A058"),
            onDarkColorHex = getTintedOnColorHex(darkenColorHex("#58A058"), isDarkThemeTarget = true)
        ),
        Genre(
            id = "folk_acoustic", name = "Folk & Acoustic",
            lightColorHex = "#90C090", // Desaturated Dark Sea Green
            onLightColorHex = getTintedOnColorHex("#90C090", isDarkThemeTarget = false),
            darkColorHex = darkenColorHex("#90C090"),
            onDarkColorHex = getTintedOnColorHex(darkenColorHex("#90C090"), isDarkThemeTarget = true)
        ),

        // Blues & Cyans - Calm and Tonal
        Genre(
            id = "jazz", name = "Jazz",
            lightColorHex = "#7358D4", // Muted Indigo/SlateBlue
            onLightColorHex = getTintedOnColorHex("#7358D4", isDarkThemeTarget = false),
            darkColorHex = darkenColorHex("#7358D4"),
            onDarkColorHex = getTintedOnColorHex(darkenColorHex("#7358D4"), isDarkThemeTarget = true)
        ),
        Genre(
            id = "electronic", name = "Electronic",
            lightColorHex = "#57B8BB", // Muted Dark Turquoise
            onLightColorHex = getTintedOnColorHex("#57B8BB", isDarkThemeTarget = false),
            darkColorHex = darkenColorHex("#57B8BB"),
            onDarkColorHex = getTintedOnColorHex(darkenColorHex("#57B8BB"), isDarkThemeTarget = true)
        ),
        Genre(
            id = "blues", name = "Blues",
            lightColorHex = "#5050A0", // Muted Midnight Blue
            onLightColorHex = getTintedOnColorHex("#5050A0", isDarkThemeTarget = false),
            darkColorHex = darkenColorHex("#5050A0"),
            onDarkColorHex = getTintedOnColorHex(darkenColorHex("#5050A0"), isDarkThemeTarget = true)
        ),
        Genre(
            id = "alternative", name = "Alternative",
            lightColorHex = "#6A9EC2", // Muted Steel Blue
            onLightColorHex = getTintedOnColorHex("#6A9EC2", isDarkThemeTarget = false),
            darkColorHex = darkenColorHex("#6A9EC2"),
            onDarkColorHex = getTintedOnColorHex(darkenColorHex("#6A9EC2"), isDarkThemeTarget = true)
        ),

        // Purples - Tonal and Rich
        Genre(
            id = "classical", name = "Classical",
            lightColorHex = "#9370DB", // Medium Purple
            onLightColorHex = getTintedOnColorHex("#9370DB", isDarkThemeTarget = false),
            darkColorHex = darkenColorHex("#9370DB"),
            onDarkColorHex = getTintedOnColorHex(darkenColorHex("#9370DB"), isDarkThemeTarget = true)
        ),
        Genre(
            id = "rnb_soul", name = "R&B / Soul",
            lightColorHex = "#B366CF", // Muted Orchid
            onLightColorHex = getTintedOnColorHex("#B366CF", isDarkThemeTarget = false),
            darkColorHex = darkenColorHex("#B366CF"),
            onDarkColorHex = getTintedOnColorHex(darkenColorHex("#B366CF"), isDarkThemeTarget = true)
        ),

        // Darks/Greys - Tonal
        Genre(
            id = "metal", name = "Metal",
            lightColorHex = "#607D8B", // Blue Grey
            onLightColorHex = getTintedOnColorHex("#607D8B", isDarkThemeTarget = false),
            darkColorHex = darkenColorHex("#607D8B"),
            onDarkColorHex = getTintedOnColorHex(darkenColorHex("#607D8B"), isDarkThemeTarget = true)
        )
    )
}
