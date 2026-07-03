package dev.p2pchat;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ChatStorage implements AutoCloseable {

    private final Connection conn;

    public ChatStorage(String dbPath) throws SQLException {
        this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        init();
    }

    private void init() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS peers (
                    peer_id TEXT PRIMARY KEY,
                    display_name TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    peer_id TEXT NOT NULL,
                    type_id INTEGER NOT NULL,
                    from_id TEXT,
                    to_id TEXT,
                    timestamp INTEGER NOT NULL,
                    text_content TEXT,
                    filename TEXT,
                    file_path TEXT,
                    created_at INTEGER NOT NULL,
                    status TEXT DEFAULT 'sent',
                    FOREIGN KEY (peer_id) REFERENCES peers(peer_id)
                )
            """);
            try {
                stmt.execute("ALTER TABLE messages ADD COLUMN status TEXT DEFAULT 'sent'");
            } catch (SQLException ignored) {}
            try {
                stmt.execute("ALTER TABLE messages ADD COLUMN sender_name TEXT DEFAULT ''");
            } catch (SQLException ignored) {}
        }
    }

    public void savePeer(String peerId, String displayName) {
        String sql = "INSERT INTO peers (peer_id, display_name, created_at) VALUES (?, ?, ?) ON CONFLICT(peer_id) DO UPDATE SET display_name = excluded.display_name WHERE excluded.display_name != peers.peer_id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, peerId);
            ps.setString(2, displayName);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            Logger.error("savePeer error: " + e.getMessage());
        }
    }

    public void updatePeerName(String peerId, String newName) {
        String sql = "UPDATE peers SET display_name = ? WHERE peer_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newName);
            ps.setString(2, peerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            Logger.error("updatePeerName error: " + e.getMessage());
        }
    }

    public void saveTextMessage(String peerId, String fromId, String toId, long timestamp, String text, String status, String senderName) {
        String sql = "INSERT INTO messages (peer_id, type_id, from_id, to_id, timestamp, text_content, created_at, status, sender_name) VALUES (?, 1, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, peerId);
            ps.setString(2, fromId);
            ps.setString(3, toId);
            ps.setLong(4, timestamp);
            ps.setString(5, text);
            ps.setLong(6, System.currentTimeMillis());
            ps.setString(7, status);
            ps.setString(8, senderName);
            ps.executeUpdate();
        } catch (SQLException e) {
            Logger.error("saveTextMessage error: " + e.getMessage());
        }
    }

    public void saveImageMessage(String peerId, String fromId, String toId, long timestamp, String filename, String filePath, String senderName) {
        String sql = "INSERT INTO messages (peer_id, type_id, from_id, to_id, timestamp, filename, file_path, created_at, sender_name) VALUES (?, 2, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, peerId);
            ps.setString(2, fromId);
            ps.setString(3, toId);
            ps.setLong(4, timestamp);
            ps.setString(5, filename);
            ps.setString(6, filePath);
            ps.setLong(7, System.currentTimeMillis());
            ps.setString(8, senderName);
            ps.executeUpdate();
        } catch (SQLException e) {
            Logger.error("saveImageMessage error: " + e.getMessage());
        }
    }

    public void saveFileMessage(String peerId, String fromId, String toId, long timestamp, String filename, String filePath, String senderName) {
        String sql = "INSERT INTO messages (peer_id, type_id, from_id, to_id, timestamp, filename, file_path, created_at, sender_name) VALUES (?, 3, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, peerId);
            ps.setString(2, fromId);
            ps.setString(3, toId);
            ps.setLong(4, timestamp);
            ps.setString(5, filename);
            ps.setString(6, filePath);
            ps.setLong(7, System.currentTimeMillis());
            ps.setString(8, senderName);
            ps.executeUpdate();
        } catch (SQLException e) {
            Logger.error("saveFileMessage error: " + e.getMessage());
        }
    }

    public void updateMessageStatus(long messageId, String status) {
        String sql = "UPDATE messages SET status = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, messageId);
            ps.executeUpdate();
        } catch (SQLException e) {
            Logger.error("updateMessageStatus error: " + e.getMessage());
        }
    }

    public void updateLastMessageStatus(String peerId, String fromId, String status) {
        String sql = "UPDATE messages SET status = ? WHERE peer_id = ? AND from_id = ? AND id = (SELECT MAX(id) FROM messages WHERE peer_id = ? AND from_id = ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, peerId);
            ps.setString(3, fromId);
            ps.setString(4, peerId);
            ps.setString(5, fromId);
            ps.executeUpdate();
        } catch (SQLException e) {
            Logger.error("updateLastMessageStatus error: " + e.getMessage());
        }
    }

    public void updateMessageStatusByTimestamp(long timestamp, String status) {
        String sql = "UPDATE messages SET status = ? WHERE timestamp = ? AND from_id != ''";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, timestamp);
            ps.executeUpdate();
        } catch (SQLException e) {
            Logger.error("updateMessageStatusByTimestamp error: " + e.getMessage());
        }
    }

    public List<StoredPeer> getPeers() {
        var peers = new ArrayList<StoredPeer>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT peer_id, display_name, created_at FROM peers ORDER BY created_at")) {
            while (rs.next()) {
                peers.add(new StoredPeer(
                        rs.getString("peer_id"),
                        rs.getString("display_name"),
                        rs.getLong("created_at")
                ));
            }
        } catch (SQLException e) {
            Logger.error("getPeers error: " + e.getMessage());
        }
        return peers;
    }

    public void deleteMessagesByPeer(String peerId) {
        String sql = "DELETE FROM messages WHERE peer_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, peerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            Logger.error("deleteMessagesByPeer error: " + e.getMessage());
        }
    }

    public void deletePeer(String peerId) {
        String sql = "DELETE FROM peers WHERE peer_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, peerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            Logger.error("deletePeer error: " + e.getMessage());
        }
    }

    public List<StoredMessage> getMessagesByPeer(String peerId) {
        var messages = new ArrayList<StoredMessage>();
        String sql = "SELECT id, type_id, from_id, to_id, timestamp, text_content, filename, file_path, status, sender_name FROM messages WHERE peer_id = ? ORDER BY timestamp";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, peerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    messages.add(new StoredMessage(
                            rs.getLong("id"),
                            peerId,
                            rs.getInt("type_id"),
                            rs.getString("from_id"),
                            rs.getString("to_id"),
                            rs.getLong("timestamp"),
                            rs.getString("text_content"),
                            rs.getString("filename"),
                            rs.getString("file_path"),
                            rs.getString("status"),
                            rs.getString("sender_name")
                    ));
                }
            }
        } catch (SQLException e) {
            Logger.error("getMessagesByPeer error: " + e.getMessage());
        }
        return messages;
    }

    @Override
    public void close() throws Exception {
        conn.close();
    }
}
