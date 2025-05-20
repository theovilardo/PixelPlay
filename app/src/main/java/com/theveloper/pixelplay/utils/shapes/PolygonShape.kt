package com.theveloper.pixelplay.utils.shapes

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin


/**
 * Shape describing Polygons
 *
 * Note: The shape draws within the minimum of provided width and height so can't be used to create stretched shape.
 *
 * @param sides number of sides.
 * @param rotation value between 0 - 360
 */
class PolygonShape(sides: Int, private val rotation: Float = 0f) : Shape {

    private companion object {
        const val TWO_PI = 2 * PI
    }

    private val STEPCOUNT = ((TWO_PI) / sides)

    private val rotationDegree = (PI / 180) * rotation


    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline = Outline.Generic(Path().apply {

        val r = min(size.height, size.width) * .5f

        val xCenter = size.width * .5f
        val yCenter = size.height * .5f

        moveTo(xCenter, yCenter)

        var t = -rotationDegree

        while (t <= TWO_PI) {
            val x = r * cos(t)
            val y = r * sin(t)
            lineTo((x + xCenter).toFloat(), (y + yCenter).toFloat())

            t += STEPCOUNT
        }

        val x = r * cos(t)
        val y = r * sin(t)
        lineTo((x + xCenter).toFloat(), (y + yCenter).toFloat())
    })
}