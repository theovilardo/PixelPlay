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
                // 1. Setup & Permissions
                setupPermissions(packageName)

                // 2. Start App MANUALLY (avoiding startActivityAndWait timeout crash)
                Log.d("BaselineProfileGenerator", "Starting activity via shell command...")
                device.executeShellCommand("am start -n $packageName/.MainActivity --ez is_benchmark true")

                // Wait for any initial splash/loading
                device.waitForIdle(2000)

                // Handle system dialogs (Permissions)
                handlePermissionDialogs()

                // Handle App Loading Overlay
                waitForAppLoading()

                // Handle Onboarding
                handleOnboarding()

                // Ensure Player is collapsed before starting navigation tests
                ensurePlayerCollapsed()

                // 3. Main Navigation Flows

                // Home Tab & Scroll
                runHomeScrollFlow()

                // Library Tabs & Scroll
                runLibraryPagerSwipeFlow()

                // Search Tab & Scroll
                runSearchScrollFlow()

                // Settings Flow
                // We navigate to Settings from Library (where the icon is typically accessible in the top bar)
                navigateToSettingsAndScroll()

                // 4. Heavy Components: UnifiedPlayerSheet
                Log.d("BaselineProfileGenerator", "Running UnifiedPlayerSheet Flow...")
                runUnifiedPlayerSheetFlow()

                // 5. Finalize
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
            } else {
                Log.w("BaselineProfileGenerator", "Loading overlay wait timed out (it might not have appeared).")
            }
        } catch (e: Exception) {
            Log.w("BaselineProfileGenerator", "Exception waiting for loading overlay: ${e.message}")
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
                Log.d("BaselineProfileGenerator", "Player seems expanded. Collapsing...")
                device.pressBack()
                device.waitForIdle(1000)
            }
        } catch (e: Exception) {}
    }

    private fun MacrobenchmarkScope.clickTab(tabNamePattern: String) {
        try {
            val pattern = Pattern.compile(tabNamePattern, Pattern.CASE_INSENSITIVE)
            val selector = By.text(pattern)
            val descSelector = By.desc(pattern)

            var tab = device.wait(Until.findObject(selector), 3000)
            if (tab == null) {
                tab = device.findObject(descSelector)
            }

            if (tab != null) {
                if (tab.isClickable) tab.click() else tab.parent?.click()
                device.waitForIdle(2000)
            } else {
                Log.w("BaselineProfileGenerator", "Could not find tab: $tabNamePattern")
            }
        } catch (e: Exception) {
            Log.e("BaselineProfileGenerator", "Error navigating to tab: $tabNamePattern", e)
        }
    }

    private fun MacrobenchmarkScope.runHomeScrollFlow() {
        Log.d("BaselineProfileGenerator", "Running Home Scroll Flow...")
        clickTab("Home|Inicio")
        scrollContent()
    }

    private fun MacrobenchmarkScope.runSearchScrollFlow() {
        Log.d("BaselineProfileGenerator", "Running Search Scroll Flow...")
        clickTab("Search|Buscar")
        scrollContent()
    }

    private fun MacrobenchmarkScope.runLibraryPagerSwipeFlow() {
        Log.d("BaselineProfileGenerator", "Running Library Pager Swipe Flow...")
        clickTab("Library|Biblioteca")

        // Find the main scrollable container which might act as the Pager or list
        // In LibraryScreen, the hierarchy is roughly Scaffold -> Column -> HorizontalPager.
        // We want to target the area that accepts swipes.
        // Usually, the center of the screen works for Pagers.

        // We will loop a few times to cover standard tabs: Songs, Albums, Artists, Playlists, Liked, Folders.
        // Assuming we start at the first tab (Songs) or last remembered.
        // Best approach is to swipe LEFT multiple times to ensure we are at the end, then swipe RIGHT?
        // Or just swipe LEFT (content moves right) to go to next tabs.
        // Direction.LEFT means finger moves right -> left, content moves left, next tab appears.

        val screenWidth = device.displayWidth
        val screenHeight = device.displayHeight

        // Center area for swiping tabs
        val startX = (screenWidth * 0.9).toInt()
        val endX = (screenWidth * 0.1).toInt()
        val centerY = (screenHeight * 0.5).toInt()

        repeat(6) { iteration ->
            Log.d("BaselineProfileGenerator", "Library Tab iteration $iteration")

            // 1. Scroll vertical content of current tab
            scrollContent()
            device.waitForIdle(1000)

            // 2. Swipe to next tab (Right-to-Left swipe)
            device.swipe(startX, centerY, endX, centerY, 20) // steps=20 for smooth swipe
            device.waitForIdle(2000) // Wait for pager snap and load
        }
    }

    private fun MacrobenchmarkScope.navigateToSettingsAndScroll() {
        Log.d("BaselineProfileGenerator", "Navigating to Settings...")
        // We assume we are in Library or accessible context.
        // If not, click Library again to be safe.
        clickTab("Library|Biblioteca")

        val settingsPattern = Pattern.compile(".*(Settings|Ajustes|Configuración).*", Pattern.CASE_INSENSITIVE)
        try {
            val settingsBtn = device.findObject(By.desc(settingsPattern))
            if (settingsBtn != null) {
                settingsBtn.click()
                device.waitForIdle(2000)

                // Scroll Settings
                scrollContent()

                // Back
                device.pressBack()
                device.waitForIdle(1000)
            } else {
                Log.w("BaselineProfileGenerator", "Settings button not found")
            }
        } catch (e: Exception) {
            Log.w("BaselineProfileGenerator", "Could not interact with Settings.")
        }
    }

    private fun MacrobenchmarkScope.scrollContent() {
        // Find scrollable
        val scrollable = device.findObject(By.scrollable(true))

        if (scrollable != null) {
            // Set margin to avoid Status Bar (Notification Shade) and Nav Bar
            // 20% top margin is usually safe
            scrollable.setGestureMargin(device.displayHeight / 5)

            // Scroll Down (finger moves UP)
            scrollable.scroll(Direction.DOWN, 1.0f)
            device.waitForIdle(500)

            // Fling (Fast Scroll)
            scrollable.fling(Direction.DOWN)
            device.waitForIdle(800)

            // Scroll Up (finger moves DOWN)
            // Important: gesture margin protects this from pulling notification shade
            scrollable.scroll(Direction.UP, 1.0f)
            device.waitForIdle(500)
        } else {
            // Fallback if no scrollable found (e.g. empty screen), just to keep flow moving
            Log.d("BaselineProfileGenerator", "No scrollable object found, skipping scroll.")
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
                device.waitForIdle(1000)
            } else {
                Log.w("BaselineProfileGenerator", "MiniPlayer cover not found.")
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

                // Buttons: Shuffle, Repeat, Play/Pause
                val shuffleBtn = device.findObject(By.desc(Pattern.compile(".*(Shuffle|Aleatorio).*", Pattern.CASE_INSENSITIVE)))
                shuffleBtn?.click()
                device.waitForIdle(500)

                val repeatBtn = device.findObject(By.desc(Pattern.compile(".*(Repeat|Repetir).*", Pattern.CASE_INSENSITIVE)))
                repeatBtn?.click()
                device.waitForIdle(500)

                val playBtn = device.findObject(By.desc(Pattern.compile(".*(Play|Pause|Reproducir|Pausa).*", Pattern.CASE_INSENSITIVE)))
                playBtn?.click()
                device.waitForIdle(500)

                // Slider Interaction (Generic swipe in bottom third area)
                val screenWidth = device.displayWidth
                val screenHeight = device.displayHeight
                val sliderY = (screenHeight * 0.82).toInt() // Approximate location of slider
                // Drag from leftish to rightish
                device.swipe((screenWidth * 0.2).toInt(), sliderY, (screenWidth * 0.8).toInt(), sliderY, 30)
                device.waitForIdle(1000)

                // Close
                device.pressBack()
                device.waitForIdle(1000)

                // Re-open/Close Hot Path
                val coverRecycled = device.findObject(coverSelector)
                coverRecycled?.click()
                device.waitForIdle(1500)
                device.pressBack()
            } catch (e: Exception) {
                Log.e("BaselineProfileGenerator", "Error in Player Sheet interaction", e)
            }
        }
    }

    private fun recoverBaselineProfile(packageName: String, instrumentation: android.app.Instrumentation, e: Exception) {
        val deviceManual = UiDevice.getInstance(instrumentation)
        Log.e("BaselineProfileGenerator", "FALLO DETECTADO: ${e.message}")

        if (e.message?.contains("flush profiles") == true || e is IllegalStateException || e is StaleObjectException || e.message?.contains("Unable to confirm activity launch") == true) {
            val srcPath = "/data/misc/profman/$packageName-primary.prof.txt"
            val paths = listOf(
                "/storage/emulated/0/Download/baseline-rescue.txt",
                "/sdcard/baseline-rescue.txt",
                "${instrumentation.targetContext.getExternalFilesDir(null)}/baseline-rescue.txt"
            )

            paths.forEach { dest ->
                try { deviceManual.executeShellCommand("cp $srcPath $dest") } catch (ignore: Exception) {}
            }

            Log.i("BaselineProfileGenerator", "Attempted manual rescue of profile to: $paths")
        } else {
            throw e
        }
    }
}
