package uk.co.periculo.burpsqlmap;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.persistence.PersistedObject;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What has already been put through sqlmap, and with which flags.
 *
 * Stored in Burp's extension data, so it lives in the project file: the record
 * follows the engagement rather than the machine, and survives a restart.
 */
public class TestedRegistry {

    private static final String STORE_KEY = "testedRuns";
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    /** One recorded sqlmap run against one endpoint. */
    public record Run(String keyId, String display, String flags, long when, String note) {
        public String whenText() {
            return WHEN.format(Instant.ofEpochMilli(when));
        }
    }

    private final PersistedObject data;
    private final MontoyaApi api;
    private final Map<String, List<Run>> byKey = new ConcurrentHashMap<>();

    public TestedRegistry(MontoyaApi api) {
        this.api = api;
        this.data = api.persistence().extensionData();
        load();
    }

    public boolean isTested(EndpointKey key) {
        return byKey.containsKey(key.id());
    }

    public int runCount(EndpointKey key) {
        List<Run> runs = byKey.get(key.id());
        return runs == null ? 0 : runs.size();
    }

    public List<Run> runsFor(EndpointKey key) {
        return new ArrayList<>(byKey.getOrDefault(key.id(), List.of()));
    }

    public List<Run> all() {
        List<Run> out = new ArrayList<>();
        byKey.values().forEach(out::addAll);
        out.sort((a, b) -> Long.compare(b.when(), a.when()));
        return out;
    }

    public void record(EndpointKey key, String flags, String note) {
        Run run = new Run(key.id(), key.toString(), flags, System.currentTimeMillis(), note);
        byKey.computeIfAbsent(key.id(), id -> new ArrayList<>()).add(run);
        save();
    }

    public void forget(EndpointKey key) {
        byKey.remove(key.id());
        save();
    }

    public void clearAll() {
        byKey.clear();
        save();
    }

    // ---- persistence -------------------------------------------------------
    // Flat TSV rather than JSON: the records are small and this keeps the
    // extension dependency-free.

    private void load() {
        String blob = data.getString(STORE_KEY);
        if (blob == null || blob.isBlank()) return;
        for (String line : blob.split("\n")) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\t", -1);
            if (parts.length < 5) continue;
            try {
                Run run = new Run(unescape(parts[0]), unescape(parts[1]), unescape(parts[2]),
                        Long.parseLong(parts[3]), unescape(parts[4]));
                byKey.computeIfAbsent(run.keyId(), id -> new ArrayList<>()).add(run);
            } catch (NumberFormatException ignored) {
                // A corrupt row should not stop the rest from loading.
            }
        }
        api.logging().logToOutput("[sqlmap] loaded " + all().size()
                + " previous run(s) across " + byKey.size() + " endpoint(s).");
    }

    private void save() {
        StringBuilder sb = new StringBuilder();
        for (Run run : all()) {
            sb.append(escape(run.keyId())).append('\t')
              .append(escape(run.display())).append('\t')
              .append(escape(run.flags())).append('\t')
              .append(run.when()).append('\t')
              .append(escape(run.note())).append('\n');
        }
        data.setString(STORE_KEY, sb.toString());
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
    }

    private static String unescape(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(++i);
                switch (next) {
                    case 't' -> out.append('\t');
                    case 'n' -> out.append('\n');
                    case '\\' -> out.append('\\');
                    default -> out.append(next);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
