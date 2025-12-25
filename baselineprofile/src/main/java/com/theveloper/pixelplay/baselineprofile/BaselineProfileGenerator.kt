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

                // 2. Start App with benchmark flag to reduce flakiness (e.g. mock network/ads)
                startActivityAndWait { intent ->
                    intent.putExtra("is_benchmark", true)
                }
                device.waitForIdle(2000)

                // Handle initial dialogs
                handlePermissionDialogs()
                handleOnboarding()

                // 3. Navigation & List Scrolling
                runTabsAndScrollingFlow()

                // 4. UnifiedPlayerSheet Interactions
                runUnifiedPlayerSheetFlow()

                // 5. Finalize
                device.pressHome()
                device.waitForIdle(1000)
            }
        } catch (e: Exception) {
            recoverBaselineProfile(packageName, instrumentation, e)
        }
    }

    private fun MacrobenchmarkScope.setupPermissions(packageName: String) {
        device.executeShellCommand("pm grant $packageName android.permission.POST_NOTIFICATIONS")
        device.executeShellCommand("appops set $packageName MANAGE_EXTERNAL_STORAGE allow")
    }

    private fun MacrobenchmarkScope.handlePermissionDialogs() {
        val pattern = Pattern.compile("Allow|While using the app|Permitir|Aceptar", Pattern.CASE_INSENSITIVE)
        repeat(3) {
            try {
                val dialogBtn = device.findObject(By.text(pattern))
                if (dialogBtn?.isClickable == true) dialogBtn.click() else dialogBtn?.parent?.click()
            } catch (e: Exception) {}
            device.waitForIdle(500)
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
        val tabs = listOf(
            "Library|Biblioteca",
            "Home|Inicio",
            "Search|Buscar"
        )

        for (tabName in tabs) {
            try {
                val selector = By.text(Pattern.compile(tabName, Pattern.CASE_INSENSITIVE))
                val tab = device.wait(Until.findObject(selector), 3000)

                if (tab != null) {
                    if (tab.isClickable) tab.click() else tab.parent?.click()
                    device.waitForIdle(1500)

                    scrollContent()

                    if (tabName.contains("Library", true) || tabName.contains("Biblioteca", true)) {
                        enterDetailAndScroll()
                    }
                }
            } catch (e: Exception) {}
        }
    }

    private fun MacrobenchmarkScope.scrollContent() {
        val scrollable = device.findObject(By.scrollable(true))
        if (scrollable != null) {
            scrollable.setGestureMargin(device.displayWidth / 10)

            // Slow scroll (layout/measure)
            scrollable.scroll(Direction.DOWN, 1.0f)
            device.waitForIdle(500)

            // Fast scroll (fling/jank)
            scrollable.fling(Direction.DOWN)
            device.waitForIdle(800)

            // Scroll back up
            scrollable.scroll(Direction.UP, 1.0f)
            device.waitForIdle(500)
        }
    }

    private fun MacrobenchmarkScope.enterDetailAndScroll() {
        try {
            val scrollable = device.findObject(By.scrollable(true))
            val items = scrollable?.children
            if (!items.isNullOrEmpty()) {
                val target = items.getOrNull(1) ?: items.getOrNull(0)
                target?.click()
                device.waitForIdle(2000)
                scrollContent()
                device.pressBack()
                device.waitForIdle(1500)
            }
        } catch (e: Exception) {}
    }

    private fun MacrobenchmarkScope.runUnifiedPlayerSheetFlow() {
        // Selectors
        val coverSelector = By.desc(Pattern.compile(".*(Carátula|Cover|Album Art).*", Pattern.CASE_INSENSITIVE))
        // Using Queue button to confirm full player expansion, as "Next" (Siguiente) might be in MiniPlayer
        val queueButtonSelector = By.desc(Pattern.compile(".*(Queue|Cola).*", Pattern.CASE_INSENSITIVE))

        // 1. Expand Player
        try {
            val cover = device.findObject(coverSelector)
            if (cover != null) {
                cover.click()
                // Wait for specific Full Player element
                device.wait(Until.hasObject(queueButtonSelector), 5000)
            }
        } catch (e: Exception) {}

        // 2. Interact if Expanded
        if (device.hasObject(queueButtonSelector)) {
            try {
                // Swipe Cover (Pager interaction)
                val fullCover = device.findObject(coverSelector)
                fullCover?.swipe(Direction.LEFT, 0.8f)
                device.waitForIdle(1000)

                // Play/Pause
                val playPausePattern = Pattern.compile(".*(Play|Pause|Reproducir|Pausa).*", Pattern.CASE_INSENSITIVE)
                val playBtn = device.findObject(By.desc(playPausePattern))
                if (playBtn?.isClickable == true) playBtn.click() else playBtn?.parent?.click()
                device.waitForIdle(500)

                // Toggle back
                val playBtn2 = device.findObject(By.desc(playPausePattern))
                if (playBtn2?.isClickable == true) playBtn2.click() else playBtn2?.parent?.click()

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

            // 4. Hot Path: Re-open and close quickly
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

        if (e.message?.contains("flush profiles") == true || e is IllegalStateException || e is StaleObjectException) {
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
