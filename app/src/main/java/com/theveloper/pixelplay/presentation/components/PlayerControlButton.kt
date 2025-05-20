package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.ui.theme.PixelPlayWhite

@Composable
fun PlayerControlButton(
    icon: Int,
    description: String,
    onClick: () -> Unit,
    isMain: Boolean = false,
    tint: Color = Color.White,
    backgroundColor: Color = Color.Transparent
) {
    Box(
        modifier = Modifier
            .size(if (isMain) 64.dp else 48.dp)
            .background(
                color = if (isMain) backgroundColor else Color.Transparent,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = description,
                tint = tint,
                modifier = Modifier.size(if (isMain) 32.dp else 24.dp)
            )
        }
    }
}

//@Composable
//fun PlayerControlButton(
//    icon: Int,
//    description: String,
//    onClick: () -> Unit,
//    isMain: Boolean = false,
//    accentColor: Color? = null
//) {
//    val size = if (isMain) 72.dp else 56.dp
//    val iconSize = if (isMain) 36.dp else 28.dp
//
//    // Usar colores del tema o color extraído del álbum
//    val backgroundColor = if (isMain) {
//        accentColor ?: MaterialTheme.colorScheme.primary
//    } else {
//        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
//    }
//
//    val iconColor = if (isMain) {
//        PixelPlayWhite // Siempre blanco para mejor contraste
//    } else {
//        MaterialTheme.colorScheme.onSurface
//    }
//
//    IconButton(
//        onClick = onClick,
//        modifier = Modifier
//            .size(size)
//            .clip(CircleShape)
//            .background(backgroundColor)
//    ) {
//        Icon(
//            painter = painterResource(icon),
//            contentDescription = description,
//            modifier = Modifier.size(iconSize),
//            tint = iconColor
//        )
//    }
//}


//@Composable
//fun PlayerControlButton( // Misma implementación
//    icon: Int,
//    description: String,
//    onClick: () -> Unit,
//    isMain: Boolean = false
//) { /* ... (igual que antes, pero usando los nuevos colores del tema si es necesario) ... */
//    val size = if (isMain) 72.dp else 56.dp
//    val iconSize = if (isMain) 36.dp else 28.dp
//    // Usar colores del tema para consistencia
//    val backgroundColor = if (isMain) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
//    val iconColor = if (isMain) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
//
//    IconButton(
//        onClick = onClick,
//        modifier = Modifier
//            .size(size)
//            .clip(CircleShape)
//            .background(backgroundColor)
//    ) {
//        Icon(
//            painter = painterResource(icon),
//            contentDescription = description,
//            modifier = Modifier.size(iconSize),
//            tint = iconColor
//        )
//    }
//}