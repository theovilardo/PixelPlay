package com.theveloper.pixelplay.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.Closeable
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val STARTUP_TIMEOUT_MS = 15_000L
private const val SHORT_WAIT_MS = 2_000L

/**
 * Baseline profile generator for PixelPlay.
 *
 * Run with `./gradlew :app:generateReleaseBaselineProfile` (or the Android Studio
 * run configuration) on a device that already has library data so the flows below
 * exercise the real UI: cold start, tab navigation, player sheet interactions,
 * queue/lyrics/cast sheets, and list scrolling.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        val targetAppId = InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: throw Exception("targetAppId not passed as instrumentation runner arg")

        markSetupComplete()

        rule.collect(
            packageName = targetAppId,
            includeInStartupProfile = true
        ) {
            launchToHome(targetAppId)
            exploreMainTabs()
            openPlayerAndSheets()
        }
    }
}

private fun markSetupComplete() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val dataStore = PreferenceDataStoreFactory.createWithPath(
        produceFile = { context.preferencesDataStoreFile("settings").absolutePath.toPath() },
    )
    runBlocking {
        dataStore.edit { prefs ->
            prefs[booleanPreferencesKey("initial_setup_done")] = true
        }
    }
    (dataStore as Closeable).close()
}

private fun androidx.benchmark.macro.MacrobenchmarkScope.launchToHome(packageName: String) {
    pressHome()
    startActivityAndWait()

    device.wait(Until.hasObject(By.pkg(packageName)), STARTUP_TIMEOUT_MS)
    device.handlePermissionDialogs()
    waitForBottomNav()
}

private fun androidx.benchmark.macro.MacrobenchmarkScope.exploreMainTabs() {
    clickTab("Search")
    device.waitForIdle()
    clickTab("Library")
    device.waitForIdle()
    scrollPrimaryLists()
    openAnyDetailAndReturn()
    clickTab("Home")
}

private fun androidx.benchmark.macro.MacrobenchmarkScope.openPlayerAndSheets() {
    if (!expandPlayerSheet()) return

    val queueButton = device.wait(Until.findObject(By.descContains("Queue")), SHORT_WAIT_MS)
    queueButton?.click()
    device.waitForIdle()
    scrollPrimaryLists()
    device.pressBack()

    val lyricsButton = device.wait(Until.findObject(By.descContains("Lyrics")), SHORT_WAIT_MS)
    lyricsButton?.click()
    device.waitForIdle()
    device.pressBack()

    val castButton = device.wait(Until.findObject(By.descContains("Cast")), SHORT_WAIT_MS)
    castButton?.click()
    device.waitForIdle()
    device.pressBack()

    device.pressBack() // Collapse player sheet if expanded
}

private fun androidx.benchmark.macro.MacrobenchmarkScope.expandPlayerSheet(): Boolean {
    val directButtons = listOf(
        By.descContains("Queue"),
        By.descContains("Lyrics")
    )
    if (directButtons.any { selector -> device.hasObject(selector) }) {
        return true
    }

    device.findObject(By.descContains("Carátula"))?.click()
    device.wait(Until.hasObject(By.descContains("Queue")), SHORT_WAIT_MS)

    if (directButtons.any { selector -> device.hasObject(selector) }) return true

    val width = device.displayWidth
    val height = device.displayHeight
    device.swipe(width / 2, (height * 0.9).toInt(), width / 2, (height * 0.3).toInt(), 24)
    return directButtons.any { selector -> device.wait(Until.hasObject(selector), SHORT_WAIT_MS) }
}

private fun androidx.benchmark.macro.MacrobenchmarkScope.clickTab(label: String) {
    device.findObject(By.text(label))?.click()
    device.waitForIdle()
}

private fun androidx.benchmark.macro.MacrobenchmarkScope.waitForBottomNav() {
    device.wait(Until.hasObject(By.text("Home")), STARTUP_TIMEOUT_MS)
    device.waitForIdle()
}

private fun androidx.benchmark.macro.MacrobenchmarkScope.scrollPrimaryLists() {
    val scrollables = device.findObjects(By.scrollable(true).pkg(device.currentPackageName))
    val target = scrollables.firstOrNull()
    target?.fling(Direction.DOWN)
    target?.fling(Direction.UP)
}

private fun androidx.benchmark.macro.MacrobenchmarkScope.openAnyDetailAndReturn() {
    val candidates = device.findObjects(By.clickable(true).pkg(device.currentPackageName))
    val detail = candidates.firstOrNull { obj ->
        val text = obj.text ?: obj.contentDescription
        text != null && text !in setOf("Home", "Search", "Library")
    }
    if (detail != null) {
        detail.click()
        device.waitForIdle()
        device.pressBack()
    }
}

private fun UiDevice.handlePermissionDialogs() {
    val allowButtons = listOf(
        "Allow",
        "Allow all the time",
        "Allow only while using the app",
        "While using the app",
        "Permitir",
        "Continuar"
    )
    repeat(3) {
        val button = allowButtons.firstNotNullOfOrNull { text -> findObject(By.textContains(text)) }
        if (button != null) {
            button.click()
            waitForIdle()
        }
    }
}
