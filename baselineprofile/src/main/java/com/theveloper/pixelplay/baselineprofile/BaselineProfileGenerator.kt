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

                // 2. Start App
                // Using startActivityAndWait can sometimes timeout if the app doesn't report fully drawn fast enough.
                // We trust the process starts and we wait for UI manually.
                startActivityAndWait { intent ->
                    intent.putExtra("is_benchmark", true)
                }

                // Wait for any initial splash/loading
                device.waitForIdle(2000)

                // Handle system dialogs (Permissions)
                handlePermissionDialogs()

                // Handle App Loading Overlay ("Preparing your library...")
                waitForAppLoading()

                // Handle Onboarding (if any remains)
                handleOnboarding()

                // 3. Navigation & List Scrolling (Critical for Jank)
                // We iterate through tabs and scroll them to capture list rendering paths
                runTabsAndScrollingFlow()

                // 4. Heavy Components: UnifiedPlayerSheet
                // This is the highest priority for the user to fix animation jank
                runUnifiedPlayerSheetFlow()

                // 5. Finalize
                device.pressHome()
                device.waitForIdle(1000)
            }
        } catch (e: Exception) {
            // Preserve the user's custom rescue logic for their environment
            recoverBaselineProfile(packageName, instrumentation, e)
        }
    }

    private fun MacrobenchmarkScope.setupPermissions(packageName: String) {
        // Permissions are crucial for the app to show content and not be empty
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
            device.wait(Until.gone(By.text(loadingPattern)), 15000)
        } catch (e: Exception) {
            // Proceed anyway, maybe it wasn't there
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

    private fun MacrobenchmarkScope.runTabsAndScrollingFlow() {
        // Explicitly target the Bottom Navigation items
        // We use both text and desc to be robust
        val bottomTabs = listOf(
            "Library|Biblioteca",
            "Home|Inicio",
            "Search|Buscar"
        )

        for (tabName in bottomTabs) {
            try {
                // Try finding by text or description (Navigation items usually have both)
                val pattern = Pattern.compile(tabName, Pattern.CASE_INSENSITIVE)
                val selector = By.text(pattern)

                // Click the tab
                val tab = device.wait(Until.findObject(selector), 5000)
                if (tab != null) {
                    tab.click()
                    device.waitForIdle(1500)

                    // If we are in Library, we also want to exercise the top tabs (Songs, Albums, Artists)
                    if (tabName.contains("Library", true) || tabName.contains("Biblioteca", true)) {
                        runLibrarySubTabsFlow()
                    } else {
                        // Just scroll the main content for Home/Search
                        scrollContent()
                    }
                }
            } catch (e: Exception) {
               // Log but continue
               android.util.Log.w("BaselineProfileGenerator", "Failed to navigate to tab: $tabName")
            }
        }
    }

    private fun MacrobenchmarkScope.runLibrarySubTabsFlow() {
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

                    // Specific drill-down for Albums to test detail screen transition
                    if (subTabName.contains("Albums", true)) {
                        enterDetailAndScroll()
                    }
                }
            } catch (e: Exception) {}
        }
    }

    private fun MacrobenchmarkScope.scrollContent() {
        // Find any scrollable container (LazyColumn, LazyGrid, etc.)
        val scrollable = device.findObject(By.scrollable(true))
        if (scrollable != null) {
            scrollable.setGestureMargin(device.displayWidth / 10)

            // Slow scroll (layout/measure)
            scrollable.scroll(Direction.DOWN, 1.0f)
            device.waitForIdle(500)

            // Fast scroll (fling/jank)
            scrollable.fling(Direction.DOWN)
            device.waitForIdle(800)

            // Scroll back up to reset for next interaction
            scrollable.scroll(Direction.UP, 1.0f)
            device.waitForIdle(500)
        }
    }

    private fun MacrobenchmarkScope.enterDetailAndScroll() {
        try {
            val scrollable = device.findObject(By.scrollable(true))
            // We need to click an item inside the scrollable.
            // Using a point in the center-top of the scrollable is often safer than finding children objects
            // which might be complex in Compose.
            if (scrollable != null) {
                val bounds = scrollable.visibleBounds
                // Click slightly below the top to avoid clicking headers
                val clickPoint = Point(bounds.centerX(), bounds.top + 200)
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
        // The Queue button is a good indicator that the sheet is fully expanded
        val queueButtonSelector = By.desc(Pattern.compile(".*(Queue|Cola).*", Pattern.CASE_INSENSITIVE))

        // 1. Expand Player
        try {
            // Find miniplayer cover and click
            val cover = device.findObject(coverSelector)
            if (cover != null) {
                cover.click()
                // Wait for expanded state
                device.wait(Until.hasObject(queueButtonSelector), 5000)
            }
        } catch (e: Exception) {}

        // 2. Interact if Expanded
        if (device.hasObject(queueButtonSelector)) {
            try {
                // Swipe Cover (Pager interaction) - Critical for GPU profiling
                val fullCover = device.findObject(coverSelector)
                fullCover?.swipe(Direction.LEFT, 0.8f)
                device.waitForIdle(1000)

                // Play/Pause
                val playPausePattern = Pattern.compile(".*(Play|Pause|Reproducir|Pausa).*", Pattern.CASE_INSENSITIVE)
                val playBtn = device.findObject(By.desc(playPausePattern))
                if (playBtn?.isClickable == true) playBtn.click() else playBtn?.parent?.click()
                device.waitForIdle(500)

                // Open Queue BottomSheet
                val queueBtn = device.findObject(queueButtonSelector)
                if (queueBtn?.isClickable == true) queueBtn.click() else queueBtn?.parent?.click()
                device.waitForIdle(1500)

                // Close Queue (Back press)
                device.pressBack()
                device.waitForIdle(1000)
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
        android.util.Log.e("BaselineProfileGenerator", "FALLO DETECTADO: ${e.message}")

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

            android.util.Log.i("BaselineProfileGenerator", "Attempted manual rescue of profile to: $paths")
        } else {
            throw e
        }
    }
}
