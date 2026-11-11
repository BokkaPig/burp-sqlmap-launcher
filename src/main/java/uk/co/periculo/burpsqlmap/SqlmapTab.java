package uk.co.periculo.burpsqlmap;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** The "sqlmap" suite tab: queued targets, the raw request, the flags and the Run button. */
public class SqlmapTab extends JPanel {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final MontoyaApi api;
    private final Settings settings;
    private final TestedRegistry tested;

    private final DefaultListModel<SqlmapTask> model = new DefaultListModel<>();
    private final JList<SqlmapTask> targets = new JList<>(model);
    private final JTextArea requestEditor = new JTextArea();
    private final JTextArea preview = new JTextArea(4, 80);
    private final OptionsPanel options;
    private final JLabel status = new JLabel(" ");

    private SqlmapTask current;

    public SqlmapTab(MontoyaApi api, Settings settings, TestedRegistry tested) {
        super(new BorderLayout());
        this.api = api;
        this.settings = settings;
        this.tested = tested;
        this.options = new OptionsPanel(settings, this::refreshPreview);

        targets.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        targets.setCellRenderer(new TestedAwareRenderer());
        targets.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) selectTask(targets.getSelectedValue());
        });

        requestEditor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        requestEditor.setTabSize(4);

        preview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        preview.setEditable(false);
        preview.setLineWrap(true);
        preview.setWrapStyleWord(true);

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                requestPanel(), options);
        rightSplit.setResizeWeight(0.45);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                targetsPanel(), rightSplit);
        mainSplit.setResizeWeight(0.22);

        add(mainSplit, BorderLayout.CENTER);
        add(bottomPanel(), BorderLayout.SOUTH);

        refreshPreview();
    }

    private JComponent targetsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Queued targets"));
        panel.add(new JScrollPane(targets), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(1, 2, 4, 0));
        JButton remove = new JButton("Remove");
        remove.addActionListener(e -> {
            int index = targets.getSelectedIndex();
            if (index >= 0) {
                current = null;
                model.remove(index);
                if (!model.isEmpty()) targets.setSelectedIndex(Math.min(index, model.size() - 1));
                else { requestEditor.setText(""); refreshPreview(); }
            }
        });
        JButton clear = new JButton("Clear");
        clear.addActionListener(e -> {
            current = null;
            model.clear();
            requestEditor.setText("");
            refreshPreview();
        });
        buttons.add(remove);
        buttons.add(clear);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent requestPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Request sent to sqlmap (-r)"));
        panel.add(new JScrollPane(requestEditor), BorderLayout.CENTER);

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JButton mark = new JButton("Mark injection point (*)");
        mark.setToolTipText("Insert sqlmap's '*' marker at the caret to test exactly that spot");
        mark.addActionListener(e -> {
            requestEditor.insert("*", requestEditor.getCaretPosition());
            requestEditor.requestFocusInWindow();
        });
        bar.add(mark);
        bar.add(new JLabel("Edits here are what sqlmap receives."));
        panel.add(bar, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent bottomPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Command"));

        JScrollPane previewScroll = new JScrollPane(preview);
        previewScroll.setPreferredSize(new Dimension(100, 80));
        panel.add(previewScroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton run = new JButton("Run in terminal");
        run.addActionListener(e -> run());
        JButton copy = new JButton("Copy command");
        copy.setToolTipText("Writes the request file and copies a command that will run as-is");
        copy.addActionListener(e -> copyCommand());
        JButton saveOnly = new JButton("Save request file only");
        saveOnly.addActionListener(e -> saveOnly());
        buttons.add(run);
        buttons.add(copy);
        buttons.add(saveOnly);
        buttons.add(Box.createHorizontalStrut(12));
        buttons.add(status);

        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    /** Puts a tick against anything already recorded as tested. */
    private class TestedAwareRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean selected, boolean focused) {
            super.getListCellRendererComponent(list, value, index, selected, focused);
            if (value instanceof SqlmapTask task) {
                int runs = tested.runCount(task.key);
                if (runs > 0) {
                    setText("\u2713 " + task);
                    setToolTipText("Already tested - " + runs + " previous run(s)");
                } else {
                    setToolTipText(task.key.toString());
                }
            }
            return this;
        }
    }

    /** Best effort: bring Burp's "sqlmap" tab to the front after a send. */
    public void bringToFront() {
        SwingUtilities.invokeLater(() -> {
            Container parent = getParent();
            while (parent != null) {
                if (parent instanceof JTabbedPane tabs) {
                    for (int i = 0; i < tabs.getTabCount(); i++) {
                        if (SwingUtilities.isDescendingFrom(this, tabs.getComponentAt(i))) {
                            tabs.setSelectedIndex(i);
                            break;
                        }
                    }
                }
                parent = parent.getParent();
            }
        });
    }

    /** Called from the context menu. */
    public void addTasks(List<HttpRequestResponse> requestResponses) {
        SwingUtilities.invokeLater(() -> {
            SqlmapTask first = null;
            for (HttpRequestResponse rr : requestResponses) {
                if (rr == null || rr.request() == null) continue;
                SqlmapTask task = new SqlmapTask(rr);
                model.addElement(task);
                if (first == null) first = task;
            }
            if (first != null) {
                targets.setSelectedValue(first, true);
                long already = java.util.stream.IntStream.range(0, model.size())
                        .mapToObj(model::get)
                        .filter(t -> tested.isTested(t.key))
                        .count();
                status.setText("Added " + requestResponses.size() + " request(s)."
                        + (already > 0 ? "  " + already + " in the queue already tested." : ""));
            }
        });
    }

    private void selectTask(SqlmapTask task) {
        if (current != null) current.rawRequest = requestEditor.getText();
        current = task;
        if (task == null) {
            requestEditor.setText("");
        } else {
            requestEditor.setText(task.rawRequest);
            requestEditor.setCaretPosition(0);
            options.suggestForceSsl(task.secure);
        }
        refreshPreview();
    }

    private void refreshPreview() {
        SqlmapOptions opts = options.snapshot();
        // Show the path a run started now would use, so the preview reads as a
        // real command rather than a template with a placeholder in it.
        Path requestFile = current == null
                ? Path.of(settings.workDir, "run", "request.txt")
                : taskDir(current).resolve("request.txt");
        preview.setText(SqlmapOptions.toShellCommand(
                opts.toArgs(settings.sqlmapPath, requestFile.toString())));
        preview.setCaretPosition(0);
    }

    /** Writes the request file, then copies a command that points at it. */
    private void copyCommand() {
        SqlmapTask task = currentTask();
        if (task == null) return;
        try {
            Path requestFile = writeRequest(task);
            String command = SqlmapOptions.toShellCommand(
                    options.snapshot().toArgs(settings.sqlmapPath, requestFile.toString()));
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(command), null);
            preview.setText(command);
            status.setText("Request written and command copied - paste it anywhere.");
            api.logging().logToOutput("[sqlmap] copied: " + command);
        } catch (IOException ex) {
            fail("Could not write the request file", ex);
        }
    }

    private void run() {
        SqlmapTask task = currentTask();
        if (task == null) return;

        SqlmapOptions opts = options.snapshot();
        String flags = opts.flagSummary();
        if (!confirmNotDuplicate(task, flags)) return;

        try {
            Path requestFile = writeRequest(task);
            Path taskDir = requestFile.getParent();

            List<String> args = opts.toArgs(settings.sqlmapPath, requestFile.toString());

            Path script = TerminalLauncher.launch(settings.terminal, taskDir, args, settings.holdOpen);

            String command = SqlmapOptions.toShellCommand(args);
            api.logging().logToOutput("[sqlmap] " + task + "\n  dir: " + taskDir + "\n  cmd: " + command);
            status.setText("Launched in " + settings.terminal + " - " + taskDir.getFileName());

            if ("tmux".equals(settings.terminal)) {
                status.setText("Started detached tmux session sqlmap-" + taskDir.getFileName()
                        + " (attach with: tmux attach -t sqlmap-" + taskDir.getFileName() + ")");
            }
            if (!task.hasInjectionMarker() && opts.testParams.isBlank()) {
                api.logging().logToOutput("  note: no '*' marker and no -p, so sqlmap will test every parameter it finds.");
            }

            tested.record(task.key, flags, "launched from Burp");
            targets.repaint();
        } catch (IOException ex) {
            fail("Could not start sqlmap", ex);
        }
    }

    private void saveOnly() {
        SqlmapTask task = currentTask();
        if (task == null) return;
        try {
            Path requestFile = writeRequest(task);
            status.setText("Saved " + requestFile);
            api.logging().logToOutput("[sqlmap] saved request to " + requestFile);
        } catch (IOException ex) {
            fail("Could not save the request file", ex);
        }
    }

    /**
     * Asks before repeating a run this endpoint has already had with exactly the
     * same flags. Different flags go through without a prompt, since testing
     * harder than last time is usually the point.
     */
    private boolean confirmNotDuplicate(SqlmapTask task, String flags) {
        List<TestedRegistry.Run> previous = tested.runsFor(task.key);
        TestedRegistry.Run same = previous.stream()
                .filter(r -> r.flags().equals(flags))
                .reduce((a, b) -> b)
                .orElse(null);

        if (same == null) {
            if (!previous.isEmpty()) {
                status.setText("Tested before with different flags (" + previous.size() + " run(s)) - continuing.");
            }
            return true;
        }

        int choice = JOptionPane.showConfirmDialog(this,
                "This endpoint has already been tested with exactly these flags.\n\n"
                        + task.key + "\n\nFlags: " + (flags.isBlank() ? "(none)" : flags)
                        + "\nLast run: " + same.whenText()
                        + "\n\nRun it again anyway?",
                "Already tested", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (choice != JOptionPane.YES_OPTION) {
            status.setText("Cancelled - already tested with these flags on " + same.whenText() + ".");
            return false;
        }
        return true;
    }

    private SqlmapTask currentTask() {
        if (current == null) {
            status.setText("Select a target first.");
            return null;
        }
        current.rawRequest = requestEditor.getText();
        return current;
    }

    /** Where a run started right now would put its files. Does not create anything. */
    private Path taskDir(SqlmapTask task) {
        String name = STAMP.format(LocalDateTime.now()) + "-"
                + task.host.replaceAll("[^A-Za-z0-9.-]", "_");
        return Path.of(settings.workDir, name);
    }

    private Path writeRequest(SqlmapTask task) throws IOException {
        Path dir = taskDir(task);
        Files.createDirectories(dir);
        Path requestFile = dir.resolve("request.txt");
        Files.write(requestFile, task.requestBytes());
        return requestFile;
    }

    private void fail(String message, Exception ex) {
        status.setText(message + ": " + ex.getMessage());
        api.logging().logToError("[sqlmap] " + message + ": " + ex);
        JOptionPane.showMessageDialog(this, message + "\n\n" + ex.getMessage(),
                "sqlmap Launcher", JOptionPane.ERROR_MESSAGE);
    }
}
