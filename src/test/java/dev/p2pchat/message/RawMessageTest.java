package dev.p2pchat.message;

import dev.p2pchat.StreamHandler;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RawMessageTest {

    @Test
    void packUnpackTextMessage() throws IOException {
        TextMessage original = new TextMessage("alice", "bob", "Alice", "Hello world");
        RawMessage raw = original.toRaw();

        byte[] packed = raw.pack();
        RawMessage unpacked = StreamHandler.unpack(packed);

        assertEquals(1, unpacked.typeId());
        assertEquals("alice", unpacked.from());
        assertEquals("bob", unpacked.to());
        assertEquals("Alice", unpacked.displayName());
    }

    @Test
    void extractTextFromEnvelope() throws IOException {
        TextMessage original = new TextMessage("alice", "bob", "Alice", "Test message");
        RawMessage raw = original.toRaw();

        Map<String, Object> envelope = StreamHandler.extractEnvelope(raw);
        assertEquals("Test message", envelope.get("text"));
    }

    @Test
    void packUnpackImageMessage() throws IOException {
        byte[] imageData = new byte[]{1, 2, 3, 4, 5};
        ImageMessage original = new ImageMessage("alice", "bob", "Alice", imageData, "photo.jpg");
        RawMessage raw = original.toRaw();

        byte[] packed = raw.pack();
        RawMessage unpacked = StreamHandler.unpack(packed);

        assertEquals(2, unpacked.typeId());
        Map<String, Object> envelope = StreamHandler.extractEnvelope(unpacked);
        assertArrayEquals(imageData, (byte[]) envelope.get("image"));
        assertEquals("photo.jpg", envelope.get("filename"));
    }

    @Test
    void senderValidationRejectsMismatch() {
        RawMessage raw = new RawMessage(1, "alice", "bob", "Alice", System.currentTimeMillis(), new byte[0]);
        assertNotEquals(raw.from(), "charlie");
    }
}
