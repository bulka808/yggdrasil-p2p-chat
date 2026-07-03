package dev.p2pchat

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.p2pchat.ui.ChatApp

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "P2P Chat"
    ) {
        ChatApp()
    }
}