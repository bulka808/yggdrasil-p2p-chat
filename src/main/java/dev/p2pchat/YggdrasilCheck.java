package dev.p2pchat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class YggdrasilCheck {

    private static final ObjectMapper mapper = new ObjectMapper();

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }

    public static boolean isRunning(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3000);
            return true;
        } catch (IOException e) {
            if (isLinux()) {
                try {
                    ProcessBuilder pb = new ProcessBuilder("yggdrasilctl", "-json", "getself");
                    pb.redirectErrorStream(true);
                    Process process = pb.start();
                    String response = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    return response.contains("\"address\"");
                } catch (Exception ex) {
                    return false;
                }
            }
            return false;
        }
    }

    public static String getSelf(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3000);
            socket.setSoTimeout(5000);

            socket.getOutputStream()
                    .write("{\"request\":\"getSelf\"}\n".getBytes(StandardCharsets.UTF_8));

            String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            Logger.debug("Yggdrasil response: " + response);

            var tree = mapper.readTree(response);
            String address = tree.path("response").path("address").asText("");
            return address.isEmpty() ? null : address;
        } catch (Exception e) {
            if (isLinux()) {
                return getSelfViaCtl();
            }
            Logger.error("Yggdrasil getSelf failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    private static String getSelfViaCtl() {
        try {
            ProcessBuilder pb = new ProcessBuilder("yggdrasilctl", "-json", "getself");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String response = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            Logger.debug("Yggdrasil ctl response: " + response);

            var tree = mapper.readTree(response);
            String address = tree.path("response").path("address").asText("");
            return address.isEmpty() ? null : address;
        } catch (Exception e) {
            Logger.error("yggdrasilctl failed: " + e.getMessage());
            return null;
        }
    }
}
