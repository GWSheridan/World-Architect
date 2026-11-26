package com.gwsheridan.worldsunwritten.testkit;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Java-only launcher for exported projects and an installed server runtime. */
public final class PackTestKit {

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || Arrays.asList(args).contains("--help")) {
            System.out.println(
                    "Worlds Unwritten pack test kit (Java 25)\n"
                            + "java -jar Worlds-Unwritten-test-kit.jar --server"
                            + " INSTALLED_SERVER --export EXPORTED_PROJECT --providers"
                            + " PROVIDER_JAR_FOLDER --output NEW_FOLDER --eula true\n"
                            + "Select an export for the loader and runtime you want to test."
                            + " Legacy exports allow --loader and --loader-version overrides."
                            + " For Fabric use its dedicated server launcher JAR and put Fabric API in the provider folder.\n"
                            + "Uses only a new disposable world. Supply legally installed"
                            + " Minecraft, a mod loader and required provider mods. No Gradle, source"
                            + " checkout or Node is required.");
            return;
        }
        var options = new HashMap<String, String>();
        if (args.length % 2 != 0)
            throw new IllegalArgumentException("Use option/value pairs; see --help");
        for (int i = 0; i < args.length; i += 2)
            if (!Set.of("--server", "--export", "--providers", "--output", "--eula", "--loader", "--loader-version")
                            .contains(args[i])
                    || options.put(args[i], args[i + 1]) != null)
                throw new IllegalArgumentException("Unknown or repeated option " + args[i]);
        for (String key : List.of("--server", "--export", "--providers", "--output", "--eula"))
            if (!options.containsKey(key)) throw new IllegalArgumentException("Missing " + key);
        if (!options.get("--eula").equals("true"))
            throw new IllegalArgumentException(
                    "Read the Minecraft EULA and supply --eula true to accept it for this"
                            + " disposable test server.");
        Path runtime = Path.of(options.get("--server")).toRealPath(),
                project = Path.of(options.get("--export")).toRealPath(),
                providers = Path.of(options.get("--providers")).toRealPath(),
                output = Path.of(options.get("--output")).toAbsolutePath().normalize();
        if (Files.exists(output)
                || output.startsWith(runtime)
                || output.startsWith(project)
                || output.startsWith(providers))
            throw new IOException(
                    "Choose a new output folder outside the runtime, project and provider"
                            + " folders.");
        var properties = new Properties();
        try (var in = Files.newInputStream(project.resolve("testkit.properties"))) {
            properties.load(in);
        }
        String format = properties.getProperty("format", "");
        if (!Set.of("2", "3", "4").contains(format)) throw new IOException("Unsupported test project format");
        var kit = new Properties();
        try (var input = PackTestKit.class.getResourceAsStream("/testkit-runtime.properties")) {
            if (input == null) throw new IOException("Missing test kit runtime metadata");
            kit.load(input);
        }
        String loader = options.getOrDefault("--loader", properties.getProperty("loader", "neoforge"));
        if (!Set.of("neoforge", "forge", "fabric").contains(loader)) throw new IOException("Unknown loader " + loader);
        String version;
        if (format.equals("4")) {
            String targetLoader = properties.getProperty("loader");
            version = properties.getProperty("loader_version");
            if (version == null || !loader.equals(targetLoader)
                    || !version.equals(options.getOrDefault("--loader-version", version)))
                throw new IOException("Select the export for the requested loader and version.");
            if (!kit.getProperty("mod").equals(properties.getProperty("worldsunwritten")))
                throw new IOException("Use the Worlds Unwritten test kit for " + properties.getProperty("worldsunwritten"));
        } else {
            var savedVersion = properties.getProperty("loader_version", properties.getProperty("neoforge", kit.getProperty(loader)));
            version = options.getOrDefault("--loader-version", options.containsKey("--loader") ? kit.getProperty(loader) : savedVersion);
        }
        if (version == null || !version.matches("[0-9]+(?:\\.[0-9]+)+")) throw new IOException("Invalid loader version");
        var bundle = project.resolve(properties.getProperty("bundle", "")).normalize();
        if (!bundle.startsWith(project)
                || !Files.isRegularFile(bundle, LinkOption.NOFOLLOW_LINKS)
                || !sha(bundle).equals(properties.getProperty("sha256", "")))
            throw new IOException("Exported preset checksum is invalid");
        String platform =
                System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
                        ? "win_args.txt"
                        : "unix_args.txt";
        String minecraft = kit.getProperty("minecraft");
        String forgeId = minecraft + "-" + version;
        Path relativeArgs = switch (loader) {
            case "neoforge" -> Path.of("libraries/net/neoforged/neoforge", version, platform);
            case "forge" -> Path.of("libraries/net/minecraftforge/forge", forgeId, platform);
            default -> null;
        };
        if (relativeArgs == null ? !Files.isRegularFile(runtime) : !Files.isRegularFile(runtime.resolve(relativeArgs))) {
            throw new IOException("Select an installed " + loader + " " + version + " server runtime");
        }
        var home =
                Path.of(
                                PackTestKit.class
                                        .getProtectionDomain()
                                        .getCodeSource()
                                        .getLocation()
                                        .toURI())
                        .getParent();
        var kitFiles = Map.of("Worlds-Unwritten.jar", "Worlds-Unwritten-" + kit.getProperty("mod") + "-mc" + minecraft + "-" + loader + ".jar",
                "Worlds-Unwritten-pack-test.jar", "pack-test-" + loader + ".jar");
        for (String file : kitFiles.values())
            if (!Files.isRegularFile(home.resolve("lib").resolve(file)))
                throw new IOException(
                        "Keep the complete test-kit ZIP together; missing lib/" + file);
        Files.createDirectory(output);
        var server = output.resolve("server");
        Files.createDirectory(server);
        List<String> launch;
        if (relativeArgs == null) {
            Files.copy(runtime, server.resolve("fabric-server.jar"));
            launch = List.of("-jar", "fabric-server.jar");
        } else {
            copyTree(runtime.resolve("libraries"), server.resolve("libraries"));
            if (loader.equals("forge")) {
                String shim = "forge-" + forgeId + "-shim.jar";
                Files.copy(runtime.resolve(shim), server.resolve(shim));
            }
            launch = List.of("@" + relativeArgs);
        }
        Files.createDirectory(server.resolve("mods"));
        var inventory = new TreeMap<String, String>();
        for (var file : kitFiles.entrySet()) {
            var source = home.resolve("lib").resolve(file.getValue());
            Files.copy(source, server.resolve("mods").resolve(file.getKey()));
            inventory.put(file.getKey(), sha(source));
        }
        try (var files = Files.list(providers)) {
            for (var source : files.sorted().toList())
                if (source.getFileName().toString().endsWith(".jar")) {
                    if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS))
                        throw new IOException("Provider must be a regular JAR: " + source);
                    Files.copy(source, server.resolve("mods").resolve(source.getFileName()));
                    inventory.put(source.getFileName().toString(), sha(source));
                }
        }
        var config = server.resolve("config/worldsunwritten");
        Files.createDirectories(config.resolve("bundles"));
        Files.copy(bundle, config.resolve("bundles/preset.zip"));
        if (format.equals("4")) {
            var target = new Properties();
            target.setProperty("loader", loader);
            target.setProperty("version", version);
            target.setProperty("required_mods", properties.getProperty("required_mods", ""));
            try (var writer = Files.newBufferedWriter(config.resolve("test-target.properties"))) {
                target.store(writer, "Disposable fixture runtime");
            }
        }
        Files.writeString(
                config.resolve("developer.json"),
                "{\"format\":1,\"bundle\":\"preset.zip\",\"developer_mode\":false}\n");
        Files.writeString(server.resolve("eula.txt"), "eula=true\n");
        // A fresh loopback-only, empty-whitelist server cannot accept a remote player.
        int port;
        try (var socket =
                new java.net.ServerSocket(0, 0, java.net.InetAddress.getLoopbackAddress())) {
            port = socket.getLocalPort();
        }
        Files.writeString(
                server.resolve("server.properties"),
                "server-ip=127.0.0.1\nserver-port="
                        + port
                        + "\n"
                        + "online-mode=false\n"
                        + "white-list=true\n"
                        + "enforce-whitelist=true\n"
                        + "max-players=1\n"
                        + "view-distance=2\n"
                        + "simulation-distance=2\n"
                        + "pause-when-empty-seconds=0\n"
                        + "management-server-enabled=false\n"
                        + "enable-rcon=false\n"
                        + "log-ips=false\n");
        Files.writeString(server.resolve("whitelist.json"), "[]\n");
        Instant start = Instant.now();
        String status = "failed", problem = "";
        int passedStages = 0;
        try {
            for (int stage = 1; stage <= 2; stage++) {
                run(server, launch, output.resolve("stage-" + stage + ".log"), stage);
                passedStages++;
            }
            status = "passed";
        } catch (Exception error) {
            problem = Objects.toString(error.getMessage(), error.getClass().getSimpleName());
        } finally {
            var report =
                    new StringBuilder("{\"format\":2,\"status\":")
                            .append(json(status))
                            .append(",\"preset\":").append(json(bundle.getFileName().toString()))
                            .append(",\"loader\":").append(json(loader))
                            .append(",\"loader_version\":").append(json(version))
                            .append(",\"stages_passed\":").append(passedStages)
                            .append(",\"elapsed_seconds\":")
                            .append(Duration.between(start, Instant.now()).toSeconds())
                            .append(",\"bundle_sha256\":")
                            .append(json(sha(bundle)))
                            .append(",\"error\":")
                            .append(json(problem))
                            .append(",\"mods\":{");
            boolean first = true;
            for (var e : inventory.entrySet()) {
                if (!first) report.append(',');
                first = false;
                report.append(json(e.getKey())).append(':').append(json(e.getValue()));
            }
            report.append("}}\n");
            Files.writeString(output.resolve("results.json"), report);
        }
        System.out.println("Pack validation " + status + ": " + output.resolve("results.json"));
        if (!status.equals("passed")) throw new IllegalStateException(problem);
    }

    private static void run(Path server, List<String> args, Path log, int stage) throws Exception {
        String java = ProcessHandle.current().info().command().orElseThrow();
        var command = new ArrayList<>(List.of(java, "-Xmx1600m", "-XX:ActiveProcessorCount=4"));
        command.addAll(args);
        command.add("--nogui");
        var process = new ProcessBuilder(command)
                        .directory(server.toFile())
                        .redirectErrorStream(true)
                        .start();
        var text = new StringBuilder();
        var failure = new AtomicReference<Throwable>();
        Thread reader =
                Thread.ofPlatform()
                        .daemon()
                        .start(
                                () -> {
                                    try (var in = process.inputReader(StandardCharsets.UTF_8);
                                            var out = Files.newBufferedWriter(log)) {
                                        String line;
                                        while ((line = in.readLine()) != null) {
                                            out.write(line);
                                            out.newLine();
                                            out.flush();
                                            synchronized (text) {
                                                text.append(line).append('\n');
                                                if (text.length() > 4_000_000)
                                                    text.delete(0, text.length() - 4_000_000);
                                            }
                                        }
                                    } catch (Throwable error) {
                                        failure.set(error);
                                    }
                                });
        try (var input = process.outputWriter(StandardCharsets.UTF_8)) {
            await(process, text, "Done (", 180, failure);
            input.write("wa_pack_test " + stage + "\n");
            input.flush();
            await(process, text, "WA_PACK_TEST_PASS stage=" + stage, 180, failure);
            input.write("save-all flush\n");
            input.flush();
            await(process, text, "Saved the game", 120, failure);
            input.write("stop\n");
            input.flush();
            if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0)
                throw new IOException("Server shutdown failed; inspect " + log);
        } finally {
            if (process.isAlive()) {
                process.destroy();
                if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroyForcibly();
            }
            reader.join(5000);
        }
    }

    private static void await(
            Process process,
            StringBuilder text,
            String marker,
            int seconds,
            AtomicReference<Throwable> failure)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < deadline) {
            synchronized (text) {
                int failureAt = text.indexOf("WA_PACK_TEST_FAILED ");
                if (failureAt >= 0) {
                    int end = text.indexOf("\n", failureAt);
                    if (end >= 0) throw new IOException(text.substring(failureAt + "WA_PACK_TEST_FAILED ".length(), end).strip());
                }
                if (text.indexOf(marker) >= 0) return;
            }
            if (failure.get() != null)
                throw new IOException("Cannot read server output", failure.get());
            if (!process.isAlive()) throw new IOException("Server exited before " + marker);
            Thread.sleep(100);
        }
        throw new IOException("Timed out waiting for " + marker);
    }

    private static void copyTree(Path source, Path target) throws IOException {
        Files.walkFileTree(
                source,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(
                            Path directory, java.nio.file.attribute.BasicFileAttributes attributes)
                            throws IOException {
                        if (Files.isSymbolicLink(directory))
                            throw new IOException("Runtime symlink is unsupported");
                        Files.createDirectories(target.resolve(source.relativize(directory)));
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(
                            Path file, java.nio.file.attribute.BasicFileAttributes attributes)
                            throws IOException {
                        if (!attributes.isRegularFile() || Files.isSymbolicLink(file))
                            throw new IOException("Runtime contains an unsupported file");
                        Files.copy(file, target.resolve(source.relativize(file)));
                        return FileVisitResult.CONTINUE;
                    }
                });
    }

    private static String sha(Path file) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(file)) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String json(String value) {
        var out = new StringBuilder("\"");
        for (char c : value.toCharArray())
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 32) out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        return out.append('"').toString();
    }
}
