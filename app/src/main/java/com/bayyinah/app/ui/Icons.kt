package com.bayyinah.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The handful of icons the app needs, defined inline (Material Symbols paths, Apache-2.0)
 * so we don't pull in the large material-icons-extended artifact.
 */
object AppIcons {
    val Play = icon("M8,5v14l11,-7z")
    val Pause = icon("M6,19h4L10,5L6,5v14zM14,5v14h4L18,5h-4z")
    val Repeat = icon("M7,7h10v3l4,-4 -4,-4v3L5,6v6h2L7,7zM17,17L7,17v-3l-4,4 4,4v-3h12v-6h-2v4z")
    val ChevronLeft = icon("M15.41,7.41L14,6l-6,6 6,6 1.41,-1.41L10.83,12z")
    val ChevronRight = icon("M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z")
    val Close = icon("M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z")
    val Listen = icon("M7,18h2L9,6L7,6v12zM11,22h2L13,2h-2v20zM3,14h2v-4L3,10v4zM15,18h2L17,6h-2v12zM19,10v4h2v-4h-2z")
    val Camera = icon("M12,15.2a3.2,3.2 0,1 0,0 -6.4a3.2,3.2 0,1 0,0 6.4zM9,2L7.17,4L4,4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2L22,6c0,-1.1 -0.9,-2 -2,-2h-3.17L15,2L9,2z")

    val Search = icon("M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z")
    val Book = icon("M18,2H6c-1.1,0 -2,0.9 -2,2v16c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM6,4h5v8l-2.5,-1.5L6,12V4z")
    val Back = icon("M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z")
    val FlashOn = icon("M7,2v11h3v9l7,-12h-4l4,-8z")
    val FlashOff = icon("M3.27,3L2,4.27l5,5V13h3v9l3.58,-6.14L17.73,20 19,18.73 3.27,3zM17,10h-4l4,-8H7v2.18l8.46,8.46L17,10z")
    val Clear = Close
    val ChevronDown = icon("M16.59,8.59L12,13.17 7.41,8.59 6,10l6,6 6,-6z")

    private fun icon(path: String): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
            .build()
}

/** Western → Arabic-Indic digits for the ayah badge (١١٤). */
fun Int.toArabicDigits(): String = toString().map { '٠' + (it - '0') }.joinToString("")
