package com.ivy.desktop.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.ivy.desktop.SampleData
import com.ivy.desktop.data.LedgerBuilder
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import kotlin.test.assertTrue

/**
 * Draws every page with [SampleData], light and dark, into build/screenshots. Mostly to catch a page
 * that crashes, and to look at the pages without a Windows machine.
 */
class ScreenshotTest {

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `every page draws`() {
        val ledger = LedgerBuilder.build(
            SampleData.backup(),
            SampleData.zone,
            SampleData.today.atTime(18, 0).toInstant(SampleData.zone),
        )
        val ready = LoadState.Ready(File("Ivy Wallet backup.zip"), LocalDateTime.of(2026, 10, 27, 6, 10), ledger)
        val out = File("build/screenshots").apply { mkdirs() }
        val screens = Page.entries.map { page -> page.name to Pair(page, ready as LoadState) } +
            ("Welcome" to Pair(Page.Overview, LoadState.NoFile))
        screens.forEach { (name, screen) ->
            listOf(false, true).forEach { dark ->
                val scene = ImageComposeScene(width = 1280, height = 1400, density = Density(1f)) {
                    IvyTheme(dark = dark) {
                        AppContent(
                            page = screen.first,
                            state = screen.second,
                            today = SampleData.today,
                            onPage = {},
                            onChooseFile = {},
                        )
                    }
                }
                // a second frame, after the first layout
                scene.render()
                val image = scene.render(nanoTime = 1_000_000_000)
                scene.close()
                val file = "$name-${if (dark) "dark" else "light"}"
                File(out, "$file.png").writeBytes(requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes)
                File(out, "$file.jpg").writeBytes(requireNotNull(image.encodeToData(EncodedImageFormat.JPEG, 80)).bytes)
            }
        }
        assertTrue(File(out, "Reports-light.png").length() > 0)
    }
}
