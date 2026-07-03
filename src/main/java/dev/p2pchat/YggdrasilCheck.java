package dev.p2pchat;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class YggdrasilCheck {

    public static boolean isRunning(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static String getSelf(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3000);
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
            System.out.println("Ошибка получения адреса Yggdrasil: " + e.getMessage());
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
            System.out.println("Yggdrasil не найден на " + host + ":" + port);
            return false;
        }
        String address = getSelf(host, port);
        if (address == null) {
            System.out.println("Yggdrasil работает, но не удалось получить адрес");
            return false;
        }
        System.out.println("Yggdrasil работает: " + host + ":" + port + ", адрес: " + address);
        return true;
    }
}
