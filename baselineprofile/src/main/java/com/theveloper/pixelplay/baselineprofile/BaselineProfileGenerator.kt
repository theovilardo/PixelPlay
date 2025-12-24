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

        try {
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
        } catch (e: IllegalStateException) {
            // Known issue on some devices (Xiaomi/Android 14+) where pm dump-profiles prints "Waiting..."
            // causing the parser to fail even if the flush was successful.
            if (e.message?.contains("Waiting for app processes to flush profiles") == true) {
                android.util.Log.w("BaselineProfileGenerator", "Suppressed dump output error: ${e.message}")
            } else {
                throw e
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
        device.clickRetry(By.descContains("Carátula"))
        device.waitForIdle()

        device.clickRetry(By.descContains("Queue"))
        device.waitForIdle()

        device.pressBack()
        device.waitForIdle()

        device.pressBack()
        device.waitForIdle()
    }
}
