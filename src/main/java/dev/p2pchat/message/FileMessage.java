package dev.p2pchat.message;

import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;

public class FileMessage extends Message {
    private final byte[] file;
    private final String filename;
    private final String extension;

    public FileMessage(String from, String to, String displayName, byte[] file, String filename, String extension) {
        super(from, to, displayName, 3);
        this.file = file;
        this.filename = filename;
        this.extension = extension;
    }

    public FileMessage(String from, String to, String displayName, byte[] file, String filename, String extension, Instant sentAt) {
        super(from, to, displayName, 3, sentAt);
        this.file = file;
        this.filename = filename;
        this.extension = extension;
    }

    public byte[] file() { return file; }
    public String filename() { return filename; }
    public String extension() { return extension; }

    @Override
    public RawMessage toRaw() throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        MessagePacker packer = MessagePack.newDefaultPacker(buf);
        packer.packMapHeader(3);
        packer.packString("filename");
        packer.packString(filename);
        packer.packString("extension");
        packer.packString(extension);
        packer.packString("file");
        packer.packBinaryHeader(file.length);
        packer.writePayload(file);
        packer.flush();
        packer.close();
        return RawMessage.fromMessage(this, buf.toByteArray());
    }
}
