package dev.p2pchat.ui

import dev.p2pchat.Logger
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.p2pchat.AppConfig

@Composable
private fun ExpandableSection(
    title: String,
    initiallyExpanded: Boolean = false,
    hint: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Color(0xFF4EC9B0),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (expanded) "▼" else "▶",
                color = Color(0xFF888888)
            )
        }
        HorizontalDivider()
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                if (hint != null) {
                    Text(hint, color = Color(0xFF888888), style = MaterialTheme.typography.bodySmall)
                }
                content()
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
fun SettingsDialog(
    config: AppConfig,
    onDismiss: () -> Unit,
    onSave: (AppConfig) -> Unit
) {
    var host by remember { mutableStateOf(config.yggdrasilHost()) }
    var yggPort by remember { mutableStateOf(config.yggdrasilPort().toString()) }
    var serverPort by remember { mutableStateOf(config.serverPort().toString()) }
    var address by remember { mutableStateOf(config.ownAddress()) }
    var displayName by remember { mutableStateOf(config.displayName()) }
    var imageLimit by remember { mutableStateOf(config.imageLimitMb().toString()) }
    var fileLimit by remember { mutableStateOf(config.fileLimitMb().toString()) }
    var storageLimit by remember { mutableStateOf(config.storageLimitMb().toString()) }
    var dbPath by remember { mutableStateOf(config.dbPath()) }
    var debugLogging by remember { mutableStateOf(config.debugLogging()) }

    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                ExpandableSection(title = "Профиль", initiallyExpanded = true) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Ваш никнейм") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Свой адрес:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("Адрес") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { clipboard.setText(AnnotatedString(address)) }
                        ) {
                            Text("📋")
                        }
                    }
                }

                ExpandableSection(title = "Лимиты") {
                    OutlinedTextField(
                        value = imageLimit,
                        onValueChange = { imageLimit = it },
                        label = { Text("Лимит фото (MB)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = fileLimit,
                        onValueChange = { fileLimit = it },
                        label = { Text("Лимит файлов (MB)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = storageLimit,
                        onValueChange = { storageLimit = it },
                        label = { Text("Лимит хранилища (MB)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                ExpandableSection(
                    title = "Техническая часть",
                    hint = "Изменение этих параметров может нарушить работу приложения"
                ) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Yggdrasil хост") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = yggPort,
                        onValueChange = { yggPort = it },
                        label = { Text("Yggdrasil порт") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = serverPort,
                        onValueChange = { serverPort = it },
                        label = { Text("Порт сервера") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = dbPath,
                        onValueChange = { dbPath = it },
                        label = { Text("Путь к БД") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text("Debug логи", modifier = Modifier.weight(1f))
                        Switch(
                            checked = debugLogging,
                            onCheckedChange = { debugLogging = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                config.setYggdrasilHost(host)
                config.setYggdrasilPort(yggPort.toIntOrNull() ?: config.yggdrasilPort())
                config.setServerPort(serverPort.toIntOrNull() ?: config.serverPort())
                config.setOwnAddress(address)
                config.setDisplayName(displayName)
                config.setImageLimitMb(imageLimit.toIntOrNull() ?: config.imageLimitMb())
                config.setFileLimitMb(fileLimit.toIntOrNull() ?: config.fileLimitMb())
                config.setStorageLimitMb(storageLimit.toIntOrNull() ?: config.storageLimitMb())
                config.setDbPath(dbPath)
                config.setDebugLogging(debugLogging)
                Logger.setDebugEnabled(debugLogging)
                try {
                    config.save("config.yaml")
                } catch (e: Exception) {
                    Logger.error("Config save error: " + e.message)
                }
                onSave(config)
                onDismiss()
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
