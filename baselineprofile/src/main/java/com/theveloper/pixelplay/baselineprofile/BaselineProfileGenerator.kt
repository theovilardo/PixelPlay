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
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.StaleObjectException
import android.content.Intent
import android.graphics.Point
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

        try {
            rule.collect(
                packageName = packageName,
                includeInStartupProfile = true,
                maxIterations = 3
            ) {
                // =================================================================================
                // 1. SETUP & STARTUP
                // =================================================================================
                setupPermissions(packageName)

                Log.d("BaselineProfileGenerator", "Starting activity...")
                device.executeShellCommand("am start -n $packageName/.MainActivity --ez is_benchmark true")

                device.waitForIdle(2000)
                handlePermissionDialogs()
                waitForAppLoading()
                handleOnboarding()
                ensurePlayerCollapsed()

                // =================================================================================
                // 2. HOME SCROLL FLOW
                // =================================================================================
                Log.d("BaselineProfileGenerator", "STEP 1: Home Scroll")
                if (waitForTab("Home|Inicio")) {
                    clickTab("Home|Inicio")
                    safeScrollContent()
                } else {
                    Log.e("BaselineProfileGenerator", "Home tab not found. Skipping Home Scroll.")
                }

                // =================================================================================
                // 3. SETTINGS FLOW
                // =================================================================================
                Log.d("BaselineProfileGenerator", "STEP 2: Settings")
                navigateToSettingsAndScroll()

                // =================================================================================
                // 4. MAIN TABS NAVIGATION
                // =================================================================================
                Log.d("BaselineProfileGenerator", "STEP 3: Main Tabs Navigation")
                val tabs = listOf("Search|Buscar", "Library|Biblioteca", "Home|Inicio")
                tabs.forEach { tab ->
                    clickTab(tab)
                    device.waitForIdle(1000)
                }

                // =================================================================================
                // 5. LIBRARY INTERNAL TABS (Pager Swipe)
                // =================================================================================
                Log.d("BaselineProfileGenerator", "STEP 4: Library Internal Tabs")
                clickTab("Library|Biblioteca")
                device.waitForIdle(1000)
                runLibraryPagerSwipeFlow()

                // =================================================================================
                // 6. UNIFIED PLAYER SHEET
                // =================================================================================
                Log.d("BaselineProfileGenerator", "STEP 5: UnifiedPlayerSheet")
                // Important: Go back to Home first to ensure MiniPlayer is visible/accessible
                clickTab("Home|Inicio")
                device.waitForIdle(1000)

                runUnifiedPlayerSheetFlow()

                // =================================================================================
                // 7. FINALIZE
                // =================================================================================
                device.pressHome()
                device.waitForIdle(1000)
                Log.d("BaselineProfileGenerator", "Generation Finished.")
            }
        } catch (e: Exception) {
            recoverBaselineProfile(packageName, instrumentation, e)
        }
    }

    private fun MacrobenchmarkScope.setupPermissions(packageName: String) {
        device.executeShellCommand("pm grant $packageName android.permission.POST_NOTIFICATIONS")
        device.executeShellCommand("appops set $packageName MANAGE_EXTERNAL_STORAGE allow")
        device.executeShellCommand("pm grant $packageName android.permission.READ_MEDIA_AUDIO")
        device.executeShellCommand("pm grant $packageName android.permission.READ_EXTERNAL_STORAGE")
        device.executeShellCommand("pm grant $packageName android.permission.WRITE_EXTERNAL_STORAGE")
    }

    private fun MacrobenchmarkScope.handlePermissionDialogs() {
        val pattern = Pattern.compile("Allow|While using the app|Permitir|Aceptar|Grant|Grant Permission", Pattern.CASE_INSENSITIVE)
        repeat(4) {
            try {
                val dialogBtn = device.findObject(By.text(pattern))
                if (dialogBtn?.isClickable == true) {
                    dialogBtn.click()
                } else if (dialogBtn?.parent?.isClickable == true) {
                    dialogBtn.parent.click()
                }
            } catch (e: Exception) {}
            device.waitForIdle(500)
        }
    }

    private fun MacrobenchmarkScope.waitForAppLoading() {
        val loadingPattern = Pattern.compile("Preparing your library|Sincronizando biblioteca", Pattern.CASE_INSENSITIVE)
        try {
            if (device.wait(Until.gone(By.text(loadingPattern)), 15000)) {
                Log.d("BaselineProfileGenerator", "Loading overlay disappeared.")
            }
        } catch (e: Exception) {
            Log.w("BaselineProfileGenerator", "Exception waiting for loading: ${e.message}")
        }
    }

    private fun MacrobenchmarkScope.handleOnboarding() {
        val pattern = Pattern.compile("Next|Continue|Skip|Done|Get Started|Siguiente|Continuar|Omitir|Empezar", Pattern.CASE_INSENSITIVE)
        repeat(5) {
            try {
                val btn = device.findObject(By.text(pattern))
                if (btn?.isClickable == true) btn.click() else btn?.parent?.click()
            } catch (e: Exception) {}
            device.waitForIdle(1000)
        }
    }

    private fun MacrobenchmarkScope.ensurePlayerCollapsed() {
        val fullPlayerIndicators = Pattern.compile(".*(Queue|Cola|Shuffle|Aleatorio).*", Pattern.CASE_INSENSITIVE)
        try {
            if (device.hasObject(By.desc(fullPlayerIndicators))) {
                Log.d("BaselineProfileGenerator", "Collapsing expanded player...")
                device.pressBack()
                device.waitForIdle(1500)
            }
        } catch (e: Exception) {}
    }

    private fun MacrobenchmarkScope.waitForTab(tabNamePattern: String): Boolean {
        val pattern = Pattern.compile(tabNamePattern, Pattern.CASE_INSENSITIVE)
        val tab = device.wait(Until.findObject(By.text(pattern)), 10000)
                  ?: device.wait(Until.findObject(By.desc(pattern)), 1000)
        return tab != null
    }

    private fun MacrobenchmarkScope.clickTab(tabNamePattern: String) {
        try {
            val pattern = Pattern.compile(tabNamePattern, Pattern.CASE_INSENSITIVE)
            // Prioritize description since we improved accessibility
            var tab = device.findObject(By.desc(pattern))
            if (tab == null) {
                tab = device.findObject(By.text(pattern))
            }

            if (tab != null) {
                if (tab.isClickable) {
                    tab.click()
                } else if (tab.parent?.isClickable == true) {
                    tab.parent.click()
                } else {
                    // Last resort: click center of the object
                    tab.click()
                }
                device.waitForIdle(1500)
            } else {
                Log.w("BaselineProfileGenerator", "Tab not found for click: $tabNamePattern")
            }
        } catch (e: Exception) {
            Log.e("BaselineProfileGenerator", "Nav error: $tabNamePattern", e)
        }
    }

    private fun MacrobenchmarkScope.navigateToSettingsAndScroll() {
        val settingsPattern = Pattern.compile("Settings|Ajustes|Configuración", Pattern.CASE_INSENSITIVE)
        try {
            val settingsBtn = device.findObject(By.desc(settingsPattern))
            if (settingsBtn != null) {
                settingsBtn.click()
                device.waitForIdle(2000)
                safeScrollContent()
                device.pressBack()
                device.waitForIdle(1500)
            } else {
                Log.w("BaselineProfileGenerator", "Settings button not found.")
            }
        } catch (e: Exception) {
            Log.w("BaselineProfileGenerator", "Settings flow failed: ${e.message}")
        }
    }

    private fun MacrobenchmarkScope.safeScrollContent() {
        val scrollable = device.findObject(By.scrollable(true))
        if (scrollable != null) {
            // Margin to avoid Notification Shade
            scrollable.setGestureMargin(device.displayHeight / 4)

            scrollable.scroll(Direction.DOWN, 1.0f)
            device.waitForIdle(500)

            scrollable.fling(Direction.DOWN)
            device.waitForIdle(1000)

            scrollable.scroll(Direction.UP, 1.0f)
            device.waitForIdle(500)
        } else {
            Log.d("BaselineProfileGenerator", "No scrollable found.")
        }
    }

    private fun MacrobenchmarkScope.runLibraryPagerSwipeFlow() {
        val screenWidth = device.displayWidth
        val screenHeight = device.displayHeight

        // Coordinates for lateral swipe (Right to Left)
        val startX = (screenWidth * 0.9).toInt()
        val endX = (screenWidth * 0.1).toInt()
        val centerY = (screenHeight * 0.5).toInt()

        // 6 tabs: Songs, Albums, Artists, Playlists, Liked, Folders
        repeat(6) { i ->
            Log.d("BaselineProfileGenerator", "Library Tab Swipe $i")

            // 1. Scroll content of current tab
            safeScrollContent()
            device.waitForIdle(500)

            // 2. Swipe to next tab
            // Increased steps from 25 to 40 for a smoother/slower swipe that Pager likes better
            device.swipe(startX, centerY, endX, centerY, 40)
            device.waitForIdle(1500)
        }
    }

    private fun MacrobenchmarkScope.runUnifiedPlayerSheetFlow() {
        val coverSelector = By.desc(Pattern.compile(".*(Carátula|Cover|Album Art).*", Pattern.CASE_INSENSITIVE))
        val fullPlayerIndicator = By.desc(Pattern.compile(".*(Queue|Cola|Shuffle|Aleatorio).*", Pattern.CASE_INSENSITIVE))

        // 1. Expand
        try {
            val cover = device.findObject(coverSelector)
            if (cover != null) {
                cover.click()
                // Wait for expansion
                device.wait(Until.hasObject(fullPlayerIndicator), 5000)
                device.waitForIdle(1500)
            } else {
                Log.w("BaselineProfileGenerator", "MiniPlayer not found.")
                return
            }
        } catch (e: Exception) { return }

        // 2. Interact
        if (device.hasObject(fullPlayerIndicator)) {
            try {
                Log.d("BaselineProfileGenerator", "Interacting with UnifiedPlayerSheet...")

                // Swipe Carousel
                val fullCover = device.findObject(coverSelector)
                fullCover?.swipe(Direction.LEFT, 0.8f)
                device.waitForIdle(1000)

                // Buttons
                val shuffleBtn = device.findObject(By.desc(Pattern.compile(".*(Shuffle|Aleatorio).*", Pattern.CASE_INSENSITIVE)))
                shuffleBtn?.click()
                device.waitForIdle(500)

                // Slider Interaction - WavyMusicSlider
                // It sits above controls. We target the bottom 15-20% area.
                val screenHeight = device.displayHeight
                val screenWidth = device.displayWidth

                // Approximate slider Y position (e.g., 85% down the screen)
                val sliderY = (screenHeight * 0.85).toInt()

                // Drag from left (20%) to right (80%)
                Log.d("BaselineProfileGenerator", "Interacting with Slider at Y=$sliderY")
                device.swipe((screenWidth * 0.2).toInt(), sliderY, (screenWidth * 0.8).toInt(), sliderY, 50)
                device.waitForIdle(1000)

                // Close
                device.pressBack()
                device.waitForIdle(1000)

                // Re-open/Close (Hot Path)
                val coverRecycled = device.findObject(coverSelector)
                coverRecycled?.click()
                device.waitForIdle(1500)
                device.pressBack()
            } catch (e: Exception) {
                Log.e("BaselineProfileGenerator", "Player interaction error", e)
            }
        }
    }

    private fun recoverBaselineProfile(packageName: String, instrumentation: android.app.Instrumentation, e: Exception) {
        val deviceManual = UiDevice.getInstance(instrumentation)
        Log.e("BaselineProfileGenerator", "FALLO DETECTADO: ${e.message}")

        if (e.message?.contains("flush profiles") == true || e is IllegalStateException || e is StaleObjectException || e.message?.contains("Unable to confirm") == true) {
            val srcPath = "/data/misc/profman/$packageName-primary.prof.txt"
            val paths = listOf(
                "/storage/emulated/0/Download/baseline-rescue.txt",
                "/sdcard/baseline-rescue.txt",
                "${instrumentation.targetContext.getExternalFilesDir(null)}/baseline-rescue.txt"
            )

            paths.forEach { dest ->
                try { deviceManual.executeShellCommand("cp $srcPath $dest") } catch (ignore: Exception) {}
            }

            Log.i("BaselineProfileGenerator", "--------------------------------------------------------")
            Log.i("BaselineProfileGenerator", "SI EL ARCHIVO NO SE GENERÓ, EJECUTA ESTO EN TU PC:")
            Log.i("BaselineProfileGenerator", "adb pull ${paths[1]} ./baseline-prof.txt")
            Log.i("BaselineProfileGenerator", "--------------------------------------------------------")
        } else {
            throw e
        }
    }
}
