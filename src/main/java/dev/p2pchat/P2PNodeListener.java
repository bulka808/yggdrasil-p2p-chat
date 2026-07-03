package dev.p2pchat;

import java.time.Instant;

public interface P2PNodeListener {
    void onTextMessage(String peerId, String displayName, String text, Instant at);
    void onImageMessage(String peerId, String displayName, byte[] image, String filename, String filePath, Instant at);
    void onFileMessage(String peerId, String displayName, byte[] file, String filename, String extension, String filePath, Instant at);
    void onMessageSent(String peerId, byte[] payload, boolean ok);
    void onMessageDelivered(long timestamp);
    void onPeerConnected(String peerId);
    void onPeerDisconnected(String peerId);
}
