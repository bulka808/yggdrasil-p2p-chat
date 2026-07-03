package dev.p2pchat.message;

import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class TextMessage extends Message {
    private final String text;

    public TextMessage(String from, String to, String displayName, String text) {
        super(from, to, displayName, 1);
        this.text = text;
    }

    public TextMessage(String from, String to, String displayName, Instant sentAt, String text) {
        super(from, to, displayName, 1, sentAt);
        this.text = text;
    }

    public String text() { return text; }

    @Override
    public RawMessage toRaw() throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        MessagePacker packer = MessagePack.newDefaultPacker(buf);
        packer.packMapHeader(1);
        packer.packString("text");
        packer.packString(text);
        packer.flush();
        packer.close();
        return RawMessage.fromMessage(this, buf.toByteArray());
    }
}
