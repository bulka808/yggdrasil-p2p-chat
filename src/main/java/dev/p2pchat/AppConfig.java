package dev.p2pchat;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class AppConfig {

    private String yggdrasilHost = "localhost";
    private int yggdrasilPort = 9001;
    private int serverPort = 4433;
    private String ownAddress = "";
    private String displayName = "";
    private int imageLimitMb = 10;
    private int fileLimitMb = 100;
    private int storageLimitMb = 4096;
    private String dbPath = "chat.db";
    private boolean debugLogging = false;

    public AppConfig() {}

    public String yggdrasilHost() { return yggdrasilHost; }
    public int yggdrasilPort() { return yggdrasilPort; }
    public int serverPort() { return serverPort; }
    public String ownAddress() { return ownAddress; }
    public String displayName() { return displayName; }
    public int imageLimitMb() { return imageLimitMb; }
    public int fileLimitMb() { return fileLimitMb; }
    public int storageLimitMb() { return storageLimitMb; }
    public String dbPath() { return dbPath; }
    public boolean debugLogging() { return debugLogging; }

    public long imageLimitBytes() { return (long) imageLimitMb << 20; }
    public long fileLimitBytes() { return (long) fileLimitMb << 20; }

    public void setYggdrasilHost(String host) { this.yggdrasilHost = host; }
    public void setYggdrasilPort(int port) { this.yggdrasilPort = port; }
    public void setServerPort(int port) { this.serverPort = port; }
    public void setOwnAddress(String address) { this.ownAddress = address; }
    public void setDisplayName(String name) { this.displayName = name; }
    public void setImageLimitMb(int mb) { this.imageLimitMb = mb; }
    public void setFileLimitMb(int mb) { this.fileLimitMb = mb; }
    public void setStorageLimitMb(int mb) { this.storageLimitMb = mb; }
    public void setDbPath(String path) { this.dbPath = path; }
    public void setDebugLogging(boolean debug) { this.debugLogging = debug; }

    public static AppConfig load(String path) throws IOException {
        String content = Files.readString(Path.of(path));
        Yaml yaml = new Yaml();
        Map<String, Object> root = yaml.load(content);

        AppConfig config = new AppConfig();

        @SuppressWarnings("unchecked")
        Map<String, Object> ygg = (Map<String, Object>) root.get("yggdrasil");
        if (ygg != null) {
            config.yggdrasilHost = (String) ygg.getOrDefault("host", config.yggdrasilHost);
            Object port = ygg.get("port");
            if (port != null) config.yggdrasilPort = ((Number) port).intValue();
        }

        Object sPort = root.get("server_port");
        if (sPort != null) config.serverPort = ((Number) sPort).intValue();

        config.ownAddress = (String) root.getOrDefault("own_address", config.ownAddress);
        config.displayName = (String) root.getOrDefault("display_name", config.displayName);

        if (config.displayName.isEmpty()) {
            config.displayName = "User_" + java.util.UUID.randomUUID().toString().substring(0, 8);
            try { config.save(path); } catch (IOException ignored) {}
        }

        if (config.ownAddress.isEmpty()) {
            String detected = YggdrasilCheck.getSelf(config.yggdrasilHost, config.yggdrasilPort);
            if (detected != null) {
                config.ownAddress = detected;
                try {
                    config.save(path);
                } catch (IOException ignored) {}
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> limits = (Map<String, Object>) root.get("limits");
        if (limits != null) {
            Object img = limits.get("image_size_mb");
            if (img != null) config.imageLimitMb = ((Number) img).intValue();
            Object file = limits.get("file_size_mb");
            if (file != null) config.fileLimitMb = ((Number) file).intValue();
            Object storage = limits.get("storage_limit_mb");
            if (storage != null) config.storageLimitMb = ((Number) storage).intValue();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> db = (Map<String, Object>) root.get("database");
        if (db != null) {
            config.dbPath = (String) db.getOrDefault("path", config.dbPath);
        }

        Object debug = root.get("debug_logging");
        if (debug != null) config.debugLogging = (Boolean) debug;

        return config;
    }

    public static AppConfig createDefault(String path) throws IOException {
        AppConfig config = new AppConfig();

        String detected = YggdrasilCheck.getSelf(config.yggdrasilHost, config.yggdrasilPort);
        if (detected != null) {
            config.ownAddress = detected;
        }

        config.displayName = "User_" + java.util.UUID.randomUUID().toString().substring(0, 8);

        config.save(path);
        return config;
    }

    public void save(String path) throws IOException {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(options);

        var root = new java.util.HashMap<String, Object>();
        root.put("yggdrasil", Map.of("host", yggdrasilHost, "port", yggdrasilPort));
        root.put("server_port", serverPort);
        root.put("own_address", ownAddress);
        root.put("display_name", displayName);
        root.put("limits", Map.of("image_size_mb", imageLimitMb, "file_size_mb", fileLimitMb, "storage_limit_mb", storageLimitMb));
        root.put("database", Map.of("path", dbPath));
        root.put("debug_logging", debugLogging);

        Files.writeString(Path.of(path), yaml.dump(root));
    }
}
