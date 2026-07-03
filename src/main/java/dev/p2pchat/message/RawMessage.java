package dev.p2pchat.message;

import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;

public class RawMessage {

    private final int typeId;
    private final String from;
    private final String to;
    private final String displayName;
    private final long timestamp;
    private final byte[] payload;

    public RawMessage(int typeId, String from, String to, String displayName, long timestamp, byte[] payload) {
        this.typeId = typeId;
        this.from = from;
        this.to = to;
        this.displayName = displayName;
        this.timestamp = timestamp;
        this.payload = payload;
    }

    public static RawMessage fromMessage(Message msg, byte[] payload) {
        return new RawMessage(
                msg.typeId(),
                msg.from(),
                msg.to(),
                msg.displayName(),
                msg.sentAt().toEpochMilli(),
                payload
        );
    }

    public int typeId() { return typeId; }
    public String from() { return from; }
    public String to() { return to; }
    public String displayName() { return displayName; }
    public long timestamp() { return timestamp; }
    public byte[] payload() { return payload; }

    public Instant sentAt() {
        return Instant.ofEpochMilli(this.timestamp);
    }

    public byte[] pack() throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        MessagePacker packer = MessagePack.newDefaultPacker(buf);

        packer.packMapHeader(6);
        packer.packString("typeId");
        packer.packInt(typeId);
        packer.packString("from");
        packer.packString(from);
        packer.packString("to");
        packer.packString(to);
        packer.packString("displayName");
        packer.packString(displayName != null ? displayName : "");
        packer.packString("timestamp");
        packer.packLong(timestamp);
        packer.packString("payload");
        packer.packBinaryHeader(payload.length);
        packer.writePayload(payload);

        packer.flush();
        packer.close();
        return buf.toByteArray();
    }
}
