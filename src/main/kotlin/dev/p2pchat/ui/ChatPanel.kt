package dev.p2pchat.ui

import dev.p2pchat.Logger
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.p2pchat.StoredMessage
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.File
import java.time.Instant
import javax.imageio.ImageIO
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun ChatPanel(
    messages: List<StoredMessage>,
    ownAddress: String = "",
    onSendMessage: (String) -> Unit,
    onSendFiles: (List<File>) -> Unit = {}
) {
    var inputText by remember { mutableStateOf("") }
    var pendingFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    val listState = rememberLazyListState()

    fun pasteFromClipboard() {
        try {
            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            if (clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
                val image = clipboard.getData(DataFlavor.imageFlavor) as? BufferedImage
                if (image != null) {
                    val receivedDir = File("received")
                    receivedDir.mkdirs()
                    val tempFile = File(receivedDir, "paste_${System.currentTimeMillis()}.jpg")
                    
                    var width = image.width
                    var height = image.height
                    if (width > 1920 || height > 1920) {
                        val ratio = 1920f / maxOf(width, height)
                        width = (width * ratio).toInt()
                        height = (height * ratio).toInt()
                    }
                    val resized = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB)
                    val g = resized.createGraphics()
                    g.drawImage(image, 0, 0, width, height, null)
                    g.dispose()
                    
                    ImageIO.write(resized, "jpg", tempFile)
                    pendingFiles = pendingFiles + tempFile
                    return
                }
            }
            if (clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)) {
                @Suppress("UNCHECKED_CAST")
                val files = clipboard.getData(DataFlavor.javaFileListFlavor) as? List<File>
                if (files != null && files.isNotEmpty()) {
                    pendingFiles = pendingFiles + files
                }
            }
        } catch (e: Exception) {
            Logger.error("Paste error: ${e.message}")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.V && event.isCtrlPressed) {
                    pasteFromClipboard()
                    true
                } else false
            }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                MessageBubble(message, ownAddress)
            }
        }

        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
        
        if (pendingFiles.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .background(Color(0xFF2D2D2D), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pendingFiles.forEach { file ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (file.name.matches(Regex(".*\\.(png|jpg|jpeg|gif|bmp)", RegexOption.IGNORE_CASE))) "📷" else "📄",
                            fontSize = 24.sp
                        )
                        Text(text = file.name, color = Color(0xFFD4D4D4), fontSize = 10.sp, maxLines = 1)
                        TextButton(onClick = { pendingFiles = pendingFiles - file }, contentPadding = PaddingValues(0.dp)) {
                            Text("✕", color = Color.Red, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed) {
                            if (pendingFiles.isNotEmpty()) { onSendFiles(pendingFiles); pendingFiles = emptyList() }
                            if (inputText.isNotBlank()) { onSendMessage(inputText); inputText = "" }
                            true
                        } else false
                    },
                placeholder = { Text("Введите сообщение...") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                val chooser = JFileChooser()
                chooser.fileFilter = FileNameExtensionFilter("Изображения и файлы", "png", "jpg", "jpeg", "gif", "bmp", "pdf", "txt", "doc", "docx")
                chooser.isMultiSelectionEnabled = true
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    pendingFiles = pendingFiles + chooser.selectedFiles.toList()
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))) { Text("📎") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                if (pendingFiles.isNotEmpty()) { onSendFiles(pendingFiles); pendingFiles = emptyList() }
                if (inputText.isNotBlank()) { onSendMessage(inputText); inputText = "" }
            }) { Text("Отправить") }
        }
    }
}

@Composable
fun MessageBubble(message: StoredMessage, ownAddress: String = "") {
    var showFullImage by remember { mutableStateOf(false) }
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    val time = timeFormatter.format(Instant.ofEpochMilli(message.timestamp()))
    val isSelf = message.fromId() == ownAddress
    val typeId = message.typeId()
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isSelf) Arrangement.End else Arrangement.Start) {
        Column(modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(8.dp)).background(if (isSelf) Color(0xFF264F78) else Color(0xFF2D2D2D)).padding(8.dp)) {
            val senderName = message.senderName()
            if (senderName.isNotBlank()) {
                Text(text = senderName, color = Color(0xFF4EC9B0), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            when (typeId) {
                1 -> Text(text = message.textContent() ?: "", color = Color(0xFFD4D4D4))
                2 -> {
                    val filePath = message.filePath()
                    if (filePath != null && filePath.isNotBlank()) {
                        val imgFile = File(filePath)
                        if (imgFile.exists()) {
                            val imageBitmap = remember(filePath) {
                                try {
                                    val bytes = imgFile.readBytes()
                                    bytes.decodeToImageBitmap()
                                } catch (e: Exception) { null }
                            }
                            if (imageBitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = imageBitmap,
                                    contentDescription = message.filename(),
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).clickable { showFullImage = true },
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Text(text = "📷 ${message.filename() ?: "изображение"}", color = Color(0xFF4EC9B0))
                            }
                        } else {
                            Text(text = "📷 ${message.filename() ?: "изображение"}", color = Color(0xFF4EC9B0))
                        }
                    } else {
                        Text(text = "📷 ${message.filename() ?: "изображение"}", color = Color(0xFF4EC9B0))
                    }
                }
                3 -> Text(text = "📄 ${message.filename() ?: "файл"}", color = Color(0xFFDCDCAA))
            }
            Row(modifier = Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = time, color = Color(0xFF888888), fontSize = 11.sp)
                if (isSelf) {
                    val status = message.status() ?: "sent"
                    val statusText = when (status) {
                        "delivered" -> "✓✓"
                        "read" -> "✓✓"
                        else -> "✓"
                    }
                    val statusColor = when (status) {
                        "delivered" -> Color(0xFF888888)
                        "read" -> Color(0xFF4EC9B0)
                        else -> Color(0xFF888888)
                    }
                    Text(text = statusText, color = statusColor, fontSize = 10.sp)
                }
            }
        }
    }
    if (showFullImage) {
        FullScreenImageViewer(
            filePath = message.filePath(),
            onDismiss = { showFullImage = false }
        )
    }
}

@Composable
fun FullScreenImageViewer(filePath: String?, onDismiss: () -> Unit) {
    if (filePath == null || filePath.isBlank()) return
    val imgFile = File(filePath)
    if (!imgFile.exists()) return

    val imageBitmap = remember(filePath) {
        try {
            val bytes = imgFile.readBytes()
            bytes.decodeToImageBitmap()
        } catch (e: Exception) { null }
    }

    if (imageBitmap == null) return

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll) {
                                val change = event.changes.first()
                                val delta = change.scrollDelta.y
                                scale = (scale - delta * 0.5f).coerceIn(0.25f, 5f)
                                change.consume()
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    },
                    contentScale = ContentScale.Fit
                )
            }
            Text(
                text = "✕",
                color = Color.White,
                fontSize = 24.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).clickable { onDismiss() }
            )
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { scale = (scale - 0.25f).coerceAtLeast(0.25f) }) {
                    Text("−")
                }
                Text(
                    text = "🔍 ${(scale * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                Button(onClick = { scale = (scale + 0.25f).coerceAtMost(5f) }) {
                    Text("+")
                }
                Button(onClick = { scale = 1f; offsetX = 0f; offsetY = 0f }) {
                    Text("⟲")
                }
            }
        }
    }
}