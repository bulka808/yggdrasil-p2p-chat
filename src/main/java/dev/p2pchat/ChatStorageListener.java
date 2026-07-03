package dev.p2pchat;

import java.time.Instant;

public class ChatStorageListener implements P2PNodeListener {

    private final ChatStorage storage;
    private final String ownAddress;

    public ChatStorageListener(ChatStorage storage, String ownAddress) {
        this.storage = storage;
        this.ownAddress = ownAddress;
    }

    @Override
    public void onTextMessage(String peerId, String displayName, String text, Instant at) {
        String name = (displayName != null && !displayName.isBlank()) ? displayName : peerId;
        Logger.debug("ChatStorageListener.onTextMessage: peerId=" + peerId + " displayName=" + name + " text=" + text);
        storage.savePeer(peerId, name);
        storage.saveTextMessage(peerId, peerId, ownAddress, at.toEpochMilli(), text, "received", name);
    }

    @Override
    public void onImageMessage(String peerId, String displayName, byte[] image, String filename, String filePath, Instant at) {
        String name = (displayName != null && !displayName.isBlank()) ? displayName : peerId;
        Logger.debug("ChatStorageListener.onImageMessage: peerId=" + peerId + " displayName=" + name + " filename=" + filename);
        storage.savePeer(peerId, name);
        storage.saveImageMessage(peerId, peerId, ownAddress, at.toEpochMilli(), filename, filePath, name);
    }

    @Override
    public void onFileMessage(String peerId, String displayName, byte[] file, String filename, String extension, String filePath, Instant at) {
        String name = (displayName != null && !displayName.isBlank()) ? displayName : peerId;
        Logger.debug("ChatStorageListener.onFileMessage: peerId=" + peerId + " displayName=" + name + " filename=" + filename);
        storage.savePeer(peerId, name);
        storage.saveFileMessage(peerId, peerId, ownAddress, at.toEpochMilli(), filename, filePath, name);
    }

    @Override
    public void onMessageSent(String peerId, byte[] payload, boolean ok) {
    }

    @Override
    public void onMessageDelivered(long timestamp) {
        Logger.debug("ChatStorageListener.onMessageDelivered: timestamp=" + timestamp);
        storage.updateMessageStatusByTimestamp(timestamp, "delivered");
    }

    @Override
    public void onPeerConnected(String peerId) {
        storage.savePeer(peerId, peerId);
    }

    @Override
    public void onPeerDisconnected(String peerId) {
    }
}
