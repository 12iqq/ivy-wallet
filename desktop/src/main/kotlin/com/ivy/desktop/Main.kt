package com.ivy.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.ivy.desktop.data.DesktopSettings
import com.ivy.desktop.ui.DesktopApp
import com.ivy.desktop.ui.IvyTheme
import org.jetbrains.skia.Image
import java.awt.Dimension

fun main(): Unit = application {
    val settings = remember { DesktopSettings() }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Ivy Wallet UAE",
        icon = remember { appIcon() },
        state = rememberWindowState(
            size = DpSize(1280.dp, 840.dp),
            position = WindowPosition(Alignment.Center),
        ),
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(MinWidth, MinHeight)
        }
        IvyTheme {
            DesktopApp(settings)
        }
    }
}

private fun appIcon(): Painter? = DesktopSettings::class.java.getResourceAsStream("/icon.png")?.use { stream ->
    BitmapPainter(Image.makeFromEncoded(stream.readBytes()).toComposeImageBitmap())
}

private const val MinWidth = 1000
private const val MinHeight = 700
