package com.theveloper.pixelplay.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val STARTUP_TIMEOUT_MS = 15_000L
private const val SHORT_WAIT_MS = 3_000L

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        val targetAppId = "com.theveloper.pixelplay"

        rule.collect(
            packageName = targetAppId,
            includeInStartupProfile = true,
            maxIterations = 1 // Solo una vez para que el dump sea rápido
        ) {
            // 1. Limpieza y Permisos (Igual que antes)
            device.executeShellCommand("pm clear $targetAppId")
            device.executeShellCommand("appops set $targetAppId MANAGE_EXTERNAL_STORAGE allow")
            device.executeShellCommand("pm grant $targetAppId android.permission.POST_NOTIFICATIONS")

            pressHome()

            // 2. Lanzamiento directo a la HomeScreen
            // Usamos el flag is_benchmark que ya configuramos en tu MainActivity
            val startCommand = "am start -n $targetAppId/com.theveloper.pixelplay.MainActivity --ez is_benchmark true"
            device.executeShellCommand(startCommand)

            // 3. Espera mínima
            // Esperamos a que el ReportDrawnWhen que pusimos en la HomeScreen se dispare
            device.wait(Until.hasObject(By.pkg(targetAppId)), 10_000L)

            // 4. Mínima interacción
            Thread.sleep(3000)

            // NO HACEMOS NADA MÁS.
            // Cuanto menos hagamos, más probable es que el dump no falle.

            pressHome()
            Thread.sleep(2000)
        }
    }
}

/**
 * Navega por las pestañas principales re-buscando los elementos en cada paso
 * para evitar StaleObjectException.
 */
private fun androidx.benchmark.macro.MacrobenchmarkScope.exploreMainTabsSafely() {
    val tabs = listOf("Search", "Library", "Home")

    tabs.forEach { tabName ->
        try {
            // Buscamos y clickeamos en el momento
            device.wait(Until.findObject(By.text(tabName)), SHORT_WAIT_MS)?.click()
            device.waitForIdle(2000)

            if (tabName == "Library") {
                scrollPrimaryListsSafely()
            }
        } catch (e: Exception) {
            // Si una pestaña falla, intentamos la siguiente
        }
    }
}

/**
 * Interactúa con el reproductor y sus hojas laterales (Queue, Lyrics, Cast)
 */
private fun androidx.benchmark.macro.MacrobenchmarkScope.openPlayerAndSheetsSafely() {
    try {
        // Intentar abrir el reproductor clickeando la carátula
        val playerTrigger = device.wait(Until.findObject(By.descContains("Carátula")), SHORT_WAIT_MS)
        if (playerTrigger != null) {
            playerTrigger.click()
        } else {
            // Si no hay carátula, intentamos un swipe hacia arriba desde el miniplayer
            val width = device.displayWidth
            val height = device.displayHeight
            device.swipe(width / 2, (height * 0.9).toInt(), width / 2, (height * 0.4).toInt(), 20)
        }

        device.waitForIdle(2000)

        // Hojas laterales: Queue, Lyrics, Cast
        val sheetButtons = listOf("Queue", "Lyrics", "Cast")
        sheetButtons.forEach { btnDesc ->
            device.wait(Until.findObject(By.descContains(btnDesc)), SHORT_WAIT_MS)?.click()
            device.waitForIdle(1500)
            device.pressBack() // Cerramos la hoja para volver al reproductor
            device.waitForIdle(1000)
        }

        device.pressBack() // Colapsar el reproductor final
    } catch (e: Exception) { }
}

/**
 * Realiza scroll sobre cualquier lista activa sin guardar referencias previas
 */
private fun androidx.benchmark.macro.MacrobenchmarkScope.scrollPrimaryListsSafely() {
    try {
        val scrollable = device.findObject(By.scrollable(true))
        if (scrollable != null) {
            scrollable.setGestureMargin(device.displayWidth / 10)
            scrollable.fling(Direction.DOWN)
            device.waitForIdle(1000)
            scrollable.fling(Direction.UP)
        }
    } catch (e: Exception) { }
}

/**
 * Marca el setup inicial como completado en DataStore para que no salten diálogos de bienvenida
 */
private fun markSetupComplete() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val dataStore = PreferenceDataStoreFactory.createWithPath(
        produceFile = { context.preferencesDataStoreFile("settings").absolutePath.toPath() },
    )
    runBlocking {
        dataStore.edit { prefs ->
            prefs[booleanPreferencesKey("initial_setup_done")] = true
        }
    }
    // No cerramos el DataStore manualmente para evitar ClassCastException
}

private fun UiDevice.handlePermissionDialogs() {
    val allowButtons = listOf("Allow", "While using the app", "Permitir", "Continuar", "Aceptar")
    repeat(3) {
        val button = allowButtons.firstNotNullOfOrNull { text -> findObject(By.textContains(text)) }
        button?.click()
    }
}