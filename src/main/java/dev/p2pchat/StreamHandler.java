package dev.p2pchat;

import dev.p2pchat.message.*;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.Value;
import org.msgpack.value.ValueFactory;
import tech.kwik.core.QuicStream;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class StreamHandler {

    private static final Path RECEIVED_DIR = Path.of("received");

    private final long imageLimit;
    private final long fileLimit;
    private final long storageLimit;
    private final String ownAddress;
    private final Consumer<TextMessage> onText;
    private final BiConsumer<ImageMessage, String> onImage;
    private final BiConsumer<FileMessage, String> onFile;

    public StreamHandler(long imageLimit, long fileLimit, long storageLimit, String ownAddress,
                         Consumer<TextMessage> onText,
                         BiConsumer<ImageMessage, String> onImage,
                         BiConsumer<FileMessage, String> onFile) {
        this.imageLimit = imageLimit;
        this.fileLimit = fileLimit;
        this.storageLimit = storageLimit;
        this.ownAddress = ownAddress;
        this.onText = onText;
        this.onImage = onImage;
        this.onFile = onFile;
    }

    public void handle(QuicStream stream, String peerId) {
        Logger.debug("StreamHandler.handle() called for peer=" + peerId);
        try (InputStream in = stream.getInputStream(); OutputStream out = stream.getOutputStream()) {
            byte[] raw = in.readAllBytes();
            if (raw == null || raw.length == 0) {
                Logger.debug("StreamHandler: no data from " + peerId + ", closing");
                return;
            }
            Logger.debug("StreamHandler: read " + raw.length + " bytes from " + peerId);
            RawMessage rawMsg = unpack(raw);

            if (!rawMsg.from().equals(peerId)) {
                Logger.info("[" + peerId + "] rejected: from=" + rawMsg.from() + " != peerId");
                out.write("REJECT".getBytes(StandardCharsets.UTF_8));
                return;
            }

            switch (rawMsg.typeId()) {
                case 1 -> handleText(rawMsg, out, peerId);
                case 2 -> handleImage(rawMsg, out, peerId);
                case 3 -> handleFile(rawMsg, out, peerId);
                default -> {
                    Logger.debug("StreamHandler: unknown typeId=" + rawMsg.typeId());
                    out.write("UNKNOWN".getBytes(StandardCharsets.UTF_8));
                }
            }

        } catch (Exception e) {
            Logger.error("StreamHandler.handle() error for peer=" + peerId + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public static RawMessage unpack(byte[] data) throws IOException {
        MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(data);
        Value envelope = unpacker.unpackValue();
        unpacker.close();

        var map = envelope.asMapValue().map();
        int typeId = map.get(ValueFactory.newString("typeId")).asIntegerValue().asInt();
        String from = map.get(ValueFactory.newString("from")).asStringValue().asString();
        String to = map.get(ValueFactory.newString("to")).asStringValue().asString();
        String dn = map.containsKey(ValueFactory.newString("displayName"))
                ? map.get(ValueFactory.newString("displayName")).asStringValue().asString() : "";
        long timestamp = map.get(ValueFactory.newString("timestamp")).asIntegerValue().asLong();
        byte[] payload = map.get(ValueFactory.newString("payload")).asBinaryValue().asByteArray();

        Logger.debug("unpack: typeId=" + typeId + " from=" + from + " to=" + to + " displayName=" + dn);
        return new RawMessage(typeId, from, to, dn, timestamp, payload);
    }

    private void handleText(RawMessage raw, OutputStream out, String peerId) throws IOException {
        var envelope = extractEnvelope(raw);
        var text = (String) envelope.getOrDefault("text", "");
        Logger.debug("[" + peerId + "] text received: " + text);

        onText.accept(new TextMessage(raw.from(), raw.to(), raw.displayName(), text));
        sendAck(raw, out, peerId);
    }

    private void handleImage(RawMessage raw, OutputStream out, String peerId) throws IOException {
        var envelope = extractEnvelope(raw);
        byte[] imageData = (byte[]) envelope.get("image");

        if (imageData == null || imageData.length > imageLimit) {
            out.write("REJECT".getBytes(StandardCharsets.UTF_8));
            Logger.info("[" + peerId + "] image rejected: limit exceeded");
            return;
        }

        String filename = (String) envelope.get("filename");
        String ext = getExtension(filename);

        Path safeFile = RECEIVED_DIR.resolve(UUID.randomUUID() + ext);
        Files.createDirectories(RECEIVED_DIR);
        Files.write(safeFile, imageData);
        ReceivedDirCleanup.cleanup(RECEIVED_DIR, storageLimit);

        Logger.debug("[" + peerId + "] image received: " + filename + " -> " + safeFile.getFileName());

        onImage.accept(new ImageMessage(raw.from(), raw.to(), raw.displayName(), imageData, filename), safeFile.toString());
        sendAck(raw, out, peerId);
    }

    private void handleFile(RawMessage raw, OutputStream out, String peerId) throws IOException {
        var envelope = extractEnvelope(raw);
        byte[] fileData = (byte[]) envelope.get("file");

        if (fileData == null || fileData.length > fileLimit) {
            out.write("REJECT".getBytes(StandardCharsets.UTF_8));
            Logger.info("[" + peerId + "] file rejected: limit exceeded");
            return;
        }

        String filename = (String) envelope.get("filename");
        String ext = (String) envelope.getOrDefault("extension", getExtension(filename));

        Path safeFile = RECEIVED_DIR.resolve(UUID.randomUUID() + ext);
        Files.createDirectories(RECEIVED_DIR);
        Files.write(safeFile, fileData);
        ReceivedDirCleanup.cleanup(RECEIVED_DIR, storageLimit);

        Logger.debug("[" + peerId + "] file received: " + filename + " -> " + safeFile.getFileName());

        onFile.accept(new FileMessage(raw.from(), raw.to(), raw.displayName(), fileData, filename, ext), safeFile.toString());
        sendAck(raw, out, peerId);
    }

    private void sendAck(RawMessage original, OutputStream out, String peerId) {
        try {
            var ackEnvelope = new HashMap<String, Object>();
            ackEnvelope.put("timestamp", original.timestamp());
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            MessagePacker packer = MessagePack.newDefaultPacker(buf);
            packer.packMapHeader(1);
            packer.packString("timestamp");
            packer.packLong(original.timestamp());
            packer.flush();
            packer.close();

            RawMessage ack = new RawMessage(4, ownAddress, original.from(), "", System.currentTimeMillis(), buf.toByteArray());
            out.write(ack.pack());
            out.flush();
            Logger.debug("[" + peerId + "] ACK sent for timestamp=" + original.timestamp());
        } catch (Exception e) {
            Logger.error("[" + peerId + "] ACK send failed: " + e.getMessage());
        }
    }

    public static Map<String, Object> extractEnvelope(RawMessage raw) throws IOException {
        MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(raw.payload());
        Value val = unpacker.unpackValue();
        unpacker.close();

        var map = val.asMapValue().map();
        var result = new HashMap<String, Object>();
        for (var entry : map.entrySet()) {
            String key = entry.getKey().asStringValue().asString();
            Value v = entry.getValue();
            if (v.isStringValue()) result.put(key, v.asStringValue().asString());
            else if (v.isIntegerValue()) result.put(key, v.asIntegerValue().asLong());
            else if (v.isBooleanValue()) result.put(key, v.asBooleanValue().getBoolean());
            else if (v.isBinaryValue()) result.put(key, v.asBinaryValue().asByteArray());
        }
        Logger.debug("extractEnvelope: keys=" + result.keySet());
        return result;
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }
}
