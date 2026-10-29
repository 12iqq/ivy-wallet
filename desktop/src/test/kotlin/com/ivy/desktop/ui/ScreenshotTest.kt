package com.ivy.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ivy.desktop.SampleData
import com.ivy.desktop.data.LedgerBuilder
import com.ivy.desktop.reports.Format
import com.ivy.desktop.reports.Reports
import com.ivy.desktop.reports.Span
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.skia.Image
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
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
        val file = File("Ivy Wallet backup.zip")
        val ready = LoadState.Ready(
            name = file.name,
            place = BackupPlace.Computer(file),
            savedAt = LocalDateTime.of(2026, 10, 27, 6, 10),
            ledger = ledger,
            problem = null,
        )
        val inDrive = ready.copy(
            name = "ivywallet-uae.zip",
            place = BackupPlace.GoogleDrive,
            problem = "Couldn't reach Google Drive, so there may be a newer backup.",
        )
        val driveGone = LoadState.Failed(
            name = "ivywallet-uae.zip",
            place = BackupPlace.GoogleDrive,
            message = "Ivy Wallet UAE may not open your Google Drive anymore. Connect it again to see your backup.",
        )
        // an app built without its registration with Google, and one with it
        val withoutGoogle =
            BackupActions(chooseFile = {}, connectDrive = null, disconnectDrive = {}, cancelConnect = {})
        val withGoogle = BackupActions(chooseFile = {}, connectDrive = {}, disconnectDrive = {}, cancelConnect = {})
        val out = File("build/screenshots").apply { mkdirs() }
        val screens = Page.entries.map { page -> Triple(page.name, page to (ready as LoadState), withoutGoogle) } +
            listOf(
                Triple("Welcome", Page.Overview to LoadState.NoFile, withoutGoogle),
                Triple("Welcome-Google", Page.Overview to LoadState.NoFile, withGoogle),
                Triple("Connecting", Page.Overview to LoadState.Connecting, withGoogle),
                Triple("Google-Drive", Page.Overview to inDrive, withGoogle),
                Triple("Google-Drive-gone", Page.Overview to driveGone, withGoogle),
            )
        screens.forEach { (name, screen, actions) ->
            listOf(false, true).forEach { dark ->
                val scene = ImageComposeScene(width = 1280, height = 1400, density = Density(1f)) {
                    IvyTheme(dark = dark) {
                        AppContent(
                            page = screen.first,
                            state = screen.second,
                            today = SampleData.today,
                            actions = actions,
                            onPage = {},
                        )
                    }
                }
                // a second frame, after the first layout
                scene.render()
                val image = scene.render(nanoTime = 1_000_000_000)
                scene.close()
                val file = "$name-${if (dark) "dark" else "light"}"
                File(out, "$file.png").writeBytes(requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes)
                // half size, small enough to print into the CI log
                val small = Surface.makeRasterN32Premul(image.width / 2, image.height / 2)
                small.canvas.drawImageRect(image, Rect.makeWH(image.width / 2f, image.height / 2f))
                val smallJpg = small.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, 70)
                File(out, "$file-small.jpg").writeBytes(requireNotNull(smallJpg).bytes)
            }
        }
        assertTrue(File(out, "Reports-light.png").length() > 0)
    }

    /** The overview's charts with the mouse over July: the tip with the month's amounts shows. */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `charts show the amounts under the mouse`() {
        val ledger = LedgerBuilder.build(
            SampleData.backup(),
            SampleData.zone,
            SampleData.today.atTime(18, 0).toInstant(SampleData.zone),
        )
        val overview = Reports.overview(ledger, SampleData.today, allTime = false)
        val months = overview.months.map { Format.month(it.month.atDay(1)) }.toImmutableList()
        val out = File("build/screenshots").apply { mkdirs() }
        // the chart starts 20 px in; its plot from x = 68 to 880, 8 px below the top
        val julyBar = Offset(68 + 812f / 12 * 8.5f, 120f)
        val julyPoint = Offset(68 + 812f / 11 * 8, 120f)
        listOf("bars" to julyBar, "line" to julyPoint).forEach { (chart, mouse) ->
            listOf(false, true).forEach { dark ->
                val scene = ImageComposeScene(width = 900, height = 300, density = Density(1f)) {
                    IvyTheme(dark = dark) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(20.dp),
                        ) {
                            if (chart == "bars") {
                                BarChart(
                                    series = persistentListOf(
                                        overview.months.map { it.income }.toImmutableList(),
                                        overview.months.map { it.expense }.toImmutableList(),
                                    ),
                                    colors = persistentListOf(Green, Red),
                                    labels = months,
                                    tips = incomeSpendingTips(overview, "AED", SampleData.today),
                                )
                            } else {
                                LineChart(
                                    lines = persistentListOf(
                                        ChartLine(
                                            values = overview.netWorth.map { it.value }.toImmutableList(),
                                            color = Ivy,
                                            dashed = false,
                                            filled = true,
                                        ),
                                    ),
                                    length = overview.netWorth.size,
                                    labels = months,
                                    tips = balanceTips(overview, "AED", SampleData.today),
                                )
                            }
                        }
                    }
                }
                scene.render()
                scene.sendPointerEvent(PointerEventType.Enter, mouse)
                scene.sendPointerEvent(PointerEventType.Move, mouse)
                scene.render(nanoTime = 500_000_000)
                val image = scene.render(nanoTime = 1_000_000_000)
                scene.close()
                save(image, File(out, "Chart-$chart-${if (dark) "dark" else "light"}.png"))
            }
        }
        assertTrue(File(out, "Chart-bars-light.png").length() > 0)
    }

    /** The Overview and Reports set to all time. */
    @Test
    fun `all time pages draw`() {
        val ledger = LedgerBuilder.build(
            SampleData.backup(),
            SampleData.zone,
            SampleData.today.atTime(18, 0).toInstant(SampleData.zone),
        )
        val out = File("build/screenshots").apply { mkdirs() }
        listOf("Overview-all-time", "Reports-all-time").forEach { name ->
            val scene = ImageComposeScene(width = 1280, height = 1400, density = Density(1f)) {
                IvyTheme(dark = false) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        if (name.startsWith("Overview")) {
                            OverviewPage(ledger, SampleData.today, startWithAllTime = true)
                        } else {
                            ReportsPage(ledger, SampleData.today, startSpan = Span.AllTime)
                        }
                    }
                }
            }
            scene.render()
            val image = scene.render(nanoTime = 1_000_000_000)
            scene.close()
            save(image, File(out, "$name-light.png"))
        }
        assertTrue(File(out, "Reports-all-time-light.png").length() > 0)
    }

    private fun save(image: Image, file: File) {
        file.writeBytes(requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes)
    }
}
