package com.cdp.codpattern.config.storage;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Filesystem primitives shared by normal saves and explicit migrations. */
public final class StorageFiles {
    private StorageFiles() {}

    public static void checkPath(Path path) throws IOException {
        Path current = path.toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isSymbolicLink(current)) throw new IOException("Symbolic link is not allowed: " + current);
            current = current.getParent();
        }
    }

    public static void write(Path target, String text) throws IOException {
        checkPath(target);
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), ".write-", ".tmp");
        try {
            Files.writeString(temp, text);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static String digest(Path path) throws IOException {
        checkPath(path);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var stream = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = stream.read(buffer)) >= 0) digest.update(buffer, 0, length);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
