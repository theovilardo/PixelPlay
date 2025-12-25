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
                clickTab("Home|Inicio")
                safeScrollContent()

                // =================================================================================
                // 3. SETTINGS FLOW (From Home)
                // =================================================================================
                Log.d("BaselineProfileGenerator", "STEP 2: Settings")
                navigateToSettingsAndScroll()

                // =================================================================================
                // 4. MAIN TABS NAVIGATION
                // =================================================================================
                Log.d("BaselineProfileGenerator", "STEP 3: Main Tabs Navigation")
                val tabs = listOf("Search|Buscar", "Library|Biblioteca", "Home|Inicio", "Library|Biblioteca")
                tabs.forEach { tab ->
                    clickTab(tab)
                    device.waitForIdle(1000)
                }

                // =================================================================================
                // 5. LIBRARY INTERNAL TABS (Pager Swipe)
                // =================================================================================
                Log.d("BaselineProfileGenerator", "STEP 4: Library Internal Tabs")
                clickTab("Library|Biblioteca")
                runLibraryPagerSwipeFlow()

                // =================================================================================
                // 6. UNIFIED PLAYER SHEET (Heavy Component)
                // =================================================================================
                Log.d("BaselineProfileGenerator", "STEP 5: UnifiedPlayerSheet")
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
        val pattern = Pattern.compile("Allow|While using the app|Permitir|Aceptar|Grant", Pattern.CASE_INSENSITIVE)
        repeat(4) {
            try {
                val dialogBtn = device.findObject(By.text(pattern))
                if (dialogBtn?.isClickable == true) dialogBtn.click() else dialogBtn?.parent?.click()
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
        val fullPlayerIndicators = Pattern.compile(".*(Queue|Cola|Shuffle|Aleatorio|Repeat|Repetir).*", Pattern.CASE_INSENSITIVE)
        try {
            if (device.hasObject(By.desc(fullPlayerIndicators))) {
                Log.d("BaselineProfileGenerator", "Collapsing expanded player...")
                device.pressBack()
                device.waitForIdle(1500)
            }
        } catch (e: Exception) {}
    }

    private fun MacrobenchmarkScope.clickTab(tabNamePattern: String) {
        try {
            val pattern = Pattern.compile(tabNamePattern, Pattern.CASE_INSENSITIVE)
            // Try description first (Icon only tabs usually have desc)
            var tab = device.wait(Until.findObject(By.desc(pattern)), 2000)
            if (tab == null) {
                tab = device.findObject(By.text(pattern))
            }

            if (tab != null) {
                if (tab.isClickable) tab.click() else tab.parent?.click()
                device.waitForIdle(1500)
            } else {
                Log.w("BaselineProfileGenerator", "Tab not found: $tabNamePattern")
            }
        } catch (e: Exception) {
            Log.e("BaselineProfileGenerator", "Nav error: $tabNamePattern", e)
        }
    }

    private fun MacrobenchmarkScope.navigateToSettingsAndScroll() {
        // Look for Settings icon in Top Bar
        val settingsPattern = Pattern.compile(".*(Settings|Ajustes|Configuración).*", Pattern.CASE_INSENSITIVE)
        try {
            val settingsBtn = device.findObject(By.desc(settingsPattern))
            if (settingsBtn != null) {
                settingsBtn.click()
                device.waitForIdle(2000)

                // Scroll the Settings screen
                safeScrollContent()

                // Back to previous screen
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
            // High gesture margin to avoid pulling Notification Shade (Status Bar)
            // 25% of screen height from top/bottom
            scrollable.setGestureMargin(device.displayHeight / 4)

            // Scroll Down (Swipe UP) - View content below
            scrollable.scroll(Direction.DOWN, 1.0f)
            device.waitForIdle(500)

            // Fling (Fast Scroll)
            scrollable.fling(Direction.DOWN)
            device.waitForIdle(1000)

            // Scroll Back Up (Swipe DOWN) - View content above
            // The margin prevents this from grabbing status bar
            scrollable.scroll(Direction.UP, 1.0f)
            device.waitForIdle(500)
        } else {
            Log.d("BaselineProfileGenerator", "No scrollable found.")
        }
    }

    private fun MacrobenchmarkScope.runLibraryPagerSwipeFlow() {
        // Swipe laterally through the Pager
        val screenWidth = device.displayWidth
        val screenHeight = device.displayHeight

        val startX = (screenWidth * 0.9).toInt()
        val endX = (screenWidth * 0.1).toInt()
        val centerY = (screenHeight * 0.5).toInt()

        // Iterate through typical number of tabs (Songs, Albums, Artists, Playlists, Liked, Folders)
        repeat(6) { i ->
            Log.d("BaselineProfileGenerator", "Library Tab Swipe $i")

            // Scroll current tab content
            safeScrollContent()
            device.waitForIdle(500)

            // Swipe to next tab (Right-to-Left)
            device.swipe(startX, centerY, endX, centerY, 25)
            device.waitForIdle(1500) // Wait for pager snap & content load
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
                // Swipe Carousel (Pager)
                val fullCover = device.findObject(coverSelector)
                fullCover?.swipe(Direction.LEFT, 0.8f)
                device.waitForIdle(1000)

                // Buttons
                val shuffleBtn = device.findObject(By.desc(Pattern.compile(".*(Shuffle|Aleatorio).*", Pattern.CASE_INSENSITIVE)))
                shuffleBtn?.click()
                device.waitForIdle(500)

                val repeatBtn = device.findObject(By.desc(Pattern.compile(".*(Repeat|Repetir).*", Pattern.CASE_INSENSITIVE)))
                repeatBtn?.click()
                device.waitForIdle(500)

                // Slider Interaction (Generic swipe at bottom)
                val sliderY = (device.displayHeight * 0.82).toInt()
                device.swipe((device.displayWidth * 0.2).toInt(), sliderY, (device.displayWidth * 0.8).toInt(), sliderY, 40)
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
