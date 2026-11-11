package uk.co.periculo.burpsqlmap;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The tamper scripts available to the local sqlmap, read from its tamper
 * directory so the list matches the installed version rather than whatever was
 * current when this extension was written. Each one's description comes from the
 * docstring on its tamper() function.
 */
public final class TamperScripts {

    public record Tamper(String name, String description) {
        @Override
        public String toString() {
            return name;
        }
    }

    /** Used when the tamper directory cannot be located. */
    private static final String[] FALLBACK = {
            "apostrophemask", "apostrophenullencode", "appendnullbyte", "base64encode",
            "between", "bluecoat", "chardoubleencode", "charencode", "charunicodeencode",
            "commalesslimit", "commalessmid", "concat2concatws", "equaltolike", "greatest",
            "halfversionedmorekeywords", "ifnull2ifisnull", "modsecurityversioned",
            "modsecurityzeroversioned", "multiplespaces", "nonrecursivereplacement",
            "percentage", "randomcase", "randomcomments", "securesphere", "space2comment",
            "space2dash", "space2hash", "space2morehash", "space2mssqlblank", "space2mysqldash",
            "space2plus", "space2randomblank", "symboliclogical", "unionalltounion",
            "unmagicquotes", "versionedkeywords", "versionedmorekeywords", "xforwardedfor"
    };

    private static final String[] WELL_KNOWN_DIRS = {
            "/usr/share/sqlmap/tamper",
            "/usr/local/share/sqlmap/tamper",
            "/opt/sqlmap/tamper",
            System.getProperty("user.home") + "/sqlmap/tamper",
            System.getProperty("user.home") + "/tools/sqlmap/tamper",
            System.getProperty("user.home") + "/.local/share/sqlmap/tamper"
    };

    private TamperScripts() {
    }

    /**
     * Finds and reads the tamper directory, falling back to a built-in name list.
     * The text field stays free-text either way, so a missing description never
     * stops anyone using a script.
     */
    public static List<Tamper> discover(String sqlmapPath) {
        Path dir = locate(sqlmapPath);
        if (dir != null) {
            List<Tamper> found = read(dir);
            if (!found.isEmpty()) return found;
        }
        List<Tamper> fallback = new ArrayList<>();
        for (String name : FALLBACK) fallback.add(new Tamper(name, ""));
        return fallback;
    }

    /** Where the scripts were read from, for display. Null when the fallback was used. */
    public static Path locate(String sqlmapPath) {
        if (sqlmapPath != null && !sqlmapPath.isBlank()) {
            Path binary = sqlmapPath.contains("/") ? Path.of(sqlmapPath) : onPath(sqlmapPath);
            if (binary != null) {
                try {
                    // /usr/bin/sqlmap is usually a symlink into the real install.
                    Path real = binary.toRealPath();
                    Path candidate = real.getParent() == null ? null : real.getParent().resolve("tamper");
                    if (candidate != null && Files.isDirectory(candidate)) return candidate;
                } catch (IOException ignored) {
                    // Fall through to the well-known locations.
                }
            }
        }
        for (String path : WELL_KNOWN_DIRS) {
            Path candidate = Path.of(path);
            if (Files.isDirectory(candidate)) return candidate;
        }
        return null;
    }

    private static Path onPath(String binary) {
        String path = System.getenv("PATH");
        if (path == null) return null;
        for (String dir : path.split(File.pathSeparator)) {
            Path candidate = Path.of(dir, binary);
            if (Files.isExecutable(candidate)) return candidate;
        }
        return null;
    }

    private static List<Tamper> read(Path dir) {
        List<Tamper> scripts = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(f -> f.getFileName().toString().endsWith(".py"))
                 .filter(f -> !f.getFileName().toString().equals("__init__.py"))
                 .sorted()
                 .forEach(f -> {
                     String name = f.getFileName().toString().replaceFirst("\\.py$", "");
                     scripts.add(new Tamper(name, describe(f)));
                 });
        } catch (IOException ignored) {
            // An unreadable directory just means no descriptions.
        }
        return scripts;
    }

    /** Pulls the first line of the docstring on the script's tamper() function. */
    private static String describe(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                if (!lines.get(i).stripLeading().startsWith("def tamper(")) continue;
                for (int j = i + 1; j < Math.min(i + 30, lines.size()); j++) {
                    if (!lines.get(j).contains("\"\"\"")) continue;
                    for (int k = j + 1; k < Math.min(j + 12, lines.size()); k++) {
                        String text = lines.get(k).trim();
                        if (!text.isEmpty() && !text.equals("\"\"\"")) return text;
                    }
                    break;
                }
                break;
            }
        } catch (IOException | RuntimeException ignored) {
            // Best effort only.
        }
        return "";
    }
}
