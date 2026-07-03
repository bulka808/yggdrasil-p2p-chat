package dev.p2pchat;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;

public class ReceivedDirCleanup {

    public static void cleanup(Path dir, long maxBytes) {
        try {
            if (!Files.exists(dir)) return;

            long targetBytes = (long) (maxBytes * 0.75);
            long totalSize = calculateSize(dir);
            if (totalSize <= maxBytes) return;

            List<Path> files = Files.list(dir)
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(p -> {
                        try { return Files.getLastModifiedTime(p); }
                        catch (IOException e) { return java.nio.file.attribute.FileTime.fromMillis(0); }
                    }))
                    .toList();

            for (Path file : files) {
                if (totalSize <= targetBytes) break;
                try {
                    long fileSize = Files.size(file);
                    Files.delete(file);
                    totalSize -= fileSize;
                } catch (IOException ignored) {}
            }
        } catch (IOException ignored) {}
    }

    private static long calculateSize(Path dir) throws IOException {
        long[] size = {0};
        try (var walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                try { size[0] += Files.size(p); }
                catch (IOException ignored) {}
            });
        }
        return size[0];
    }
}
