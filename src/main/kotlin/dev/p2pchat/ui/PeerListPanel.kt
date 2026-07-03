package dev.p2pchat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.p2pchat.P2PNode
import dev.p2pchat.StoredPeer

@Composable
fun PeerListPanel(
    peers: List<StoredPeer>,
    selectedPeer: StoredPeer?,
    node: P2PNode,
    onPeerSelected: (StoredPeer) -> Unit,
    onAddPeer: (String, String) -> Unit,
    onEditPeer: (String, String) -> Unit,
    onDeletePeer: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPeer by remember { mutableStateOf<StoredPeer?>(null) }
    
    Column(
        modifier = Modifier
            .width(200.dp)
            .fillMaxHeight()
            .background(Color(0xFF252526))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Пиры",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onSettingsClick) {
                Text("⚙")
            }
        }
        
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(peers) { peer ->
                PeerItem(
                    peer = peer,
                    isSelected = peer == selectedPeer,
                    isOnline = node.isConnected(peer.peerId()),
                    onClick = { onPeerSelected(peer) },
                    onLongClick = { editingPeer = peer }
                )
            }
        }
        
        Button(
            onClick = { showAddDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("+ Пир")
        }
    }
    
    if (showAddDialog) {
        AddPeerDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { peerId, name ->
                onAddPeer(peerId, name)
                showAddDialog = false
            }
        )
    }
    
    if (editingPeer != null) {
        EditPeerDialog(
            peer = editingPeer!!,
            onDismiss = { editingPeer = null },
            onConfirm = { newName ->
                onEditPeer(editingPeer!!.peerId(), newName)
                editingPeer = null
            },
            onDelete = {
                onDeletePeer(editingPeer!!.peerId())
                editingPeer = null
            }
        )
    }
}

@Composable
fun PeerItem(peer: StoredPeer, isSelected: Boolean, isOnline: Boolean, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) Color(0xFF264F78) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isOnline) Color(0xFF4EC9B0) else Color(0xFF666666))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = peer.displayName(),
            color = Color(0xFFD4D4D4),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AddPeerDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var peerId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить пира") },
        text = {
            Column {
                OutlinedTextField(
                    value = peerId,
                    onValueChange = { peerId = it },
                    label = { Text("Yggdrasil адрес (IPv6)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Имя пира") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (peerId.isNotBlank()) {
                        onConfirm(peerId, if (name.isBlank()) peerId else name)
                    }
                }
            ) {
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

@Composable
fun EditPeerDialog(peer: StoredPeer, onDismiss: () -> Unit, onConfirm: (String) -> Unit, onDelete: () -> Unit) {
    var name by remember { mutableStateOf(peer.displayName()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактировать пира") },
        text = {
            Column {
                Text(text = "Адрес: ${peer.peerId()}", color = Color(0xFF888888))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Имя пира") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text("Удалить", color = Color(0xFFCF6679))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        if (name.isNotBlank()) {
                            onConfirm(name)
                        }
                    }
                ) {
                    Text("OK")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}