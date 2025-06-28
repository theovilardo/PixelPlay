package com.theveloper.pixelplay.benchmark // Asegúrate que el package name coincida con la ubicación en tu módulo baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMacrobenchmarkApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE_NAME = "com.theveloper.pixelplay"
private const val ITERATIONS = 3 // Unas pocas iteraciones son suficientes para generar el perfil
private const val TIMEOUT_MS = 10_000L // Timeout para esperar elementos de UI

@OptIn(ExperimentalMacrobenchmarkApi::class)
@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun generateBaselineProfile() {
        rule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric()), // Opcional para generación, pero bueno para verificar
            iterations = ITERATIONS,
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.Ignore(), // Crucial para la generación de perfiles
            baselineProfileMode = BaselineProfileMode.Require(), // Para forzar la generación
            setupBlock = {
                pressHome()
            }
        ) {
            // Iniciar la actividad principal y esperar a que se cargue
            startActivityAndWait()
            device.waitForIdle(TIMEOUT_MS) // Esperar a que la UI esté inactiva

            // --- Flujo de Usuario Crítico ---

            // 1. Esperar y hacer scroll en la lista de Canciones
            waitForAndScrollUiElement("LibraryScreen_SongList", Direction.DOWN)

            // 2. Cambiar a la pestaña de Álbumes
            clickUiElement("LibraryScreen_Tab_Albums")
            device.waitForIdle(TIMEOUT_MS)


            // 3. Esperar y hacer scroll en la lista de Álbumes
            waitForAndScrollUiElement("LibraryScreen_AlbumGrid", Direction.DOWN)

            // 4. Cambiar a la pestaña de Artistas
            clickUiElement("LibraryScreen_Tab_Artists")
            device.waitForIdle(TIMEOUT_MS)

            // 5. Esperar y hacer scroll en la lista de Artistas
            waitForAndScrollUiElement("LibraryScreen_ArtistList", Direction.DOWN)

            // 6. Volver a la pestaña de canciones para posible interacción con el player
            clickUiElement("LibraryScreen_Tab_Songs")
            device.waitForIdle(TIMEOUT_MS)
            waitForUiElement("LibraryScreen_SongList") // Esperar a que la lista de canciones vuelva a estar presente


            // 7. (Opcional pero recomendado) Abrir y colapsar el reproductor
            //    Asumimos que el primer elemento de la lista de canciones es clickeable.
            //    Necesitaríamos un testTag en el primer item o una forma de seleccionarlo.
            //    Por ahora, si la lista de canciones está presente, intentamos hacer clic en un hijo.
            val songList = device.findObject(By.res(PACKAGE_NAME, "LibraryScreen_SongList"))
            if (songList != null && songList.childCount > 0) {
                // Intentar hacer clic en el primer elemento visible (esto es un poco genérico)
                // Un testTag específico en el item sería más robusto.
                val firstSongItem = songList.children.firstOrNull()
                firstSongItem?.click()
                device.waitForIdle(TIMEOUT_MS)

                // Esperar a que el reproductor completo sea visible (usando un testTag de un elemento dentro)
                waitForUiElement("UnifiedPlayerSheet_FullPlayer_AlbumArt", timeout = 5000L) // Menor timeout si la apertura es rápida

                // Colapsar el reproductor (simulando un swipe down o clic en botón de colapsar si existe)
                // La forma más sencilla es presionar "back" si el back handler está configurado para colapsar.
                // O simular un swipe hacia abajo en el reproductor.
                // Por ahora, asumimos que un gesto de swipe hacia abajo en el reproductor lo colapsa.
                // Un selector para el área del reproductor completo sería útil aquí.
                val fullPlayerArea = device.findObject(By.res(PACKAGE_NAME, "UnifiedPlayerSheet_FullPlayer_AlbumArt")) // O un contenedor más grande
                fullPlayerArea?.swipe(Direction.DOWN, 1.0f, 2000) // Swipe rápido
                device.waitForIdle(TIMEOUT_MS)

                // Esperar a que el mini-reproductor sea visible
                waitForUiElement("UnifiedPlayerSheet_MiniPlayer", timeout = 5000L)
            }

            // Dejar que la app se asiente un poco al final
            device.waitForIdle(TIMEOUT_MS)
        }
    }
}

// Funciones de utilidad para UiAutomator (pueden ir en el mismo archivo o en uno separado)
fun UiDevice.waitForUiElement(resourceId: String, timeout: Long = TIMEOUT_MS) {
    if (!wait(Until.hasObject(By.res(PACKAGE_NAME, resourceId)), timeout)) {
        // Elemento no encontrado, podría ser un error o el flujo no es como se esperaba.
        // Para la generación de perfiles, a menudo es mejor continuar si es posible.
        println("Advertencia: Elemento con resId '$resourceId' no encontrado después de $timeout ms.")
    }
    waitForIdle(timeout / 2) // Dar tiempo para que la UI se asiente después de encontrar el elemento
}

fun UiDevice.clickUiElement(resourceId: String, timeout: Long = TIMEOUT_MS) {
    val element = findObject(By.res(PACKAGE_NAME, resourceId))
    if (element != null && element.wait(Until.clickable(true), timeout)) {
        element.click()
    } else {
        println("Advertencia: Elemento clickeable con resId '$resourceId' no encontrado o no clickeable.")
    }
    waitForIdle(timeout / 2)
}

fun UiDevice.waitForAndScrollUiElement(resourceId: String, direction: Direction, timeout: Long = TIMEOUT_MS) {
    val element = findObject(By.res(PACKAGE_NAME, resourceId))
    if (element != null && element.wait(Until.scrollable(true), timeout)) {
        element.setGestureMargin(height / 10) // Margen para evitar tocar bordes de pantalla
        element.fling(direction)
        waitForIdle(1000) // Esperar después del fling
        // Opcionalmente, un segundo fling o scroll más pequeño
        // element.scroll(direction, 0.5f)
        // waitForIdle(500)
    } else {
        println("Advertencia: Elemento scrolleable con resId '$resourceId' no encontrado o no scrolleable.")
        // Fallback: intentar un scroll genérico en la pantalla si el elemento específico no se encuentra.
        // Esto es menos preciso pero puede ayudar a cubrir más código.
        val mainContainer = findObject(By.pkg(PACKAGE_NAME).depth(0))
        mainContainer?.fling(direction)
        waitForIdle(1000)
    }
    waitForIdle(timeout / 2)
}

// Nota: La función startActivityAndWait() es proporcionada por MacrobenchmarkRule y se usa dentro del bloque `measureRepeated`.
// No es necesario definirla aquí.
// La función pressHome() también es de MacrobenchmarkRule.
