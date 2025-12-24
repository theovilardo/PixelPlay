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
                // is_benchmark=true triggers automatic dummy song loading and player expansion in MainActivity/ViewModel
                device.executeShellCommand("am start -n $packageName/.MainActivity --ez is_benchmark true")

                // 3. Handle First Run / Permissions Dialogs / Onboarding
                handlePermissionDialogs()
                handleOnboarding()

                // 4. Wait for Player Sheet to Expand
                // The app should auto-expand the player. We check for "Queue" button which is only in the expanded sheet.
                val queueSelector = By.descContains("Queue")
                if (!device.wait(Until.hasObject(queueSelector), 10000)) {
                     // Retry handling onboarding if player didn't show immediately
                     handleOnboarding()
                     // Maybe player is collapsed (mini player)? Try to expand manually if auto-expand failed
                     val miniPlayer = device.wait(Until.findObject(By.descContains("Carátula")), 2000)
                     if (miniPlayer != null) miniPlayer.click()

                     device.wait(Until.hasObject(queueSelector), 5000)
                }
                device.waitForIdle()

                // 5. Interact with UnifiedPlayerSheet
                openAndInteractWithPlayer()

                // 6. Navigation: Collapse and go to Tabs (to profile navigation too)
                device.pressBack() // Collapse player
                device.waitForIdle()

                val tabs = listOf("Search", "Library", "Home")
                tabs.forEach { tabName ->
                    device.clickRetry(By.text(tabName))
                    device.waitForIdle()
                }

                // 7. Final Idle
                device.pressHome()
                device.waitForIdle(2000)
            }
        } catch (e: IllegalStateException) {
            // Known issue on some devices (Xiaomi/Android 14+) where pm dump-profiles prints "Waiting..."
            // causing the parser to fail even if the flush was successful.
            if (e.message?.contains("Waiting for app processes to flush profiles") == true) {
                android.util.Log.w("BaselineProfileGenerator", "Suppressed dump output error: ${e.message}")

                try {
                    val srcPath = "/data/misc/profman/$packageName-primary.prof.txt"
                    val context = InstrumentationRegistry.getInstrumentation().targetContext
                    val destDir = context.getExternalFilesDir(null)
                    val destFile = File(destDir, "BaselineProfileGenerator_generate-baseline-prof.txt")
                    val device = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                    val cmd = "cp $srcPath ${destFile.absolutePath}"
                    val output = device.executeShellCommand(cmd)
                    android.util.Log.i("BaselineProfileGenerator", "Manually copied profile to ${destFile.absolutePath}. Output: $output")
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
                val obj = wait(Until.findObject(selector), 2000)
                if (obj != null) {
                    obj.click()
                    return
                } else {
                    return
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

    private fun androidx.benchmark.macro.MacrobenchmarkScope.openAndInteractWithPlayer() {
        val queueSelector = By.descContains("Queue")

        if (device.hasObject(queueSelector)) {
            // Interact
            val playPausePattern = Pattern.compile(".*(Play|Pause).*", Pattern.CASE_INSENSITIVE)
            val playPauseSelector = By.desc(playPausePattern)

            repeat(3) {
                 device.clickRetry(playPauseSelector)
                 device.waitForIdle()
                 Thread.sleep(800)
            }

            val height = device.displayHeight
            val width = device.displayWidth
            device.swipe((width * 0.8).toInt(), (height * 0.4).toInt(), (width * 0.2).toInt(), (height * 0.4).toInt(), 30)
            device.waitForIdle()

            device.clickRetry(queueSelector)
            device.waitForIdle()

            try {
                val list = device.wait(Until.findObject(By.scrollable(true)), 2000)
                list?.fling(Direction.DOWN)
            } catch (e: Exception) {}

            device.pressBack()
            device.waitForIdle()
        }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.scrollList() {
        try {
            val list = device.wait(Until.findObject(By.scrollable(true)), 2000) ?: return
            list.setGestureMargin(device.displayWidth / 10)
            list.fling(Direction.DOWN)
            device.waitForIdle()
        } catch (e: Exception) {}
    }
}
