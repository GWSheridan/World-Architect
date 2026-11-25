package com.gwsheridan.worldsunwritten.compatibility.legacy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;

/** Import authoring files once, without modifying the installation or any archived bundles. */
public final class LegacyConfiguration {
    private LegacyConfiguration() {}
    public static synchronized Path directory(Path configuration) {
        Path root = configuration.toAbsolutePath().normalize(), old = root.resolve(LegacyIds.NAMESPACE), current = root.resolve(LegacyIds.CURRENT);
        if (Files.exists(current) || !Files.exists(old)) return current;
        Path temporary = null;
        try {
            if (Files.isSymbolicLink(old)) throw new IOException("The legacy configuration directory is a symbolic link.");
            List<Path> files; try (var stream = Files.walk(old)) { files = stream.sorted().toList(); }
            temporary = Files.createTempDirectory(root, ".unwritten-import-");
            for (var source : files) {
                if (Files.isSymbolicLink(source)) throw new IOException("Configuration import cannot follow symbolic links.");
                Path destination = temporary.resolve(old.relativize(source));
                if (Files.isDirectory(source)) { Files.createDirectories(destination); continue; }
                var expected = hash(source); Files.copy(source, destination);
                if (!MessageDigest.isEqual(expected, hash(destination)) || !MessageDigest.isEqual(expected, hash(source))) throw new IOException("Configuration changed during import: " + old.relativize(source));
            }
            try (var stream = Files.walk(old)) { if (!stream.sorted().toList().equals(files)) throw new IOException("Configuration files changed during import."); }
            Files.move(temporary, current, StandardCopyOption.ATOMIC_MOVE); temporary = null;
            return current;
        } catch (IOException error) { throw new UncheckedIOException("Worlds Unwritten could not import the existing configuration; the original was preserved", error); }
        finally {
            if (temporary != null) try (var stream = Files.walk(temporary)) {
                for (var file : stream.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            } catch (IOException error) { /* The uniquely named incomplete import is never selected. */ }
        }
    }
    private static byte[] hash(Path file) throws IOException {
        try (var stream = Files.newInputStream(file)) {
            var digest = MessageDigest.getInstance("SHA-256"); byte[] buffer = new byte[65536];
            for (int count; (count = stream.read(buffer)) >= 0;) digest.update(buffer, 0, count);
            return digest.digest();
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
