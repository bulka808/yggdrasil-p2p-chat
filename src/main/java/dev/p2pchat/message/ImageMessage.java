package dev.p2pchat.message;

import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;

public class ImageMessage extends Message {
    private final byte[] image;
    private final String filename;

    public ImageMessage(String from, String to, String displayName, byte[] image, String filename) {
        super(from, to, displayName, 2);
        this.image = image;
        this.filename = filename;
    }

    public ImageMessage(String from, String to, String displayName, byte[] image, String filename, Instant sentAt) {
        super(from, to, displayName, 2, sentAt);
        this.image = image;
        this.filename = filename;
    }

    public byte[] image() { return image; }
    public String filename() { return filename; }

    @Override
    public RawMessage toRaw() throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        MessagePacker packer = MessagePack.newDefaultPacker(buf);
        packer.packMapHeader(2);
        packer.packString("filename");
        packer.packString(filename);
        packer.packString("image");
        packer.packBinaryHeader(image.length);
        packer.writePayload(image);
        packer.flush();
        packer.close();
        return RawMessage.fromMessage(this, buf.toByteArray());
    }
}
