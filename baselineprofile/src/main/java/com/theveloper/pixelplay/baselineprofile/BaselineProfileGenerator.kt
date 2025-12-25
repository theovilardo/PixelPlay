package com.theveloper.pixelplay.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.StaleObjectException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        val packageName = "com.theveloper.pixelplay"
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        try {
            rule.collect(
                packageName = packageName,
                includeInStartupProfile = true,
                maxIterations = 3
            ) {
                // 1. Permisos
                device.executeShellCommand("pm grant $packageName android.permission.POST_NOTIFICATIONS")
                device.executeShellCommand("appops set $packageName MANAGE_EXTERNAL_STORAGE allow")

                // 2. Inicio
                device.executeShellCommand("am start -n $packageName/.MainActivity --ez is_benchmark true")
                device.wait(Until.hasObject(By.pkg(packageName).depth(0)), 10000)

                handlePermissionDialogs()
                handleOnboarding()

                // 3. Player (inglés/español)
                val queueSelector = By.desc(Pattern.compile(".*(Queue|Cola|Siguiente).*", Pattern.CASE_INSENSITIVE))
                val coverSelector = By.desc(Pattern.compile(".*(Carátula|Cover|Album Art).*", Pattern.CASE_INSENSITIVE))

                if (!device.wait(Until.hasObject(queueSelector), 10000)) {
                    device.findObject(coverSelector)?.click()
                    device.wait(Until.hasObject(queueSelector), 5000)
                }

                interactWithPlayerSheet(queueSelector)

                // 4. Tabs
                device.pressBack()
                device.waitForIdle()

                val tabs = listOf("Search|Buscar", "Library|Biblioteca", "Home|Inicio")
                tabs.forEach { term ->
                    val sel = By.text(Pattern.compile(term, Pattern.CASE_INSENSITIVE))
                    try {
                        val obj = device.wait(Until.findObject(sel), 3000)
                        if (obj?.isClickable == true) obj.click() else obj?.parent?.click()
                        device.waitForIdle(1000)
                    } catch (e: Exception) {}
                }

                device.pressHome()
                device.waitForIdle(2000)
            }
        } catch (e: Exception) {
            val deviceManual = UiDevice.getInstance(instrumentation)
            if (e.message?.contains("flush profiles") == true || e is IllegalStateException || e is StaleObjectException) {

                android.util.Log.e("BaselineProfileGenerator", "FALLO DETECTADO. INICIANDO RESCATE MULTI-RUTA...")

                val srcPath = "/data/misc/profman/$packageName-primary.prof.txt"

                // Intentamos 3 rutas diferentes por si una falla por permisos
                val paths = listOf(
                    "/storage/emulated/0/Download/baseline-rescue.txt",
                    "/sdcard/baseline-rescue.txt",
                    "${instrumentation.targetContext.getExternalFilesDir(null)}/baseline-rescue.txt"
                )

                paths.forEach { dest ->
                    deviceManual.executeShellCommand("cp $srcPath $dest")
                }

                android.util.Log.i("BaselineProfileGenerator", "--------------------------------------------------------")
                android.util.Log.i("BaselineProfileGenerator", "SI EL ARCHIVO NO SE GENERÓ AUTOMÁTICAMENTE, PRUEBA ESTOS COMANDOS:")
                android.util.Log.i("BaselineProfileGenerator", "OPCIÓN A: adb pull ${paths[0]} ./baseline-prof.txt")
                android.util.Log.i("BaselineProfileGenerator", "OPCIÓN B: adb pull ${paths[1]} ./baseline-prof.txt")
                android.util.Log.i("BaselineProfileGenerator", "OPCIÓN C: adb pull ${paths[2]} ./baseline-prof.txt")
                android.util.Log.i("BaselineProfileGenerator", "--------------------------------------------------------")
            } else {
                throw e
            }
        }
    }

    private fun MacrobenchmarkScope.interactWithPlayerSheet(queueSelector: BySelector) {
        val playPausePattern = Pattern.compile(".*(Play|Pause|Reproducir|Pausa).*", Pattern.CASE_INSENSITIVE)
        repeat(3) {
            try {
                val btn = device.findObject(By.desc(playPausePattern))
                if (btn?.isClickable == true) btn.click() else btn?.parent?.click()
                device.waitForIdle(800)
            } catch (e: Exception) { }
        }
        device.pressBack()
    }

    private fun MacrobenchmarkScope.handlePermissionDialogs() {
        val pattern = Pattern.compile("Allow|While using the app|Permitir|Aceptar", Pattern.CASE_INSENSITIVE)
        repeat(3) {
            try { device.findObject(By.text(pattern))?.click() } catch (e: Exception) {}
            device.waitForIdle(500)
        }
    }

    private fun MacrobenchmarkScope.handleOnboarding() {
        val pattern = Pattern.compile("Next|Continue|Skip|Done|Get Started|Siguiente|Continuar|Omitir", Pattern.CASE_INSENSITIVE)
        repeat(5) {
            try { device.findObject(By.text(pattern))?.click() } catch (e: Exception) {}
            device.waitForIdle(1000)
        }
    }
}