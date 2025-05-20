package com.theveloper.pixelplay.data.model

import java.io.File

data class DirectoryItem(
    val path: String,
    var isAllowed: Boolean
) {
    val displayName: String
        get() = File(path).name.ifEmpty { path } // Muestra el nombre de la carpeta o el path si es la raíz
}