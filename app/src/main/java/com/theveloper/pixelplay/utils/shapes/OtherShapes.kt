package com.theveloper.pixelplay.utils.shapes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

fun createHexagonShape() = object : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        return Outline.Generic(Path().apply {
            val radius = minOf(size.width, size.height) / 2f
            val angle = 2.0 * Math.PI / 6
            moveTo(size.width / 2f + radius * cos(0.0).toFloat(), size.height / 2f + radius * sin(0.0).toFloat())
            for (i in 1..6) {
                lineTo(size.width / 2f + radius * cos(angle * i).toFloat(), size.height / 2f + radius * sin(angle * i).toFloat())
            }
            close()
        })
    }
}

// Implementaciones similares para createRoundedTriangleShape, createSemiCircleShape
// (Estas pueden ser más complejas dependiendo del diseño exacto que quieras)

// Ejemplo simple de triángulo redondeado (tendrías que ajustarlo)
fun createRoundedTriangleShape() = object : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Generic(Path().apply {
            val path = Path()
            path.moveTo(size.width / 2f, 0f)
            path.lineTo(size.width, size.height)
            path.lineTo(0f, size.height)
            path.close()

            // Para redondear las esquinas, podrías usar CornerPathEffect en un Modifier.drawBehind,
            // o construir la forma con arcos y líneas. Clipping con Shape solo recorta.
            // Una forma simple es usar un RoundRect para el clip con radios grandes, pero no es un triángulo real.
            // Para un triángulo redondeado preciso, tendrías que dibujar la forma con arcos.
            // Por ahora, dejaremos el clip simple o necesitarás una implementación más avanzada.

            // Alternativa simple: clip a un rectángulo con esquinas redondeadas
            // return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(16f, 16f)))
            // Esto no es un triángulo. Necesitas una implementación real de forma de triángulo redondeado.
            // Por simplicidad en este ejemplo, usaremos formas más estándar o de la librería.

            // Para el ejemplo, simplemente usaremos un triángulo básico sin redondeo complejo en el clip.
            // Si necesitas triángulos redondeados reales, busca implementaciones más avanzadas.
            moveTo(size.width / 2f, 0f)
            lineTo(size.width, size.height * 0.8f) // Ajuste para que la base no llegue hasta abajo
            lineTo(0f, size.height * 0.8f)
            close()

        })
    }
}

// Ejemplo simple de Semicírculo (tendrías que ajustarlo)
fun createSemiCircleShape() = object : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Generic(Path().apply {
            arcTo(
                rect = Rect(0f, 0f, size.width, size.width), // Un círculo basado en el ancho
                startAngleDegrees = 0f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            lineTo(size.width / 2f, size.width / 2f) // Dibuja una línea hacia el centro si necesitas cerrarlo como pastel
            close() // Opcional: cierra la forma
        })
    }
}

/**
 * Crea una forma de hexágono con esquinas redondeadas.
 */
fun createRoundedHexagonShape(cornerRadius: Dp) = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        return Outline.Generic(Path().apply {
            val width = size.width
            val height = size.height
            val radius = min(width, height) / 2f
            val cornerRadiusPx = with(density) { cornerRadius.toPx() }

            // Puntos del hexágono sin redondear
            val points = (0..5).map { i ->
                val angle = PI / 3 * i
                Offset(
                    x = width / 2f + radius * cos(angle).toFloat(),
                    y = height / 2f + radius * sin(angle).toFloat()
                )
            }

            // Movemos al primer punto con un offset para empezar el arco
            moveTo(points[0].x + cornerRadiusPx * cos(PI / 3.0).toFloat(), points[0].y + cornerRadiusPx * sin(PI / 3.0).toFloat())

            for (i in 0..5) {
                val p1 = points[i]
                val p2 = points[(i + 1) % 6]
                val p3 = points[(i + 2) % 6]

                // Línea hacia el punto de inicio del arco
                lineTo(p2.x - cornerRadiusPx * cos(PI / 3.0).toFloat(), p2.y - cornerRadiusPx * sin(PI / 3.0).toFloat())

                // Arco en la esquina
                arcTo(
                    rect = Rect(
                        left = p2.x - cornerRadiusPx,
                        top = p2.y - cornerRadiusPx,
                        right = p2.x + cornerRadiusPx,
                        bottom = p2.y + cornerRadiusPx
                    ),
                    startAngleDegrees = (i * 60 + 30).toFloat(), // Ángulo de inicio del arco
                    sweepAngleDegrees = 60f, // Ángulo del arco
                    forceMoveTo = false
                )
            }
            close()
        })
    }
}

/**
 * Crea una forma de triángulo con esquinas redondeadas.
 * Implementación simple para clipping.
 */
fun createRoundedTriangleShape(cornerRadius: Dp) = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Generic(Path().apply {
            val width = size.width
            val height = size.height
            val cornerRadiusPx = with(density) { cornerRadius.toPx() }

            // Puntos del triángulo
            val p1 = Offset(width / 2f, 0f) // Superior
            val p2 = Offset(width, height) // Inferior derecha
            val p3 = Offset(0f, height) // Inferior izquierda

            // Para simplificar el redondeo en el clip, usaremos arcos.
            // Esto no es un triángulo perfecto con arcos tangentes, sino un enfoque práctico para clipping.

            // Calcula puntos de control para los arcos
            val control12 = Offset(p1.x + (p2.x - p1.x) * 0.8f, p1.y + (p2.y - p1.y) * 0.8f)
            val control23 = Offset(p2.x + (p3.x - p2.x) * 0.2f, p2.y + (p3.y - p2.y) * 0.2f)
            val control31 = Offset(p3.x + (p1.x - p3.x) * 0.8f, p3.y + (p1.y - p3.y) * 0.8f)


            moveTo(p1.x, p1.y + cornerRadiusPx * 2) // Empieza un poco más abajo del vértice superior

            // Arco superior derecha
            quadraticTo(p1.x, p1.y, p1.x + cornerRadiusPx * sqrt(2f), p1.y + cornerRadiusPx * sqrt(2f))
            lineTo(p2.x - cornerRadiusPx * sqrt(2f), p2.y - cornerRadiusPx * sqrt(2f))

            // Arco inferior derecha
            quadraticTo(p2.x, p2.y, p2.x - cornerRadiusPx * sqrt(2f), p2.y + cornerRadiusPx * sqrt(2f))
            lineTo(p3.x + cornerRadiusPx * sqrt(2f), p3.y + cornerRadiusPx * sqrt(2f))

            // Arco inferior izquierda
            quadraticTo(p3.x, p3.y, p3.x + cornerRadiusPx * sqrt(2f), p3.y - cornerRadiusPx * sqrt(2f))
            lineTo(p1.x - cornerRadiusPx * sqrt(2f), p1.y + cornerRadiusPx * sqrt(2f))

            close() // Cierra la forma
        })
    }
}


/**
 * Crea una forma de semicírculo con una base ligeramente redondeada.
 */
fun createSemiCircleShape(cornerRadius: Dp) = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Generic(Path().apply {
            val width = size.width
            val height = size.height
            val radius = width / 2f
            val cornerRadiusPx = with(density) { cornerRadius.toPx() }

            // Arco superior (semicírculo)
            arcTo(
                rect = Rect(0f, 0f, width, width), // Un círculo basado en el ancho
                startAngleDegrees = 0f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )

            // Base (línea con arcos en los extremos)
            val startBaseX = 0f + cornerRadiusPx
            val endBaseX = width - cornerRadiusPx
            val baseY = width / 2f // La base está a la mitad del diámetro del círculo

            lineTo(endBaseX, baseY) // Línea hacia el final de la base

            // Arco inferior derecho
            arcTo(
                rect = Rect(endBaseX - cornerRadiusPx, baseY - cornerRadiusPx, endBaseX + cornerRadiusPx, baseY + cornerRadiusPx),
                startAngleDegrees = 90f,
                sweepAngleDegrees = -90f, // Arco hacia abajo
                forceMoveTo = false
            )

            lineTo(startBaseX, baseY + cornerRadiusPx) // Línea inferior

            // Arco inferior izquierdo
            arcTo(
                rect = Rect(startBaseX - cornerRadiusPx, baseY - cornerRadiusPx, startBaseX + cornerRadiusPx, baseY + cornerRadiusPx),
                startAngleDegrees = 180f,
                sweepAngleDegrees = -90f, // Arco hacia abajo
                forceMoveTo = false
            )

            close() // Cierra la forma
        })
    }
}