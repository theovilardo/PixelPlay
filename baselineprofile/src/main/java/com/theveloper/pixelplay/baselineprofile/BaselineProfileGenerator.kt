package com.theveloper.pixelplay.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.UiDevice
import android.util.Log
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
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

        rule.collect(
            packageName = packageName,
            includeInStartupProfile = true,
            maxIterations = 1
        ) {
            try {
                // 1. SETUP & STARTUP
                setupResilientPermissions(packageName)
                pressHome()

                Log.d("BaselineProfileGenerator", "--- INICIANDO PIXELPLAY ---")

                device.executeShellCommand("am start -n $packageName/.MainActivity")
                device.wait(Until.hasObject(By.pkg(packageName)), 15000)
                Thread.sleep(6000) // Tiempo extra para carga inicial

                handlePermissionDialogs()
                handleOnboarding()

                // =================================================================================
                // 1.1. HOME SCREEN EXTENDED FLOW (Daily Mix & Stats)
                // =================================================================================
                runStep("Home Screen Extended Flow") {
                    clickTab("Home|Inicio")
                    Thread.sleep(1000)

                    // Scroll to bottom
                    scrollToListBottom()
                    Thread.sleep(1000)

                    // Daily Mix
                    val dailyMixPattern = Pattern.compile(".*(Daily Mix|Mix Diario).*", Pattern.CASE_INSENSITIVE)
                    val dailyMix = device.wait(Until.findObject(By.text(dailyMixPattern)), 3000)
                        ?: device.wait(Until.findObject(By.desc(dailyMixPattern)), 1000)

                    if (dailyMix != null) {
                        dailyMix.click()
                        Thread.sleep(2500)
                        blindScroll() // Scroll inside Daily Mix
                        device.pressBack()
                        Thread.sleep(1500)
                    } else {
                        Log.w("BaselineProfileGenerator", "Daily Mix element not found")
                    }

                    // Stats
                    val statsPattern = Pattern.compile(".*(Stats|Estadísticas).*", Pattern.CASE_INSENSITIVE)
                    val stats = device.wait(Until.findObject(By.text(statsPattern)), 3000)
                        ?: device.wait(Until.findObject(By.desc(statsPattern)), 1000)

                    if (stats != null) {
                        stats.click()
                        Thread.sleep(2500)
                        blindScroll() // Scroll inside Stats
                        device.pressBack()
                        Thread.sleep(1500)
                    }

                    scrollToTop() // Regresar arriba para continuar flujo
                    Thread.sleep(1000)
                }

                // =================================================================================
                // 2. AJUSTES (Ya funcionando)
                // =================================================================================
                runStep("Settings & Scroll") {
                    val settingsPattern = Pattern.compile(".*(Settings|Configuraci|Ajustes).*", Pattern.CASE_INSENSITIVE)
                    val settingsBtn = device.findObject(By.desc(settingsPattern)) ?: device.findObject(By.text(settingsPattern))
                    settingsBtn?.let {
                        device.click(it.visibleCenter.x, it.visibleCenter.y)
                        Thread.sleep(2000)
                        blindScroll()
                        device.pressBack()
                        Thread.sleep(2000) // Espera vital tras volver
                    }
                }

                // =================================================================================
                // 3. LIBRARY INTERACTION & PLAY (Modified)
                // =================================================================================
                runStep("Library Interaction & Play") {
                    clickTab("Library|Biblioteca")
                    Thread.sleep(1500)

                    // 3-dot Menu Interaction
                    val moreOptionsPattern = Pattern.compile(".*(More options|Más opciones).*", Pattern.CASE_INSENSITIVE)
                    val moreOptions = device.wait(Until.findObject(By.desc(moreOptionsPattern)), 3000)

                    if (moreOptions != null) {
                        moreOptions.click()
                        Thread.sleep(1500)
                        scrollBottomSheetContent() // Scroll SongInfoBottomSheet UP only
                        Thread.sleep(800)
                        device.pressBack() // Close Sheet
                        Thread.sleep(1500) // Wait for sheet to close fully
                    } else {
                        Log.w("BaselineProfileGenerator", "More options menu not found")
                    }

                    // Clic en la primera canción de la lista (Force Play)
                    val midX = device.displayWidth / 2
                    val firstItemY = (device.displayHeight * 0.35).toInt()

                    // Wait safely to ensure we are back on the list
                    Thread.sleep(1000)
                    device.click(midX, firstItemY)
                    Thread.sleep(2500)
                }

                // =================================================================================
                // 4. SEARCH & GENRE FLOW (Modified)
                // =================================================================================
                runStep("Search & Genre Flow") {
                    clickTab("Search|Buscar")
                    Thread.sleep(1500)

                    val unknownPattern = Pattern.compile(".*(Unknown|Desconocido).*", Pattern.CASE_INSENSITIVE)
                    val genreCard = device.wait(Until.findObject(By.text(unknownPattern)), 3000)
                        ?: device.wait(Until.findObject(By.desc(unknownPattern)), 1000)

                    if (genreCard != null) {
                        genreCard.click()
                        Thread.sleep(2000)
                        blindScroll() // Scroll GenreDetail
                        device.pressBack()
                        Thread.sleep(1000)
                    } else {
                        Log.w("BaselineProfileGenerator", "Unknown Genre Card not found")
                    }

                    // Volvemos a Home para tener el miniplayer a la vista
                    clickTab("Home|Inicio")
                    Thread.sleep(1500)
                }

                // =================================================================================
                // 5. LIBRARY PAGER (Ya funcionando excelente)
                // =================================================================================
                runStep("Library Pager") {
                    clickTab("Library|Biblioteca")
                    Thread.sleep(1000)
                    runLibraryPagerSwipeFlow()
                }

                // =================================================================================
                // 6. PLAYER SHEET EXTENDED (Modified)
                // =================================================================================
                runStep("Unified Player Sheet Extended") {
                    // Volver a Home para asegurar visibilidad
                    clickTab("Home|Inicio")
                    Thread.sleep(1500)

                    val playerPattern = Pattern.compile(".*(Player|Reproductor|Mini|Carátula|Cover|Album Art).*", Pattern.CASE_INSENSITIVE)
                    var miniPlayer = device.wait(Until.findObject(By.desc(playerPattern)), 3000)
                        ?: device.wait(Until.findObject(By.text(playerPattern)), 1000)

                    if (miniPlayer == null) {
                        Log.w("BaselineProfileGenerator", "MiniPlayer no detectado por texto, usando clic por zona física.")
                        // Clic en el área central inferior, justo encima de las pestañas
                        device.click(device.displayWidth / 2, (device.displayHeight * 0.88).toInt())
                    } else {
                        device.click(miniPlayer.visibleCenter.x, miniPlayer.visibleCenter.y)
                    }
                    Thread.sleep(3500) // Espera a expansión completa

                    // 1. Carousel Swipe
                    val carouselY = (device.displayHeight * 0.45).toInt()
                    val leftX = (device.displayWidth * 0.2).toInt()
                    val rightX = (device.displayWidth * 0.8).toInt()
                    repeat(2) {
                        device.swipe(rightX, carouselY, leftX, carouselY, 30)
                        Thread.sleep(800)
                        device.swipe(leftX, carouselY, rightX, carouselY, 30)
                        Thread.sleep(800)
                    }

                    // 2. Playback Controls & Wavy Slider Targeting
                    val playPausePattern = Pattern.compile(".*(Play|Pause|Reproducir|Pausar).*", Pattern.CASE_INSENSITIVE)
                    val nextPattern = Pattern.compile(".*(Next|Siguiente).*", Pattern.CASE_INSENSITIVE)
                    val prevPattern = Pattern.compile(".*(Previous|Anterior).*", Pattern.CASE_INSENSITIVE)

                    // Find controls to anchor slider swipe
                    var playButton = device.wait(Until.findObject(By.desc(playPausePattern)), 2000)

                    // Wavy Slider Interaction
                    if (playButton != null) {
                        // Slider is typically above the controls.
                        val sliderY = playButton.visibleBounds.top - (device.displayHeight * 0.12).toInt()
                        device.swipe(leftX, sliderY, rightX, sliderY, 80)
                        Thread.sleep(1000)
                        device.swipe(rightX, sliderY, leftX, sliderY, 80)
                        Thread.sleep(1500)
                    } else {
                        Log.w("BaselineProfileGenerator", "Play/Pause button not found, skipping slider interaction")
                    }

                    // Toggle Controls
                    device.findObject(By.desc(prevPattern))?.click()
                    Thread.sleep(1000)
                    device.findObject(By.desc(nextPattern))?.click()
                    Thread.sleep(1000)

                    // Play/Pause Interaction
                    playButton = device.wait(Until.findObject(By.desc(playPausePattern)), 1000)
                    if (playButton != null) {
                        playButton.click() // Pause
                        Thread.sleep(1200)
                        playButton = device.wait(Until.findObject(By.desc(playPausePattern)), 1000)
                        playButton?.click() // Resume
                        Thread.sleep(1000)
                    }

                    // 4. Queue BottomSheet (Fling Up as requested)
                    val startX = device.displayWidth / 2
                    val startY = (device.displayHeight * 0.92).toInt() // Near bottom
                    val endY = (device.displayHeight * 0.5).toInt()

                    device.swipe(startX, startY, startX, endY, 10) // Fast swipe (fling speed)
                    Thread.sleep(2500) // Wait for sheet to animate

                    // Verify Queue is open
                    val nextUpPattern = Pattern.compile(".*(Next Up|A continuación).*", Pattern.CASE_INSENSITIVE)
                    if (device.wait(Until.hasObject(By.text(nextUpPattern)), 1000)) {
                        repeat(3) {
                            scrollBottomSheetContent()
                            Thread.sleep(600)
                        }
                        device.pressBack() // Close Queue
                        Thread.sleep(1500)
                    }

                    device.pressBack() // Colapsar Player
                    Thread.sleep(1000)
                }

                Log.d("BaselineProfileGenerator", "--- FLUJO FINALIZADO ---")
                pressHome()

            } catch (e: Exception) {
                Log.e("BaselineProfileGenerator", "Error fatal: ${e.toString()}")
                e.printStackTrace()
            } finally {
                recoverBaselineProfile(packageName, instrumentation)
            }
        }
    }

    private fun runStep(name: String, block: () -> Unit) {
        try {
            Log.d("BaselineProfileGenerator", ">> PASO: $name")
            block()
            Log.d("BaselineProfileGenerator", ">> OK")
        } catch (e: Exception) {
            Log.e("BaselineProfileGenerator", ">> FALLÓ $name: ${e.toString()}")
            e.printStackTrace()
        }
    }

    private fun MacrobenchmarkScope.setupResilientPermissions(packageName: String) {
        val permissions = listOf(
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE"
        )
        permissions.forEach {
            try { device.executeShellCommand("pm grant $packageName $it") } catch (ignore: Exception) {}
        }
        device.executeShellCommand("appops set $packageName MANAGE_EXTERNAL_STORAGE allow")
    }

    private fun MacrobenchmarkScope.handlePermissionDialogs() {
        val pattern = Pattern.compile("Allow|Permitir|Aceptar|While using|Mientras", Pattern.CASE_INSENSITIVE)
        repeat(3) {
            device.findObject(By.text(pattern))?.click()
            Thread.sleep(500)
        }
    }

    private fun MacrobenchmarkScope.handleOnboarding() {
        val pattern = Pattern.compile("Next|Continue|Skip|Done|Siguiente|Omitir|Empezar", Pattern.CASE_INSENSITIVE)
        repeat(4) {
            device.findObject(By.text(pattern))?.click()
            Thread.sleep(1000)
        }
    }

    private fun MacrobenchmarkScope.clickTab(tabNamePattern: String) {
        val pattern = Pattern.compile(tabNamePattern, Pattern.CASE_INSENSITIVE)
        val tab = device.findObject(By.desc(pattern)) ?: device.findObject(By.text(pattern))
        tab?.let {
            device.click(it.visibleCenter.x, it.visibleCenter.y)
            Thread.sleep(1500)
        }
    }

    private fun MacrobenchmarkScope.blindScroll() {
        val midX = device.displayWidth / 2
        val bottomY = (device.displayHeight * 0.75).toInt()
        val topY = (device.displayHeight * 0.25).toInt()
        device.swipe(midX, bottomY, midX, topY, 45)
        Thread.sleep(1200)
        device.swipe(midX, topY, midX, bottomY, 45)
        Thread.sleep(1200)
    }

    private fun MacrobenchmarkScope.scrollBottomSheetContent() {
        val midX = device.displayWidth / 2
        val bottomY = (device.displayHeight * 0.75).toInt()
        val topY = (device.displayHeight * 0.25).toInt()
        device.swipe(midX, bottomY, midX, topY, 45)
        Thread.sleep(1200)
    }

    private fun MacrobenchmarkScope.scrollToListBottom() {
        val midX = device.displayWidth / 2
        val bottomY = (device.displayHeight * 0.75).toInt()
        val topY = (device.displayHeight * 0.25).toInt()
        repeat(4) {
            device.swipe(midX, bottomY, midX, topY, 45)
            Thread.sleep(800)
        }
    }

    private fun MacrobenchmarkScope.scrollToTop() {
        val midX = device.displayWidth / 2
        val bottomY = (device.displayHeight * 0.75).toInt()
        val topY = (device.displayHeight * 0.25).toInt()
        repeat(4) {
            device.swipe(midX, topY, midX, bottomY, 45)
            Thread.sleep(800)
        }
    }

    private fun MacrobenchmarkScope.runLibraryPagerSwipeFlow() {
        val startX = (device.displayWidth * 0.85).toInt()
        val endX = (device.displayWidth * 0.15).toInt()
        val centerY = (device.displayHeight * 0.5).toInt()
        repeat(4) {
            device.swipe(startX, centerY, endX, centerY, 35)
            Thread.sleep(1500)
            blindScroll()
        }
    }

    private fun recoverBaselineProfile(packageName: String, instrumentation: android.app.Instrumentation) {
        val deviceManual = UiDevice.getInstance(instrumentation)

        // El Logcat confirmó que la ruta es:
        val srcPath = "/data/misc/profman/$packageName-primary.prof.txt"

        // Destino en la carpeta de medios del benchmark (Donde SIEMPRE hay permisos de escritura/lectura)
        val dest = "/sdcard/Android/media/com.theveloper.pixelplay.baselineprofile/baseline-prof.txt"

        Log.i("BaselineProfileGenerator", "Forzando volcado de perfiles...")

        deviceManual.executeShellCommand("pm dump-profiles --dump-classes-and-methods $packageName")
        deviceManual.executeShellCommand("killall -s SIGUSR1 $packageName")
        Thread.sleep(5000)

        // Intentamos una copia estándar a una ruta que ADB puede leer sin Root
        deviceManual.executeShellCommand("sh -c 'cat $srcPath > $dest'")

        Log.i("BaselineProfileGenerator", "RESCATE FINALIZADO.")
        Log.i("BaselineProfileGenerator", "SI FALLA EL PULL MANUAL, USA EL COMANDO ROOT DESDE TU MAC.")
    }
}