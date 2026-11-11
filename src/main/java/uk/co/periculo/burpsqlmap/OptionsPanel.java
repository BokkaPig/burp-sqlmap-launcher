package uk.co.periculo.burpsqlmap;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static uk.co.periculo.burpsqlmap.UiHelpers.*;

/** The flag builder: every widget here maps to one sqlmap option. */
public class OptionsPanel extends JTabbedPane {

    private static final String[] DBMS_VALUES = {
            "", "MySQL", "PostgreSQL", "Microsoft SQL Server", "Oracle", "SQLite",
            "MariaDB", "IBM DB2", "Firebird", "Sybase", "Microsoft Access",
            "SAP MaxDB", "Informix", "HSQLDB", "H2", "MonetDB", "Vertica",
            "Mckoi", "Presto", "Altibase", "MimerSQL", "ClickHouse", "CrateDB",
            "Cubrid", "InterSystems Cache", "eXtremeDB", "FrontBase", "Raima"
    };

    private final Settings settings;

    // Detection & tuning
    private final JSpinner level = spinner(3, 1, 5);
    private final JSpinner risk = spinner(3, 1, 3);
    private final JCheckBox techB = check("B", false, "Boolean-based blind");
    private final JCheckBox techE = check("E", false, "Error-based");
    private final JCheckBox techU = check("U", false, "UNION query-based");
    private final JCheckBox techS = check("S", false, "Stacked queries");
    private final JCheckBox techT = check("T", false, "Time-based blind");
    private final JCheckBox techQ = check("Q", false, "Inline queries");
    private final JComboBox<String> dbms = new JComboBox<>(DBMS_VALUES);
    private final JTextField testParams = field("", 24);
    private final JTextField skipParams = field("", 24);
    private final JSpinner threads = spinner(1, 1, 10);
    private final JTextField delay = field("", 5);
    private final JTextField timeout = field("", 5);
    private final JTextField tamper = field("", 24);
    private final JComboBox<TamperScripts.Tamper> tamperPicker = new JComboBox<>();
    private final JButton tamperClear = new JButton("Clear");
    private final JCheckBox batch = check("--batch", true, "Never ask for user input, use the default answer");
    private final JCheckBox randomAgent = check("--random-agent", false, "Use a randomly selected HTTP User-Agent");
    private final JCheckBox forceSsl = check("--force-ssl", false, "Force usage of HTTPS");
    private final JCheckBox flushSession = check("--flush-session", false, "Flush session files for the current target");
    private final JCheckBox textOnly = check("--text-only", false, "Compare pages based only on their textual content");
    private final JSpinner verbosity = spinner(1, 0, 6);

    // Enumeration
    private final JCheckBox banner = check("--banner", false, "Retrieve DBMS banner");
    private final JCheckBox currentUser = check("--current-user", false, "Retrieve DBMS current user");
    private final JCheckBox currentDb = check("--current-db", false, "Retrieve DBMS current database");
    private final JCheckBox isDba = check("--is-dba", false, "Detect if the DBMS current user is a DBA");
    private final JCheckBox users = check("--users", false, "Enumerate DBMS users");
    private final JCheckBox passwords = check("--passwords", false, "Enumerate DBMS user password hashes");
    private final JCheckBox privileges = check("--privileges", false, "Enumerate DBMS user privileges");
    private final JCheckBox listDbs = check("--dbs", false, "Enumerate DBMS databases");
    private final JCheckBox listTables = check("--tables", false, "Enumerate DBMS database tables");
    private final JCheckBox listColumns = check("--columns", false, "Enumerate DBMS database table columns");
    private final JCheckBox schema = check("--schema", false, "Enumerate DBMS schema");
    private final JCheckBox dump = check("--dump", false, "Dump DBMS database table entries");
    private final JCheckBox dumpAll = check("--dump-all", false, "Dump all DBMS databases tables entries");
    private final JTextField dbName = field("", 18);
    private final JTextField tblName = field("", 18);
    private final JTextField colName = field("", 18);

    // Extra
    private final JCheckBox useProxy = check("Route sqlmap through a proxy", false,
            "Send sqlmap's own traffic through Burp so you can see what it sends");
    private final JTextField proxy = field("http://127.0.0.1:8080", 28);
    private final JTextArea extraArgs = new JTextArea(4, 40);

    // Launcher
    private final JTextField sqlmapPath;
    private final JComboBox<String> terminal;
    private final JTextField workDir;
    private final JCheckBox holdOpen;

    public OptionsPanel(Settings settings, Runnable onChange) {
        this.settings = settings;
        this.sqlmapPath = field(settings.sqlmapPath, 30);
        this.terminal = new JComboBox<>(TerminalLauncher.available().toArray(new String[0]));
        this.terminal.setSelectedItem(settings.terminal);
        this.workDir = field(settings.workDir, 30);
        this.holdOpen = check("Keep the terminal open after sqlmap exits", settings.holdOpen,
                "Waits for Enter so you can read the output before the window closes");

        dbms.setEditable(true);
        buildTamperPicker();
        extraArgs.setLineWrap(true);
        extraArgs.setWrapStyleWord(true);
        extraArgs.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        addTab("Detection & tuning", detectionTab());
        addTab("Enumeration", enumerationTab());
        addTab("Proxy & extra flags", extraTab());
        addTab("Launcher", launcherTab());

        onChange(onChange,
                level, risk, techB, techE, techU, techS, techT, techQ, dbms, testParams,
                skipParams, threads, delay, timeout, tamper, batch, randomAgent, forceSsl,
                flushSession, textOnly, verbosity, banner, currentUser, currentDb, isDba,
                users, passwords, privileges, listDbs, listTables, listColumns, schema,
                dump, dumpAll, dbName, tblName, colName, useProxy, proxy, extraArgs,
                sqlmapPath, terminal, workDir, holdOpen);

        // Launcher widgets feed the persisted settings rather than the command line.
        onChange(this::syncSettings, sqlmapPath, terminal, workDir, holdOpen);
    }

    private Component detectionTab() {
        Form form = new Form();
        form.addRow("Level / risk:", level, new JLabel("  risk"), risk,
                new JLabel("   threads"), threads);
        form.addRow("Techniques:", techB, techE, techU, techS, techT, techQ,
                hint("(none ticked = sqlmap's default, all)"));
        form.addRow("DBMS:", dbms);
        form.addRow("Test parameter (-p):", testParams);
        form.addRow("Skip parameter (--skip):", skipParams);
        form.addRow("Tamper scripts:", tamper, tamperPicker, tamperClear);
        form.addRow("Delay / timeout:", delay, new JLabel("  timeout"), timeout,
                new JLabel("   verbosity"), verbosity);
        form.addRow("Flags:", batch, randomAgent, forceSsl);
        form.addRow("", flushSession, textOnly);
        form.addFiller();
        return scroll(form);
    }

    private Component enumerationTab() {
        Form form = new Form();
        form.addRow("Fingerprint:", banner, currentUser, currentDb, isDba);
        form.addRow("Accounts:", users, passwords, privileges);
        form.addRow("Structure:", listDbs, listTables, listColumns, schema);
        form.addRow("Data:", dump, dumpAll);
        form.addRow("Database (-D):", dbName);
        form.addRow("Table (-T):", tblName);
        form.addRow("Columns (-C):", colName);
        form.addFiller();
        return scroll(form);
    }

    private Component extraTab() {
        Form form = new Form();
        form.addRow("Proxy:", useProxy);
        form.addRow("Proxy URL:", proxy);
        form.addRow("Extra flags:", hint("Anything not covered above, written as you would on the command line"));
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(form, BorderLayout.NORTH);
        JScrollPane extra = new JScrollPane(extraArgs);
        extra.setBorder(BorderFactory.createTitledBorder("Appended verbatim"));
        wrapper.add(extra, BorderLayout.CENTER);
        return wrapper;
    }

    private Component launcherTab() {
        Form form = new Form();
        form.addRow("sqlmap binary:", sqlmapPath);
        form.addRow("Terminal:", terminal);
        form.addRow("Working directory:", workDir);
        form.addRow("", holdOpen);
        form.addRow("", hint("Each run gets its own subdirectory holding the request file, "
                + "the runner script and sqlmap's output."));
        form.addFiller();
        return scroll(form);
    }

    /**
     * sqlmap takes --tamper as a comma-separated list, so the dropdown appends to
     * the field rather than replacing it, and the field stays free-text for
     * anything custom.
     */
    private void buildTamperPicker() {
        List<TamperScripts.Tamper> scripts = TamperScripts.discover(settings.sqlmapPath);
        Path source = TamperScripts.locate(settings.sqlmapPath);

        tamperPicker.addItem(new TamperScripts.Tamper("", "Add a tamper script..."));
        scripts.forEach(tamperPicker::addItem);
        tamperPicker.setPrototypeDisplayValue(
                new TamperScripts.Tamper("space2randomblank  -  replaces each space with a random blank", ""));
        tamperPicker.setToolTipText(scripts.size() + " scripts"
                + (source == null ? " (built-in list; sqlmap's tamper directory was not found)"
                                  : " from " + source));
        tamperPicker.setRenderer(new TamperRenderer());

        tamperPicker.addActionListener(e -> {
            Object selected = tamperPicker.getSelectedItem();
            if (!(selected instanceof TamperScripts.Tamper chosen) || chosen.name().isEmpty()) return;
            appendTamper(chosen.name());
            tamperPicker.setSelectedIndex(0);
        });

        tamperClear.setToolTipText("Remove all tamper scripts");
        tamperClear.addActionListener(e -> tamper.setText(""));

        // The list outgrows the field after two or three picks, so keep the whole
        // thing readable on hover.
        onChange(() -> {
            String text = tamper.getText().trim();
            tamper.setToolTipText(text.isEmpty() ? "No tamper scripts selected" : text);
        }, tamper);
    }

    /** Adds one script to the comma-separated list, ignoring duplicates. */
    private void appendTamper(String name) {
        List<String> parts = new ArrayList<>();
        for (String part : tamper.getText().split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) parts.add(trimmed);
        }
        if (parts.contains(name)) return;
        parts.add(name);
        tamper.setText(String.join(",", parts));
    }

    /** Shows the description beside each name in the popup, but not in the closed box. */
    private static class TamperRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean selected, boolean focused) {
            super.getListCellRendererComponent(list, value, index, selected, focused);
            if (value instanceof TamperScripts.Tamper t) {
                if (t.name().isEmpty()) {
                    setText(t.description());
                } else if (index >= 0 && !t.description().isEmpty()) {
                    String description = t.description().length() > 58
                            ? t.description().substring(0, 55) + "..."
                            : t.description();
                    setText(t.name() + "  -  " + description);
                    list.setToolTipText(t.description());
                } else {
                    setText(t.name());
                }
            }
            return this;
        }
    }

    private static JLabel hint(String text) {
        JLabel label = new JLabel(text);
        label.setEnabled(false);
        return label;
    }

    private static JScrollPane scroll(Component c) {
        JScrollPane pane = new JScrollPane(c);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.getVerticalScrollBar().setUnitIncrement(16);
        return pane;
    }

    private void syncSettings() {
        settings.sqlmapPath = sqlmapPath.getText().trim().isEmpty() ? "sqlmap" : sqlmapPath.getText().trim();
        settings.terminal = comboText(terminal);
        settings.workDir = workDir.getText().trim();
        settings.holdOpen = holdOpen.isSelected();
        settings.save();
    }

    /** Ticks --force-ssl automatically when the selected target is HTTPS. */
    public void suggestForceSsl(boolean secure) {
        forceSsl.setSelected(secure);
    }

    /** Reads every widget into a fresh options object. */
    public SqlmapOptions snapshot() {
        SqlmapOptions o = new SqlmapOptions();
        o.level = (Integer) level.getValue();
        o.risk = (Integer) risk.getValue();

        StringBuilder tech = new StringBuilder();
        if (techB.isSelected()) tech.append('B');
        if (techE.isSelected()) tech.append('E');
        if (techU.isSelected()) tech.append('U');
        if (techS.isSelected()) tech.append('S');
        if (techT.isSelected()) tech.append('T');
        if (techQ.isSelected()) tech.append('Q');
        o.technique = tech.toString();

        o.dbms = comboText(dbms);
        o.testParams = testParams.getText();
        o.skipParams = skipParams.getText();
        o.threads = (Integer) threads.getValue();
        o.delay = delay.getText();
        o.timeout = timeout.getText();
        o.tamper = tamper.getText();
        o.batch = batch.isSelected();
        o.randomAgent = randomAgent.isSelected();
        o.forceSsl = forceSsl.isSelected();
        o.flushSession = flushSession.isSelected();
        o.textOnly = textOnly.isSelected();
        o.verbosity = (Integer) verbosity.getValue();

        o.useProxy = useProxy.isSelected();
        o.proxy = proxy.getText();

        o.banner = banner.isSelected();
        o.currentUser = currentUser.isSelected();
        o.currentDb = currentDb.isSelected();
        o.isDba = isDba.isSelected();
        o.users = users.isSelected();
        o.passwords = passwords.isSelected();
        o.privileges = privileges.isSelected();
        o.listDbs = listDbs.isSelected();
        o.listTables = listTables.isSelected();
        o.listColumns = listColumns.isSelected();
        o.schema = schema.isSelected();
        o.dump = dump.isSelected();
        o.dumpAll = dumpAll.isSelected();
        o.dbName = dbName.getText();
        o.tblName = tblName.getText();
        o.colName = colName.getText();

        o.extraArgs = extraArgs.getText();
        return o;
    }
}
