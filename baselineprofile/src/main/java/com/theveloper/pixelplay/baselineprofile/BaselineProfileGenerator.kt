package com.theveloper.pixelplay.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
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
                device.clickRetry(By.text(tabName))
                device.waitForIdle()
            }

            // 6. List Interaction (Library)
            device.clickRetry(By.text("Library"))
            device.waitForIdle()
            scrollList()

            // 7. Open Detail / Play Song
            try {
                val list = device.wait(Until.findObject(By.scrollable(true)), 3000)
                if (list != null) {
                    val rect = list.visibleBounds
                    // Click by coordinates to avoid StaleObjectException on list items
                    device.click(rect.centerX(), rect.top + (rect.height() / 5))
                    device.waitForIdle()
                }
            } catch (e: Exception) {
                // Ignore
            }

            // 8. UnifiedPlayerSheet Interaction
            openAndInteractWithPlayer()

            // 9. Workaround for Xiaomi/Android 14+ profile flush error
            try {
                val pidOutput = device.executeShellCommand("pidof $packageName").trim()
                if (pidOutput.isNotEmpty()) {
                    val pid = pidOutput.split("\\s+".toRegex()).first()
                    device.executeShellCommand("kill -10 $pid")
                    Thread.sleep(2000)
                    device.executeShellCommand("am force-stop $packageName")
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun UiDevice.clickRetry(selector: BySelector) {
        var attempts = 0
        while (attempts < 3) {
            try {
                val obj = wait(Until.findObject(selector), 2000)
                if (obj != null) {
                    obj.click()
                    return
                } else {
                    return // Object not found
                }
            } catch (e: StaleObjectException) {
                attempts++
            }
        }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.handlePermissionDialogs() {
        val allowPatterns = listOf("Allow", "While using the app", "Permitir", "Continuar", "Aceptar")
        val pattern = Pattern.compile(allowPatterns.joinToString("|"), Pattern.CASE_INSENSITIVE)

        repeat(3) {
            device.clickRetry(By.text(pattern))
            device.waitForIdle()
        }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.handleOnboarding() {
        val nextPatterns = listOf("Next", "Continue", "Skip", "Done", "Get Started", "Siguiente", "Continuar", "Omitir", "Empezar")
        val pattern = Pattern.compile(nextPatterns.joinToString("|"), Pattern.CASE_INSENSITIVE)

        var attempts = 0
        while (attempts < 5) {
            // Check if exists first to break loop
            if (device.findObject(By.text(pattern)) == null) break

            device.clickRetry(By.text(pattern))
            device.waitForIdle()
            attempts++
        }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.scrollList() {
        try {
            val list = device.wait(Until.findObject(By.scrollable(true)), 2000) ?: return
            list.setGestureMargin(device.displayWidth / 10)

            list.fling(Direction.DOWN)
            device.waitForIdle()

            list.fling(Direction.UP)
            device.waitForIdle()
        } catch (e: StaleObjectException) {
            // Ignore scrolling errors
        }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.openAndInteractWithPlayer() {
        // Try to expand player
        // Use clickRetry to handle potential UI updates
        device.clickRetry(By.descContains("Carátula"))
        device.waitForIdle()

        // Interact with Queue
        device.clickRetry(By.descContains("Queue"))
        device.waitForIdle()

        // Close Queue (Back)
        device.pressBack()
        device.waitForIdle()

        // Collapse Player (Back)
        device.pressBack()
        device.waitForIdle()
    }
}
