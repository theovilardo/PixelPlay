package com.theveloper.pixelplay.data.database

import androidx.compose.material3.ColorScheme
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

// Para simplificar, almacenaremos los colores como Strings hexadecimales.
// Almacena los valores de color para UN esquema (sea light o dark)
data class StoredColorSchemeValues(
    val primary: String, val onPrimary: String, val primaryContainer: String, val onPrimaryContainer: String,
    val secondary: String, val onSecondary: String, val secondaryContainer: String, val onSecondaryContainer: String,
    val tertiary: String, val onTertiary: String, val tertiaryContainer: String, val onTertiaryContainer: String,
    val background: String, val onBackground: String, val surface: String, val onSurface: String,
    val surfaceVariant: String, val onSurfaceVariant: String, val error: String, val onError: String,
    val outline: String, val errorContainer: String, val onErrorContainer: String,
    val inversePrimary: String, val inverseSurface: String, val inverseOnSurface: String,
    val surfaceTint: String, val outlineVariant: String, val scrim: String
    // Añade aquí todos los roles de ColorScheme que quieras persistir
)
//data class StoredColorScheme(
//    // Light Theme Colors
//    val primaryLight: String, val onPrimaryLight: String, val primaryContainerLight: String, val onPrimaryContainerLight: String,
//    val secondaryLight: String, val onSecondaryLight: String, val secondaryContainerLight: String, val onSecondaryContainerLight: String,
//    val tertiaryLight: String, val onTertiaryLight: String, val tertiaryContainerLight: String, val onTertiaryContainerLight: String,
//    val backgroundLight: String, val onBackgroundLight: String, val surfaceLight: String, val onSurfaceLight: String,
//    val surfaceVariantLight: String, val onSurfaceVariantLight: String, val errorLight: String, val onErrorLight: String,
//    val outlineLight: String,
//
//    // Dark Theme Colors
//    val primaryDark: String, val onPrimaryDark: String, val primaryContainerDark: String, val onPrimaryContainerDark: String,
//    val secondaryDark: String, val onSecondaryDark: String, val secondaryContainerDark: String, val onSecondaryContainerDark: String,
//    val tertiaryDark: String, val onTertiaryDark: String, val tertiaryContainerDark: String, val onTertiaryContainerDark: String,
//    val backgroundDark: String, val onBackgroundDark: String, val surfaceDark: String, val onSurfaceDark: String,
//    val surfaceVariantDark: String, val onSurfaceVariantDark: String, val errorDark: String, val onErrorDark: String,
//    val outlineDark: String
//)

@Entity(tableName = "album_art_themes")
data class AlbumArtThemeEntity(
    @PrimaryKey val albumArtUriString: String,
    @Embedded(prefix = "light_") val lightThemeValues: StoredColorSchemeValues,
    @Embedded(prefix = "dark_") val darkThemeValues: StoredColorSchemeValues
)

//@Entity(tableName = "album_art_themes")
//data class AlbumArtThemeEntity(
//    @PrimaryKey val albumArtUriString: String,
//    @Embedded val lightThemeColors: StoredColorScheme,
//    @Embedded(prefix = "dark_") val darkThemeColors: StoredColorScheme
//)