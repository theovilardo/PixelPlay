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
                // We use the shell command directly as it proved more robust for this device.
                Log.d("BaselineProfileGenerator", "Starting activity via shell command...")
                device.executeShellCommand("am start -n $packageName/.MainActivity --ez is_benchmark true")

                // Wait for any initial splash/loading
                device.waitForIdle(2000)

                // Handle system dialogs (Permissions)
                handlePermissionDialogs()

                // Handle App Loading Overlay ("Preparing your library...")
                waitForAppLoading()

                // Handle Onboarding (if any remains)
                handleOnboarding()

                // Ensure Player is collapsed before starting navigation tests
                ensurePlayerCollapsed()

                // 3. Navigation & List Scrolling (Critical for Jank)
                Log.d("BaselineProfileGenerator", "Running Tabs and Scrolling Flow...")
                runTabsAndScrollingFlow()

                // 4. Heavy Components: UnifiedPlayerSheet
                Log.d("BaselineProfileGenerator", "Running UnifiedPlayerSheet Flow...")
                runUnifiedPlayerSheetFlow()

                // 5. Finalize
                device.pressHome()
                device.waitForIdle(1000)
                Log.d("BaselineProfileGenerator", "Generation Finished.")
            }
        } catch (e: Exception) {
            // Preserve the user's custom rescue logic for their environment
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
        // The app shows a "Preparing your library..." overlay. We must wait for it to disappear.
        val loadingPattern = Pattern.compile("Preparing your library|Sincronizando biblioteca", Pattern.CASE_INSENSITIVE)
        try {
            // Wait up to 15 seconds for the loading screen to be gone
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
        // Indicators of Expanded Player (not present in MiniPlayer)
        val fullPlayerIndicators = Pattern.compile(".*(Queue|Cola|Shuffle|Aleatorio|Repeat|Repetir).*", Pattern.CASE_INSENSITIVE)

        try {
            if (device.hasObject(By.desc(fullPlayerIndicators))) {
                Log.d("BaselineProfileGenerator", "Player seems expanded. Collapsing...")
                device.pressBack()
                device.waitForIdle(1000)
            }
        } catch (e: Exception) {}
    }

    private fun MacrobenchmarkScope.runTabsAndScrollingFlow() {
        val bottomTabs = listOf(
            "Library|Biblioteca",
            "Home|Inicio",
            "Search|Buscar"
        )

        for (tabName in bottomTabs) {
            try {
                Log.d("BaselineProfileGenerator", "Attempting to navigate to tab: $tabName")
                val pattern = Pattern.compile(tabName, Pattern.CASE_INSENSITIVE)
                // Try finding by text or description (Navigation items usually have both)
                val selector = By.text(pattern)
                val descSelector = By.desc(pattern)

                // Click the tab
                var tab = device.wait(Until.findObject(selector), 3000)
                if (tab == null) {
                    tab = device.findObject(descSelector)
                }

                if (tab != null) {
                    // If the tab is not clickable (maybe it's a child text), click parent
                    if (tab.isClickable) tab.click() else tab.parent?.click()
                    device.waitForIdle(2000) // Visual pause for the user

                    // Special flows for specific tabs
                    if (tabName.contains("Library", true) || tabName.contains("Biblioteca", true)) {
                        runLibrarySubTabsFlow()
                        // Run settings flow from Library
                        runSettingsFlow()
                    } else if (tabName.contains("Search", true) || tabName.contains("Buscar", true)) {
                        scrollContent()
                    } else {
                        scrollContent()
                    }
                } else {
                    Log.w("BaselineProfileGenerator", "Could not find tab: $tabName")
                }
            } catch (e: Exception) {
               Log.e("BaselineProfileGenerator", "Error navigating to tab: $tabName", e)
            }
        }
    }

    private fun MacrobenchmarkScope.runLibrarySubTabsFlow() {
        Log.d("BaselineProfileGenerator", "Running Library Sub-Tabs Flow...")
        // Top tabs in LibraryScreen
        val libraryTabs = listOf("Albums|Álbumes", "Artists|Artistas", "Songs|Canciones")

        for (subTabName in libraryTabs) {
            try {
                val pattern = Pattern.compile(subTabName, Pattern.CASE_INSENSITIVE)
                val subTab = device.findObject(By.text(pattern))
                if (subTab != null) {
                    subTab.click()
                    device.waitForIdle(1000)

                    scrollContent()

                    // Specific drill-down for Albums
                    if (subTabName.contains("Albums", true) || subTabName.contains("Álbumes", true)) {
                        enterDetailAndScroll()
                    }
                }
            } catch (e: Exception) {}
        }
    }

    private fun MacrobenchmarkScope.runSettingsFlow() {
        Log.d("BaselineProfileGenerator", "Running Settings Flow...")
        // In Library Screen, look for Settings icon
        val settingsPattern = Pattern.compile(".*(Settings|Ajustes|Configuración).*", Pattern.CASE_INSENSITIVE)
        try {
            val settingsBtn = device.findObject(By.desc(settingsPattern))
            if (settingsBtn != null) {
                settingsBtn.click()
                device.waitForIdle(2000) // Visual pause

                // Scroll Settings
                scrollContent()

                // Back to Library
                device.pressBack()
                device.waitForIdle(1000)
            } else {
                Log.w("BaselineProfileGenerator", "Settings button not found")
            }
        } catch (e: Exception) {
            Log.w("BaselineProfileGenerator", "Could not find/interact with Settings.")
        }
    }

    private fun MacrobenchmarkScope.scrollContent() {
        val scrollable = device.findObject(By.scrollable(true))
        if (scrollable != null) {
            scrollable.setGestureMargin(device.displayWidth / 10)

            scrollable.scroll(Direction.DOWN, 1.0f)
            device.waitForIdle(500)

            scrollable.fling(Direction.DOWN)
            device.waitForIdle(800)

            scrollable.scroll(Direction.UP, 1.0f)
            device.waitForIdle(500)
        }
    }

    private fun MacrobenchmarkScope.enterDetailAndScroll() {
        try {
            val scrollable = device.findObject(By.scrollable(true))
            if (scrollable != null) {
                val bounds = scrollable.visibleBounds
                // Click center-top to hit an item, avoiding headers
                val clickPoint = Point(bounds.centerX(), bounds.top + (bounds.height() / 4))
                device.click(clickPoint.x, clickPoint.y)

                device.waitForIdle(2000) // Wait for transition

                // Now inside detail, scroll
                scrollContent()

                // Back
                device.pressBack()
                device.waitForIdle(1000)
            }
        } catch (e: Exception) {}
    }

    private fun MacrobenchmarkScope.runUnifiedPlayerSheetFlow() {
        // Selectors
        val coverSelector = By.desc(Pattern.compile(".*(Carátula|Cover|Album Art).*", Pattern.CASE_INSENSITIVE))
        // Use Queue or Shuffle as indicator of full player
        val fullPlayerIndicator = By.desc(Pattern.compile(".*(Queue|Cola|Shuffle|Aleatorio).*", Pattern.CASE_INSENSITIVE))

        // 1. Expand Player
        try {
            val cover = device.findObject(coverSelector)
            if (cover != null) {
                cover.click()
                // Wait for expanded state
                device.wait(Until.hasObject(fullPlayerIndicator), 5000)
                device.waitForIdle(1000) // Visual pause
            } else {
                Log.w("BaselineProfileGenerator", "MiniPlayer cover not found to expand.")
            }
        } catch (e: Exception) {}

        // 2. Interact if Expanded
        if (device.hasObject(fullPlayerIndicator)) {
            try {
                Log.d("BaselineProfileGenerator", "Interacting with UnifiedPlayerSheet...")
                // Swipe Cover (Pager interaction)
                val fullCover = device.findObject(coverSelector)
                fullCover?.swipe(Direction.LEFT, 0.8f)
                device.waitForIdle(1000)

                // Play/Pause
                val playPausePattern = Pattern.compile(".*(Play|Pause|Reproducir|Pausa).*", Pattern.CASE_INSENSITIVE)
                val playBtn = device.findObject(By.desc(playPausePattern))
                if (playBtn?.isClickable == true) playBtn.click() else playBtn?.parent?.click()
                device.waitForIdle(500)

                // Open Queue BottomSheet if found
                val queueBtn = device.findObject(By.desc(Pattern.compile(".*(Queue|Cola).*", Pattern.CASE_INSENSITIVE)))
                if (queueBtn?.isClickable == true) {
                    queueBtn.click()
                    device.waitForIdle(1500)
                    device.pressBack() // Close Queue
                    device.waitForIdle(1000)
                }
            } catch (e: Exception) {}

            // 3. Collapse Player
            device.pressBack()
            device.waitForIdle(1000)

            // 4. Exercise Re-entry (Hot Path)
            try {
                val coverRecycled = device.findObject(coverSelector)
                coverRecycled?.click()
                device.waitForIdle(1500)
                device.pressBack()
            } catch (e: Exception) {}
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
