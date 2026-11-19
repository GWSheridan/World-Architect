package com.gwsheridan.worldarchitect.release;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.regex.Pattern;

/** Check the documentation shipped in the source and distribution archives. */
public final class DocumentationAudit {
    private static final Pattern LINK = Pattern.compile("\\[[^\\]]+\\]\\(([^)]+)\\)");

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        var failures = new ArrayList<String>();
        int count = 0;
        for (String directory : new String[] {"docs", "examples"}) {
            try (var files = Files.walk(root.resolve(directory))) {
                for (Path path : files.filter(p -> p.toString().endsWith(".md"))
                        .filter(p -> !p.toString().replace('\\', '/').matches(".*/(?:build|\\.gradle|\\.gradle-local|run)/.*")).toList()) {
                    inspect(root, path, failures);
                    count++;
                }
            }
        }
        for (String name : new String[] {"README.md", "CHANGELOG.md"}) inspect(root, root.resolve(name), failures);
        if (!failures.isEmpty()) throw new IllegalStateException(String.join("\n", failures));
        System.out.println("Documentation links checked in " + (count + 2) + " Markdown files.");
    }

    private static void inspect(Path root, Path file, ArrayList<String> failures) throws Exception {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        var links = LINK.matcher(text);
        while (links.find()) {
            String target = links.group(1).replaceFirst("#.*$", "");
            if (target.isEmpty() || target.startsWith("mailto:")) continue;
            String repository = "https://github.com/GWSheridan/World-Architect/";
            Path resolved;
            var versioned=Pattern.compile(Pattern.quote(repository)+"(?:blob|tree)/v[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][^/]+)?/(.+)").matcher(target);
            if (versioned.matches()) {
                resolved = root.resolve(versioned.group(1));
            } else if (target.matches("^[a-zA-Z]+:.*")) {
                continue;
            } else {
                resolved = file.getParent().resolve(target.replace("%20", " "));
            }
            if (!Files.exists(resolved.normalize())) failures.add(root.relativize(file) + ": missing " + target);
        }
    }
}
