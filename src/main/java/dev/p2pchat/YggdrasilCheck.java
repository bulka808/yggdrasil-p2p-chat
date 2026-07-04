package dev.p2pchat;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnixDomainSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

public class YggdrasilCheck {

    private static final String LINUX_SOCKET = "/var/run/yggdrasil.sock";

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }

    private static Socket connectSocket(String host, int port) throws IOException {
        Socket socket = new Socket();
        if (isLinux() && Files.exists(Path.of(LINUX_SOCKET))) {
            socket.connect(UnixDomainSocketAddress.of(LINUX_SOCKET));
        } else {
            socket.connect(new InetSocketAddress(host, port), 3000);
        }
        return socket;
    }

    public static boolean isRunning(String host, int port) {
        try (Socket socket = connectSocket(host, port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static String getSelf(String host, int port) {
        try (Socket socket = connectSocket(host, port)) {
            socket.setSoTimeout(5000);

            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            String request = "{\"request\":\"getSelf\"}\n";
            out.write(request.getBytes(StandardCharsets.UTF_8));
            out.flush();

            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) != -1) {
                sb.append(new String(buf, 0, len, StandardCharsets.UTF_8));
                if (sb.toString().contains("\n")) break;
            }

            return parseAddress(sb.toString());
        } catch (Exception e) {
            Logger.error("Ошибка получения адреса Yggdrasil: " + e.getMessage());
            return null;
        }
    }

    private static String parseAddress(String json) {
        String addressKey = "\"address\"";
        int addressIdx = json.indexOf(addressKey);
        if (addressIdx == -1) return null;

        int colonIdx = json.indexOf(":", addressIdx);
        int quoteStart = json.indexOf("\"", colonIdx + 1);
        int quoteEnd = json.indexOf("\"", quoteStart + 1);

        if (quoteStart == -1 || quoteEnd == -1) return null;

        return json.substring(quoteStart + 1, quoteEnd);
    }

    public static boolean check(String host, int port) {
        if (!isRunning(host, port)) {
            Logger.error("Yggdrasil не найден на " + host + ":" + port);
            return false;
        }
        String address = getSelf(host, port);
        if (address == null) {
            Logger.error("Yggdrasil работает, но не удалось получить адрес");
            return false;
        }
        Logger.info("Yggdrasil работает: " + host + ":" + port + ", адрес: " + address);
        return true;
    }
}
