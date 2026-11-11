package uk.co.periculo.burpsqlmap;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Endpoints the detector picked out, with what has already been tested marked. */
public class CandidatesPanel extends JPanel {

    private static final DateTimeFormatter SEEN =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final MontoyaApi api;
    private final CandidateStore store;
    private final TestedRegistry tested;
    private final CandidateDetector detector;
    private final SqlmapTab runTab;

    private final CandidateTableModel model = new CandidateTableModel();
    private final JTable table = new JTable(model);
    private final TableRowSorter<CandidateTableModel> sorter = new TableRowSorter<>(model);
    private final JTextArea detail = new JTextArea();
    private final JLabel counts = new JLabel();
    private final JTextField filterField = new JTextField(16);
    private final JCheckBox hideTested = new JCheckBox("Hide tested", false);

    public CandidatesPanel(MontoyaApi api, CandidateStore store, TestedRegistry tested,
                           CandidateDetector detector, SqlmapTab runTab) {
        super(new BorderLayout());
        this.api = api;
        this.store = store;
        this.tested = tested;
        this.detector = detector;
        this.runTab = runTab;

        table.setRowSorter(sorter);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setAutoCreateRowSorter(false);
        table.getTableHeader().setReorderingAllowed(false);
        sorter.setSortKeys(List.of(new RowSorter.SortKey(1, SortOrder.DESCENDING)));
        table.getColumnModel().getColumn(1).setCellRenderer(new ScoreRenderer());
        setColumnWidths(28, 50, 60, 150, 220, 160, 240, 45, 70);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showDetail();
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) sendToSqlmap();
            }
        });

        detail.setEditable(false);
        detail.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(table), new JScrollPane(detail));
        split.setResizeWeight(0.72);

        add(toolbar(), BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);

        // Proxy traffic arrives far faster than a table should repaint, so the
        // table pulls on a timer rather than being pushed to per request.
        Timer poll = new Timer(800, e -> {
            if (store.consumeDirty()) refresh();
        });
        poll.start();

        filterField.getDocument().addDocumentListener(new SimpleDocumentListener(this::applyFilter));
        hideTested.addActionListener(e -> applyFilter());

        refresh();
    }

    private JComponent toolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));

        JCheckBox enabled = new JCheckBox("Detection on", detector.enabled);
        enabled.setToolTipText("Passively score traffic passing through Burp");
        enabled.addActionListener(e -> detector.enabled = enabled.isSelected());

        JCheckBox inScope = new JCheckBox("In scope only", detector.inScopeOnly);
        inScope.addActionListener(e -> detector.inScopeOnly = inScope.isSelected());

        JCheckBox proxyOnly = new JCheckBox("Proxy traffic only", detector.proxyOnly);
        proxyOnly.setToolTipText("Ignore Repeater, Scanner and Intruder traffic");
        proxyOnly.addActionListener(e -> detector.proxyOnly = proxyOnly.isSelected());

        JButton send = new JButton("Send to sqlmap");
        send.addActionListener(e -> sendToSqlmap());
        JButton mark = new JButton("Mark as tested");
        mark.setToolTipText("For runs done outside this extension");
        mark.addActionListener(e -> markTested());
        JButton forget = new JButton("Clear tested mark");
        forget.addActionListener(e -> forgetTested());
        JButton remove = new JButton("Remove");
        remove.addActionListener(e -> removeSelected());
        JButton clear = new JButton("Clear list");
        clear.addActionListener(e -> {
            store.clear();
            refresh();
        });

        bar.add(enabled);
        bar.add(inScope);
        bar.add(proxyOnly);
        bar.add(new JSeparator(SwingConstants.VERTICAL));
        bar.add(send);
        bar.add(mark);
        bar.add(forget);
        bar.add(remove);
        bar.add(clear);
        bar.add(new JLabel("  Filter:"));
        bar.add(filterField);
        bar.add(hideTested);
        bar.add(counts);
        return bar;
    }

    private void setColumnWidths(int... widths) {
        for (int i = 0; i < widths.length && i < table.getColumnModel().getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    private void refresh() {
        List<Candidate> rows = store.all();
        rows.sort(Comparator.comparingInt((Candidate c) -> c.score).reversed());
        model.setRows(rows);
        applyFilter();

        long testedCount = rows.stream().filter(c -> tested.isTested(c.key)).count();
        counts.setText("   " + rows.size() + " candidates, " + testedCount + " tested");
    }

    private void applyFilter() {
        String text = filterField.getText().trim().toLowerCase();
        sorter.setRowFilter(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends CandidateTableModel, ? extends Integer> entry) {
                Candidate c = model.candidateAt(entry.getIdentifier());
                if (c == null) return false;
                if (hideTested.isSelected() && tested.isTested(c.key)) return false;
                if (text.isEmpty()) return true;
                return (c.key.host() + c.key.path() + c.key.params() + c.evidence())
                        .toLowerCase().contains(text);
            }
        });
    }

    private List<Candidate> selected() {
        List<Candidate> out = new ArrayList<>();
        for (int viewRow : table.getSelectedRows()) {
            Candidate c = model.candidateAt(table.convertRowIndexToModel(viewRow));
            if (c != null) out.add(c);
        }
        return out;
    }

    private void sendToSqlmap() {
        List<Candidate> chosen = selected();
        if (chosen.isEmpty()) return;
        List<HttpRequestResponse> samples = new ArrayList<>();
        for (Candidate c : chosen) {
            if (c.sample != null) samples.add(c.sample);
        }
        if (samples.isEmpty()) return;
        runTab.addTasks(samples);
        runTab.bringToFront();
    }

    private void markTested() {
        for (Candidate c : selected()) {
            tested.record(c.key, "(marked manually)", "marked tested in Burp");
        }
        refresh();
    }

    private void forgetTested() {
        for (Candidate c : selected()) {
            tested.forget(c.key);
        }
        refresh();
        showDetail();
    }

    private void removeSelected() {
        for (Candidate c : selected()) {
            store.remove(c);
        }
        refresh();
    }

    private void showDetail() {
        List<Candidate> chosen = selected();
        if (chosen.size() != 1) {
            detail.setText(chosen.isEmpty()
                    ? "Select a candidate to see why it was flagged and what has already been run against it."
                    : chosen.size() + " candidates selected.");
            return;
        }

        Candidate c = chosen.get(0);
        StringBuilder sb = new StringBuilder();
        sb.append(c.key).append('\n');
        sb.append("Score    : ").append(c.score).append("  (").append(c.severity()).append(")\n");
        sb.append("Why      : ").append(c.evidence()).append('\n');
        sb.append("Seen     : ").append(c.seen).append(" time(s), last at ")
          .append(SEEN.format(c.lastSeen)).append('\n');
        if (c.cookieCount > 0) {
            sb.append("Cookies  : ").append(c.cookieCount)
              .append(" (not part of the endpoint identity; sqlmap tests these at --level 2+)\n");
        }

        List<TestedRegistry.Run> runs = tested.runsFor(c.key);
        sb.append('\n');
        if (runs.isEmpty()) {
            sb.append("Not yet tested.\n");
        } else {
            sb.append("Already tested - ").append(runs.size()).append(" run(s):\n");
            for (TestedRegistry.Run run : runs) {
                sb.append("  ").append(run.whenText()).append("   ")
                  .append(run.flags().isBlank() ? "(no flags)" : run.flags()).append('\n');
            }
        }
        detail.setText(sb.toString());
        detail.setCaretPosition(0);
    }

    /** Colours the score so the list can be read at a glance. */
    private class ScoreRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(t, value, selected, focused, row, column);
            Candidate c = model.candidateAt(t.convertRowIndexToModel(row));
            if (c != null && !selected) {
                setForeground(switch (c.severity()) {
                    case "High" -> new Color(0xC0392B);
                    case "Medium" -> new Color(0xB9770E);
                    default -> t.getForeground();
                });
            }
            setHorizontalAlignment(CENTER);
            return this;
        }
    }

    private class CandidateTableModel extends AbstractTableModel {

        private final String[] columns = {"Tested", "Score", "Method", "Host", "Path",
                "Parameters", "Why", "Hits", "Last seen"};
        private List<Candidate> rows = new ArrayList<>();

        void setRows(List<Candidate> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        Candidate candidateAt(int modelRow) {
            return modelRow >= 0 && modelRow < rows.size() ? rows.get(modelRow) : null;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return (column == 1 || column == 7) ? Integer.class : String.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            Candidate c = rows.get(row);
            int runs = tested.runCount(c.key);
            return switch (column) {
                case 0 -> runs == 0 ? "" : (runs == 1 ? "✓" : "✓ x" + runs);
                case 1 -> c.score;
                case 2 -> c.key.method();
                case 3 -> c.key.host();
                case 4 -> c.key.path();
                case 5 -> c.key.paramList();
                case 6 -> c.evidence();
                case 7 -> c.seen;
                case 8 -> SEEN.format(c.lastSeen);
                default -> "";
            };
        }
    }

    /** Saves repeating the three DocumentListener methods everywhere. */
    private record SimpleDocumentListener(Runnable onChange)
            implements javax.swing.event.DocumentListener {
        public void insertUpdate(javax.swing.event.DocumentEvent e) { onChange.run(); }
        public void removeUpdate(javax.swing.event.DocumentEvent e) { onChange.run(); }
        public void changedUpdate(javax.swing.event.DocumentEvent e) { onChange.run(); }
    }
}
