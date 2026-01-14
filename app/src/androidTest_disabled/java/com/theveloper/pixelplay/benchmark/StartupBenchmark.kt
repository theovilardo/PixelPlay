package com.theveloper.pixelplay.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE_NAME = "com.theveloper.pixelplay"
private const val ITERATIONS = 5
private const val STARTUP_TIMEOUT_MS = 15_000L

@LargeTest
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupCold() = startup(StartupMode.COLD)

    @Test
    fun startupWarm() = startup(StartupMode.WARM)

    @Test
    fun startupHot() = startup(StartupMode.HOT)

    private fun startup(startupMode: StartupMode) = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric()),
        iterations = ITERATIONS,
        startupMode = startupMode,
        setupBlock = {
            pressHome()
        }
    ) { // El bloque 'measure' es la acción a medir
        startActivityAndWait()
        // Opcional: Añadir interacciones básicas después del arranque si son críticas
        // Por ejemplo, esperar a que aparezca la lista de canciones y hacer scroll
        // Esto haría que el perfil de base también cubra estas interacciones iniciales.
        // Por ahora, nos centramos en el tiempo de arranque puro.
    }

    @Test
    fun scrollLibraryAndNavigate() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric()), // Podríamos añadir FrameTimingMetric aquí
        iterations = ITERATIONS,
        startupMode = StartupMode.COLD, // O WARM si se prefiere
        setupBlock = {
            pressHome()
        }
    ) {
        startActivityAndWait()

        // Esperar a que la LibraryScreen (o su contenido) esté visible
        // Esto es un ejemplo, el selector real dependerá de la UI
        // Asumimos que la pestaña "SONGS" es la primera y tiene una lista.
        // Puede que necesitemos selectores más robustos (resourceId, contentDescription)

        // Esperar a que la pestaña de canciones (o su contenido) esté disponible.
        // Este es un selector de ejemplo, puede necesitar ajuste.
        // Si `LibraryActionRow` es un ID único, usarlo.
        // O un elemento dentro de la LazyColumn de canciones.
        // Por ahora, simplemente esperamos un tiempo corto y hacemos scroll.
        // En un test real, usaríamos selectores de UI Automator más precisos.
        // Reemplazar "lazy_column_songs_tag" con el testTag real de la LazyColumn en LibrarySongsTab
        val songList = device.findObject(By.res(PACKAGE_NAME, "lazy_column_songs_tag"))
        // Alternativamente, si no hay testTag, buscar por descripción o un elemento hijo conocido.
        // Ejemplo: device.wait(Until.hasObject(By.text("ALGUNA_CANCION_CONOCIDA")), STARTUP_TIMEOUT_MS)

        if (songList != null && songList.wait(Until.exists(), 5000L)) { // Espera a que exista
            songList.fling(Direction.DOWN)
            Thread.sleep(500) // Pausa después del scroll
            songList.fling(Direction.UP)
            Thread.sleep(500)
        } else {
            // Fallback si no se encuentra la lista específica
            val mainContainer = device.findObject(By.pkg(PACKAGE_NAME).depth(0))
            mainContainer?.fling(Direction.DOWN)
            Thread.sleep(500)
            mainContainer?.fling(Direction.UP)
            Thread.sleep(500)
        }

        // Opcional: Navegar a otra pantalla, como la de búsqueda
        // val searchButton = device.findObject(By.desc("Search")) // Asumiendo content description
        // searchButton?.click()
        // device.wait(Until.hasObject(By.res(PACKAGE_NAME, "search_bar_tag")), STARTUP_TIMEOUT_MS)
    }
}

/**
 * Clase para generar Baseline Profiles.
 * Ejecutar este test en un dispositivo físico (rooteado o userdebug) o emulador.
 * El perfil generado se encontrará en la salida del test y deberá copiarse a
 * app/src/main/baseline-prof.txt
 *
 * Es crucial que los selectores de UI (By.res, By.text, etc.) coincidan con
 * los elementos reales de la UI de la aplicación (testTags, contentDescriptions, resource IDs).
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun generateBaselineProfile() {
        rule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric()), // Se puede omitir para BaselineProfileMode.Disable, pero útil para verificar
            iterations = 3, // Menos iteraciones para la generación de perfiles es común
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.Ignore(), // Importante para BaselineProfileGenerator
            baselineProfileMode = BaselineProfileMode.Require(), // Para forzar la generación
            setupBlock = {
                pressHome()
            }
        ) {
            startActivityAndWait()
            // Aquí se replican las acciones del test `scrollLibraryAndNavigate`
            // o cualquier otro flujo crítico que se quiera incluir en el perfil.

            // Esperar a que la UI esté lista. Usar un selector más fiable que Thread.sleep.
            // Ejemplo: Esperar a que el contenedor de pestañas de la biblioteca esté presente.
            // Reemplazar "tab_row_library" con el testTag o resourceId real.
            device.wait(Until.hasObject(By.res(PACKAGE_NAME, "tab_row_library")), STARTUP_TIMEOUT_MS)
            Thread.sleep(2000) // Dar tiempo adicional para que el contenido se cargue después de que el contenedor esté presente.

            // Scroll en la lista de canciones (ajustar selector)
            // Reemplazar "lazy_column_songs_tag" con el testTag real.
            val songList = device.findObject(By.res(PACKAGE_NAME, "lazy_column_songs_tag"))
            if (songList != null && songList.wait(Until.exists(), 5000L)) {
                songList.fling(Direction.DOWN)
                Thread.sleep(500) // Pequeña pausa para permitir que la UI se asiente
                songList.fling(Direction.UP)
                Thread.sleep(500)
            } else {
                // Fallback si la lista específica no se encuentra (menos ideal)
                val mainContainer = device.findObject(By.pkg(PACKAGE_NAME).depth(0))
                mainContainer?.fling(Direction.DOWN)
                Thread.sleep(500)
            }
            // Añadir más interacciones aquí si es necesario.
            // Por ejemplo, cambiar de pestaña, abrir el reproductor, etc.
            // Ejemplo: Hacer clic en la pestaña de Álbumes (ajustar selector)
            // val albumsTab = device.findObject(By.text("ALBUMS"))
            // albumsTab?.click()
            // Thread.sleep(1000) // Esperar a que cargue la pestaña de álbumes
            // val albumList = device.findObject(By.res(PACKAGE_NAME, "lazy_grid_albums_tag")) // Ajustar selector
            // albumList?.fling(Direction.DOWN)
            // Thread.sleep(500)
        }
    }
}
