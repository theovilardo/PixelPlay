package com.theveloper.pixelplay.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
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

        rule.collect(
            packageName = packageName,
            includeInStartupProfile = true,
            maxIterations = 1
        ) {
            // 1. Grant Permissions
            device.executeShellCommand("pm grant $packageName android.permission.POST_NOTIFICATIONS")
            device.executeShellCommand("appops set $packageName MANAGE_EXTERNAL_STORAGE allow")

            // 2. Start the Activity manually
            device.executeShellCommand("am start -n $packageName/.MainActivity --ez is_benchmark true")

            // 3. Handle First Run / Permissions Dialogs / Onboarding
            handlePermissionDialogs()
            handleOnboarding()

            // 4. Wait for Home Screen Content
            if (!device.wait(Until.hasObject(By.text("Home")), 15000)) {
                 handleOnboarding()
                 device.wait(Until.hasObject(By.text("Home")), 5000)
            }
            device.waitForIdle()

            // 5. Navigation: Switch Tabs
            val tabs = listOf("Search", "Library", "Home")
            tabs.forEach { tabName ->
                val tab = device.wait(Until.findObject(By.text(tabName)), 3000)
                tab?.click()
                device.waitForIdle()
            }

            // 6. List Interaction (Library)
            val libraryTab = device.wait(Until.findObject(By.text("Library")), 3000)
            if (libraryTab != null) {
                libraryTab.click()
                device.waitForIdle()
                scrollList()
            }

            // 7. Open Detail / Play Song
            try {
                val list = device.wait(Until.findObject(By.scrollable(true)), 3000)
                if (list != null) {
                    val rect = list.visibleBounds
                    device.click(rect.centerX(), rect.top + (rect.height() / 5))
                    device.waitForIdle()
                }
            } catch (e: Exception) {
                // Ignore
            }

            // 8. UnifiedPlayerSheet Interaction
            openAndInteractWithPlayer()

            // 9. Workaround for "Waiting for app processes to flush profiles..." error (Xiaomi/Android 14+)
            // We manually signal the app to save the profile and then kill it.
            // This prevents the rule's internal dump command from triggering the verbose wait message.
            try {
                // Find PID
                val pidOutput = device.executeShellCommand("pidof $packageName").trim()
                if (pidOutput.isNotEmpty()) {
                    // pidof might return multiple PIDs, take the first
                    val pid = pidOutput.split("\\s+".toRegex()).first()

                    // Send SIGUSR1 (10) to force ART to save the profile to disk
                    device.executeShellCommand("kill -10 $pid")

                    // Wait for the flush to complete (2s should be enough)
                    Thread.sleep(2000)

                    // Force stop the app.
                    // When the rule subsequently calls `pm dump-profiles`, it should find the app dead
                    // and read the saved profile from disk without printing "Waiting for..."
                    device.executeShellCommand("am force-stop $packageName")
                }
            } catch (e: Exception) {
                // Log and ignore
                android.util.Log.e("BaselineProfile", "Failed to run flush workaround", e)
            }
        }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.handlePermissionDialogs() {
        val allowPatterns = listOf("Allow", "While using the app", "Permitir", "Continuar", "Aceptar")
        val pattern = Pattern.compile(allowPatterns.joinToString("|"), Pattern.CASE_INSENSITIVE)

        repeat(3) {
            val button = device.wait(Until.findObject(By.text(pattern)), 2000)
            if (button != null) {
                button.click()
                device.waitForIdle()
            }
        }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.handleOnboarding() {
        val nextPatterns = listOf("Next", "Continue", "Skip", "Done", "Get Started", "Siguiente", "Continuar", "Omitir", "Empezar")
        val pattern = Pattern.compile(nextPatterns.joinToString("|"), Pattern.CASE_INSENSITIVE)

        var attempts = 0
        while (attempts < 5) {
            val button = device.wait(Until.findObject(By.text(pattern)), 2000)
            if (button != null) {
                button.click()
                device.waitForIdle()
            } else {
                break
            }
            attempts++
        }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.scrollList() {
        val list = device.wait(Until.findObject(By.scrollable(true)), 2000) ?: return
        list.setGestureMargin(device.displayWidth / 10)

        list.fling(Direction.DOWN)
        device.waitForIdle()

        list.fling(Direction.UP)
        device.waitForIdle()
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.openAndInteractWithPlayer() {
        val playerTrigger = device.wait(Until.findObject(By.descContains("Carátula")), 2000)

        if (playerTrigger != null) {
            playerTrigger.click()
            device.waitForIdle()

            val queueBtn = device.wait(Until.findObject(By.descContains("Queue")), 2000)
            if (queueBtn != null) {
                queueBtn.click()
                device.waitForIdle()
                device.pressBack()
                device.waitForIdle()
            }

            device.pressBack()
            device.waitForIdle()
        }
    }
}
