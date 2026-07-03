package dev.p2pchat;

public record StoredMessage(
    long id,
    String peerId,
    int typeId,
    String fromId,
    String toId,
    long timestamp,
    String textContent,
    String filename,
    String filePath,
    String status,
    String senderName
) {}
