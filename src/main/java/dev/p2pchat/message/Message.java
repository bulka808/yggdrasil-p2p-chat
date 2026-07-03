package dev.p2pchat.message;

import java.io.IOException;
import java.time.Instant;

public abstract class Message {

    protected final String from;
    protected final String to;
    protected final String displayName;
    protected final Instant sentAt;
    protected final int typeId;

    public Message(String from, String to, String displayName, int typeId) {
        this.from = from;
        this.to = to;
        this.displayName = displayName;
        this.typeId = typeId;
        this.sentAt = Instant.now();
    }

    public Message(String from, String to, String displayName, int typeId, Instant sentAt) {
        this.from = from;
        this.to = to;
        this.displayName = displayName;
        this.typeId = typeId;
        this.sentAt = sentAt;
    }

    public String from() { return from; }
    public String to() { return to; }
    public String displayName() { return displayName; }
    public Instant sentAt() { return sentAt; }
    public int typeId() { return typeId; }

    public abstract RawMessage toRaw() throws IOException;
}
