package dev.p2pchat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnixDomainSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

public class YggdrasilCheck {

    private static final String LINUX_SOCKET = "/var/run/yggdrasil.sock";
    private static final ObjectMapper mapper = new ObjectMapper();

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

            socket.getOutputStream()
                    .write("{\"request\":\"getSelf\"}\n".getBytes(StandardCharsets.UTF_8));

            BufferedReader br = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String response = br.readLine();

            var tree = mapper.readTree(response);
            String address = tree.path("response").path("address").asText("");
            return address.isEmpty() ? null : address;
        } catch (Exception e) {
            Logger.error("Yggdrasil: " + e.getMessage());
            return null;
        }
    }
}
