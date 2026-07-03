package dev.p2pchat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.p2pchat.*
import dev.p2pchat.message.TextMessage
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.ImageWriter
import javax.imageio.stream.ImageOutputStream

@Composable
fun ChatApp() {
    val config = remember { loadConfig() }
    LaunchedEffect(Unit) { Logger.setDebugEnabled(config.debugLogging()) }
    val storage = remember { ChatStorage(config.dbPath()) }
    val node = remember { P2PNode(config) }

    var selectedPeer by remember { mutableStateOf<StoredPeer?>(null) }
    var messages by remember { mutableStateOf<List<StoredMessage>>(emptyList()) }
    var peers by remember { mutableStateOf(storage.getPeers()) }
    var showSettings by remember { mutableStateOf(false) }
    var currentPeerId by remember { mutableStateOf<String?>(null) }
    var showYggWarning by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        Thread {
            if (!YggdrasilCheck.isRunning(config.yggdrasilHost(), config.yggdrasilPort())) {
                showYggWarning = true
                Logger.error("Yggdrasil not running on ${config.yggdrasilHost()}:${config.yggdrasilPort()}")
                return@Thread
            }
            try {
                node.startServer(config.serverPort())
                Logger.info("Server started on port " + config.serverPort())
            } catch (e: Exception) {
                Logger.error("Server start failed: " + e.message)
            }
        }.start()

        val storageListener = ChatStorageListener(storage, config.ownAddress())
        node.addListener(storageListener)

        val listener = object : P2PNodeListener {
            override fun onTextMessage(peerId: String, displayName: String, text: String, at: java.time.Instant) {
                java.awt.Toolkit.getDefaultToolkit().beep()
                peers = storage.getPeers()
                if (peerId == currentPeerId) {
                    messages = storage.getMessagesByPeer(peerId)
                }
            }
            override fun onImageMessage(peerId: String, displayName: String, image: ByteArray?, filename: String?, filePath: String?, at: java.time.Instant) {
                java.awt.Toolkit.getDefaultToolkit().beep()
                peers = storage.getPeers()
                if (peerId == currentPeerId) {
                    messages = storage.getMessagesByPeer(peerId)
                }
            }
            override fun onFileMessage(peerId: String, displayName: String, file: ByteArray?, filename: String?, extension: String?, filePath: String?, at: java.time.Instant) {
                java.awt.Toolkit.getDefaultToolkit().beep()
                peers = storage.getPeers()
                if (peerId == currentPeerId) {
                    messages = storage.getMessagesByPeer(peerId)
                }
            }
            override fun onMessageSent(peerId: String, payload: ByteArray?, ok: Boolean) {}
            override fun onMessageDelivered(timestamp: Long) {
                if (currentPeerId != null) {
                    messages = storage.getMessagesByPeer(currentPeerId!!)
                }
            }
            override fun onPeerConnected(peerId: String) {
                peers = storage.getPeers()
            }
            override fun onPeerDisconnected(peerId: String) {
                peers = storage.getPeers()
            }
        }
        node.addListener(listener)

        onDispose {
            node.removeListener(listener)
            node.removeListener(storageListener)
            try { node.close() } catch (_: Exception) {}
            try { storage.close() } catch (_: Exception) {}
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme()
    ) {
        Row(modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E))) {
            PeerListPanel(
                peers = peers,
                selectedPeer = selectedPeer,
                node = node,
                onPeerSelected = { peer ->
                    selectedPeer = peer
                    currentPeerId = peer.peerId
                    messages = storage.getMessagesByPeer(peer.peerId)
                },
                onAddPeer = { peerId, name ->
                    storage.savePeer(peerId, name)
                    peers = storage.getPeers()
                },
                onEditPeer = { peerId, newName ->
                    storage.updatePeerName(peerId, newName)
                    peers = storage.getPeers()
                },
                onDeletePeer = { peerId ->
                    storage.deleteMessagesByPeer(peerId)
                    storage.deletePeer(peerId)
                    peers = storage.getPeers()
                    if (currentPeerId == peerId) {
                        selectedPeer = null
                        currentPeerId = null
                        messages = emptyList()
                    }
                },
                onSettingsClick = { showSettings = true }
            )
            
            ChatPanel(
                messages = messages,
                ownAddress = config.ownAddress(),
                onSendMessage = { text ->
                    selectedPeer?.let { peer ->
                        Logger.debug("Sending text to ${peer.peerId}: $text")
                        storage.saveTextMessage(peer.peerId, config.ownAddress(), peer.peerId, System.currentTimeMillis(), text, "sent", config.displayName())
                        messages = storage.getMessagesByPeer(peer.peerId)
                        
                        if (peer.peerId != config.ownAddress()) {
                            Thread {
                                try {
                                    node.sendMessage(TextMessage(config.ownAddress(), peer.peerId, config.displayName(), text))
                                } catch (e: Exception) {
                                    Logger.error("Send error: ${e.message}")
                                }
                            }.start()
                        }
                    }
                },
                onSendFiles = { files ->
                    selectedPeer?.let { peer ->
                        val receivedDir = File("received")
                        receivedDir.mkdirs()
                        
                        for (file in files) {
                            val ext = file.extension.lowercase()
                            val isImage = ext in listOf("png", "jpg", "jpeg", "gif", "bmp")
                            
                            val storedFile = if (file.absolutePath.startsWith(receivedDir.absolutePath)) {
                                file
                            } else {
                                val dest = File(receivedDir, "${System.currentTimeMillis()}_${file.name}")
                                Files.copy(file.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
                                dest
                            }
                            
                            val rawData = Files.readAllBytes(storedFile.toPath())
                            val data = if (isImage) convertToJpeg(rawData) else rawData
                            
                            if (isImage) {
                                storage.saveImageMessage(peer.peerId, config.ownAddress(), peer.peerId, System.currentTimeMillis(), file.name, storedFile.absolutePath, config.displayName())
                            } else {
                                storage.saveFileMessage(peer.peerId, config.ownAddress(), peer.peerId, System.currentTimeMillis(), file.name, storedFile.absolutePath, config.displayName())
                            }
                            
                            if (peer.peerId != config.ownAddress()) {
                                Thread {
                                    try {
                                        if (isImage) {
                                            node.sendMessage(dev.p2pchat.message.ImageMessage(config.ownAddress(), peer.peerId, config.displayName(), data, file.name))
                                        } else {
                                            node.sendMessage(dev.p2pchat.message.FileMessage(config.ownAddress(), peer.peerId, config.displayName(), data, file.name, ext))
                                        }
                                    } catch (e: Exception) {
                                        Logger.error("File send error: ${e.message}")
                                    }
                                }.start()
                            }
                        }
                        messages = storage.getMessagesByPeer(peer.peerId)
                    }
                }
            )
        }
        
        if (showSettings) {
            SettingsDialog(
                config = config,
                onDismiss = { showSettings = false },
                onSave = { updatedConfig ->
                    try {
                        node.stop()
                        node.startServer(updatedConfig.serverPort())
                    } catch (_: Exception) {}
                }
            )
        }

        if (showYggWarning) {
            AlertDialog(
                onDismissRequest = { showYggWarning = false },
                title = { Text("Yggdrasil не запущен") },
                text = { Text("Запустите Yggdrasil и перезапустите приложение.\nАдрес: ${config.yggdrasilHost()}:${config.yggdrasilPort()}") },
                confirmButton = {
                    TextButton(onClick = { showYggWarning = false }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

private const val MAX_IMAGE_DIMENSION = 1920
private const val JPEG_QUALITY = 0.8f

private fun convertToJpeg(imageBytes: ByteArray): ByteArray {
    val img = ImageIO.read(ByteArrayInputStream(imageBytes)) ?: return imageBytes
    
    var width = img.width
    var height = img.height
    
    if (width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION) {
        val ratio = MAX_IMAGE_DIMENSION.toFloat() / maxOf(width, height)
        width = (width * ratio).toInt()
        height = (height * ratio).toInt()
    }
    
    val resized = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val g = resized.createGraphics()
    g.drawImage(img, 0, 0, width, height, null)
    g.dispose()
    
    val baos = ByteArrayOutputStream()
    val writer: ImageWriter = ImageIO.getImageWritersByFormatName("jpg").next()
    val params = writer.defaultWriteParam
    params.compressionMode = ImageWriteParam.MODE_EXPLICIT
    params.compressionQuality = JPEG_QUALITY
    
    val ios: ImageOutputStream = ImageIO.createImageOutputStream(baos)
    writer.output = ios
    writer.write(null, IIOImage(resized, null, null), params)
    writer.dispose()
    ios.close()
    
    return baos.toByteArray()
}

private fun loadConfig(): AppConfig {
    return try {
        AppConfig.load("config.yaml")
    } catch (e: Exception) {
        try {
            AppConfig.createDefault("config.yaml")
        } catch (ex: Exception) {
            throw RuntimeException("Failed to create config", ex)
        }
    }
}