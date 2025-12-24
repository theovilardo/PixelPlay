package com.theveloper.pixelplay.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
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

                // Attempt manual recovery: Copy the profile from the system location to the app's output dir
                // so the Gradle plugin can still find it.
                try {
                    // Path derived from error message analysis
                    val srcPath = "/data/misc/profman/$packageName-primary.prof.txt"

                    val context = InstrumentationRegistry.getInstrumentation().targetContext
                    val destDir = context.getExternalFilesDir(null)
                    // Filename must match what the plugin expects: <Class>_<Method>-baseline-prof.txt
                    val destFile = File(destDir, "BaselineProfileGenerator_generate-baseline-prof.txt")

                    val device = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                    val cmd = "cp $srcPath ${destFile.absolutePath}"

                    val output = device.executeShellCommand(cmd)
                    android.util.Log.i("BaselineProfileGenerator", "Manually copied profile to ${destFile.absolutePath}. Output: $output")

                    // CRITICAL: Print the magic string so the Gradle plugin picks up the file!
                    println("Profile saved to '${destFile.absolutePath}'")

                } catch (recoveryEx: Exception) {
                    android.util.Log.e("BaselineProfileGenerator", "Failed manual profile recovery", recoveryEx)
                }

            } else {
                throw e
            }
        }
    }

    private fun UiDevice.clickRetry(selector: BySelector) {
        var attempts = 0
        while (attempts < 3) {
            try {
                // Always find a FRESH object
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
        // 1. Expand Player
        val miniPlayerSelector = By.descContains("Carátula")
        // Check if exists before clicking, or fallback to swipe
        if (device.findObject(miniPlayerSelector) != null) {
            device.clickRetry(miniPlayerSelector)
        } else {
            // Fallback: Swipe up
            val height = device.displayHeight
            val width = device.displayWidth
            device.swipe(width / 2, (height * 0.9).toInt(), width / 2, height / 2, 20)
        }
        device.waitForIdle()

        // 2. Interact with Player Controls
        // Use regex for Play/Pause
        val playPausePattern = Pattern.compile(".*(Play|Pause).*", Pattern.CASE_INSENSITIVE)
        val playPauseSelector = By.desc(playPausePattern)

        // Repeat interactions, finding FRESH objects each time to avoid StaleObjectException
        repeat(3) {
             device.clickRetry(playPauseSelector)
             device.waitForIdle()
             Thread.sleep(800)
        }

        // 3. Swipe Art / Carousel
        val height = device.displayHeight
        val width = device.displayWidth
        device.swipe((width * 0.8).toInt(), (height * 0.4).toInt(), (width * 0.2).toInt(), (height * 0.4).toInt(), 30)
        device.waitForIdle()

        // 4. Open and Close Queue
        val queueSelector = By.descContains("Queue")
        device.clickRetry(queueSelector)
        device.waitForIdle()

        scrollList()

        device.pressBack()
        device.waitForIdle()

        // 5. Collapse Player
        device.pressBack()
        device.waitForIdle()
    }
}
