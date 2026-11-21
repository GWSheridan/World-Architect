package com.gwsheridan.worldarchitect.release;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;

/** Build tool only; never included in the production mod JAR. */
public final class ReleaseChecksums {
    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[0]).toAbsolutePath().normalize();
        String version = args[5];
        if (!version.matches("[0-9][a-zA-Z0-9._-]*")) throw new IllegalArgumentException("Invalid release version");
        Files.createDirectories(directory);
        var assets = new ArrayList<Path>();
        for (int i = 1; i <= 3; i++) assets.add(stage(Path.of(args[i]), directory));
        var kit=directory.resolve("worldarchitect-"+version+"-test-kit.zip");if(Files.isRegularFile(kit))assets.add(kit);
        var bundleDirectories=new ArrayList<Path>();bundleDirectories.add(Path.of(args[4]));
        for(int index=6;index<args.length;index++)bundleDirectories.add(Path.of(args[index]));
        var projects=Path.of(args[4]).getParent().getParent().resolve("modpack-builder/bundles");if(Files.isDirectory(projects))bundleDirectories.add(projects);
        for(Path bundleDirectory:bundleDirectories)try (var bundles = Files.list(bundleDirectory)) {
            for (Path bundle : bundles.filter(path -> path.getFileName().toString().endsWith("-"+version+".zip")).sorted().toList()) assets.add(stage(bundle, directory));
        }
        assets.sort(Comparator.comparing(path -> path.getFileName().toString()));
        var rows = new ArrayList<String>();
        for (Path asset : assets) {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(asset)) {
                byte[] buffer = new byte[65536]; int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            rows.add(HexFormat.of().formatHex(digest.digest()) + "  " + asset.getFileName());
        }
        Files.writeString(directory.resolve("SHA256SUMS-" + version + ".txt"), String.join("\n", rows) + "\n");
        System.out.println("Staged " + assets.size() + " release assets and SHA-256 checksums.");
    }
    private static Path stage(Path source, Path directory) throws Exception {
        source = source.toAbsolutePath().normalize();
        Path target = directory.resolve(source.getFileName()).normalize();
        if (!target.getParent().equals(directory)) throw new IllegalArgumentException("Asset must stay inside release directory");
        if (!source.equals(target)) Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }
}
