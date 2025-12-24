package com.theveloper.pixelplay.baselineprofile

import android.content.Intent
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

            // 2. Start the Activity
            startActivityAndWait { intent ->
                intent.putExtra("is_benchmark", true)
            }

            // 3. Handle First Run / Permissions Dialogs / Onboarding
            handlePermissionDialogs()
            handleOnboarding()

            // 4. Wait for Home Screen Content
            // Increased timeout to 15s to ensure cold start completes
            if (!device.wait(Until.hasObject(By.text("Home")), 15000)) {
                // If not found, try handling onboarding one more time or fail
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
                // Wait for a scrollable list to appear
                val list = device.wait(Until.findObject(By.scrollable(true)), 3000)
                if (list != null) {
                    val rect = list.visibleBounds
                    // Click 20% down from the top to hit an item
                    device.click(rect.centerX(), rect.top + (rect.height() / 5))
                    device.waitForIdle()
                }
            } catch (e: Exception) {
                // Ignore
            }

            // 8. UnifiedPlayerSheet Interaction
            openAndInteractWithPlayer()

            // 9. Final Idle
            device.pressHome()
            device.waitForIdle(2000)
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

        // Try to click through potential onboarding screens
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
        // Try to find the MiniPlayer artwork or container to expand
        val playerTrigger = device.wait(Until.findObject(By.descContains("Carátula")), 2000)

        if (playerTrigger != null) {
            playerTrigger.click()
            device.waitForIdle()

            // Interact with Queue button
            val queueBtn = device.wait(Until.findObject(By.descContains("Queue")), 2000)
            if (queueBtn != null) {
                queueBtn.click()
                device.waitForIdle()
                device.pressBack() // Close queue
                device.waitForIdle()
            }

            // Collapse the player
            device.pressBack()
            device.waitForIdle()
        }
    }
}
