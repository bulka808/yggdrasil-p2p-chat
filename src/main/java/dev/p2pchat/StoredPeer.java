package dev.p2pchat;

public record StoredPeer(
    String peerId,
    String displayName,
    long createdAt
) {}
