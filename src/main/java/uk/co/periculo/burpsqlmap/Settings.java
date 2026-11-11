package uk.co.periculo.burpsqlmap;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.persistence.Preferences;

/** Extension-level settings, persisted in Burp's project preferences. */
public class Settings {

    private static final String KEY_SQLMAP = "sqlmap.path";
    private static final String KEY_TERMINAL = "sqlmap.terminal";
    private static final String KEY_WORKDIR = "sqlmap.workdir";
    private static final String KEY_HOLD = "sqlmap.holdOpen";

    private final Preferences prefs;

    public String sqlmapPath;
    public String terminal;
    public String workDir;
    public boolean holdOpen;

    public Settings(MontoyaApi api) {
        this.prefs = api.persistence().preferences();
        this.sqlmapPath = or(prefs.getString(KEY_SQLMAP), "sqlmap");
        this.terminal = or(prefs.getString(KEY_TERMINAL), TerminalLauncher.detectDefault());
        this.workDir = or(prefs.getString(KEY_WORKDIR),
                System.getProperty("java.io.tmpdir") + "/burp-sqlmap");
        Boolean h = prefs.getBoolean(KEY_HOLD);
        this.holdOpen = h == null || h;
    }

    public void save() {
        prefs.setString(KEY_SQLMAP, sqlmapPath);
        prefs.setString(KEY_TERMINAL, terminal);
        prefs.setString(KEY_WORKDIR, workDir);
        prefs.setBoolean(KEY_HOLD, holdOpen);
    }

    private static String or(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
