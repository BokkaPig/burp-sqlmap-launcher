package uk.co.periculo.burpsqlmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds every sqlmap flag the UI can set, and turns them into an argv list.
 */
public class SqlmapOptions {

    // Detection / tuning
    public int level = 3;
    public int risk = 3;
    public String technique = "";          // subset of BEUSTQ, empty = sqlmap default
    public String dbms = "";               // empty = auto-detect
    public String testParams = "";         // -p
    public String skipParams = "";         // --skip
    public int threads = 1;
    public String delay = "";
    public String timeout = "";
    public String tamper = "";

    // Behaviour
    public boolean batch = true;
    public boolean randomAgent = false;
    public boolean forceSsl = false;
    public boolean flushSession = false;
    public boolean textOnly = false;
    public int verbosity = 1;

    // Proxy
    public boolean useProxy = false;
    public String proxy = "http://127.0.0.1:8080";

    // Enumeration
    public boolean banner = false;
    public boolean currentUser = false;
    public boolean currentDb = false;
    public boolean isDba = false;
    public boolean listDbs = false;
    public boolean listTables = false;
    public boolean listColumns = false;
    public boolean schema = false;
    public boolean dump = false;
    public boolean dumpAll = false;
    public boolean passwords = false;
    public boolean users = false;
    public boolean privileges = false;
    public String dbName = "";             // -D
    public String tblName = "";            // -T
    public String colName = "";            // -C

    // Anything the UI does not cover
    public String extraArgs = "";

    /**
     * Builds the full argv for a run. The request file is always passed with -r,
     * so headers, cookies and body come straight from Burp untouched.
     */
    public List<String> toArgs(String sqlmapPath, String requestFile) {
        List<String> a = new ArrayList<>();
        a.add(sqlmapPath);
        a.add("-r");
        a.add(requestFile);

        if (forceSsl) a.add("--force-ssl");
        if (level > 1) { a.add("--level"); a.add(String.valueOf(level)); }
        if (risk > 1) { a.add("--risk"); a.add(String.valueOf(risk)); }
        if (!technique.isBlank()) { a.add("--technique"); a.add(technique.trim()); }
        if (!dbms.isBlank()) { a.add("--dbms"); a.add(dbms.trim()); }
        if (!testParams.isBlank()) { a.add("-p"); a.add(testParams.trim()); }
        if (!skipParams.isBlank()) { a.add("--skip"); a.add(skipParams.trim()); }
        if (threads > 1) { a.add("--threads"); a.add(String.valueOf(threads)); }
        if (!delay.isBlank()) { a.add("--delay"); a.add(delay.trim()); }
        if (!timeout.isBlank()) { a.add("--timeout"); a.add(timeout.trim()); }
        if (!tamper.isBlank()) { a.add("--tamper"); a.add(tamper.trim()); }

        if (batch) a.add("--batch");
        if (randomAgent) a.add("--random-agent");
        if (flushSession) a.add("--flush-session");
        if (textOnly) a.add("--text-only");
        if (useProxy && !proxy.isBlank()) { a.add("--proxy"); a.add(proxy.trim()); }
        if (verbosity != 1) { a.add("-v"); a.add(String.valueOf(verbosity)); }

        if (banner) a.add("--banner");
        if (currentUser) a.add("--current-user");
        if (currentDb) a.add("--current-db");
        if (isDba) a.add("--is-dba");
        if (users) a.add("--users");
        if (passwords) a.add("--passwords");
        if (privileges) a.add("--privileges");
        if (listDbs) a.add("--dbs");
        if (listTables) a.add("--tables");
        if (listColumns) a.add("--columns");
        if (schema) a.add("--schema");
        if (dump) a.add("--dump");
        if (dumpAll) a.add("--dump-all");

        if (!dbName.isBlank()) { a.add("-D"); a.add(dbName.trim()); }
        if (!tblName.isBlank()) { a.add("-T"); a.add(tblName.trim()); }
        if (!colName.isBlank()) { a.add("-C"); a.add(colName.trim()); }

        a.addAll(tokenise(extraArgs));
        return a;
    }

    /**
     * Splits free-text extra args the way a shell would, honouring quotes so
     * things like --tamper="space2comment" or -C "user name" survive intact.
     */
    public static List<String> tokenise(String s) {
        List<String> out = new ArrayList<>();
        if (s == null || s.isBlank()) return out;

        StringBuilder cur = new StringBuilder();
        boolean inWord = false;
        char quote = 0;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
                else cur.append(c);
            } else if (c == '\'' || c == '"') {
                quote = c;
                inWord = true;
            } else if (Character.isWhitespace(c)) {
                if (inWord) { out.add(cur.toString()); cur.setLength(0); inWord = false; }
            } else {
                cur.append(c);
                inWord = true;
            }
        }
        if (inWord) out.add(cur.toString());
        return out;
    }

    /**
     * The flags alone, without the binary or the request file: what makes one
     * run meaningfully different from another when checking for duplicates.
     */
    public String flagSummary() {
        List<String> args = toArgs("sqlmap", "-");
        return String.join(" ", args.subList(3, args.size()));
    }

    /** Renders argv as a copy-pasteable shell command. */
    public static String toShellCommand(List<String> args) {
        StringBuilder sb = new StringBuilder();
        for (String arg : args) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(shellQuote(arg));
        }
        return sb.toString();
    }

    public static String shellQuote(String s) {
        if (!s.isEmpty() && s.matches("[A-Za-z0-9_@%+=:,./-]+")) return s;
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
