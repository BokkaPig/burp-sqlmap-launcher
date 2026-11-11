package uk.co.periculo.burpsqlmap;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.*;

/** Small Swing conveniences so the panels stay readable. */
final class UiHelpers {

    private UiHelpers() {
    }

    /** A left-aligned label/field form that does not stretch its rows vertically. */
    static class Form extends JPanel {
        private final GridBagConstraints labelGbc = new GridBagConstraints();
        private final GridBagConstraints fieldGbc = new GridBagConstraints();
        private int row = 0;

        Form() {
            super(new GridBagLayout());
            setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

            labelGbc.anchor = GridBagConstraints.WEST;
            labelGbc.insets = new Insets(3, 0, 3, 8);
            labelGbc.gridx = 0;

            fieldGbc.anchor = GridBagConstraints.WEST;
            fieldGbc.fill = GridBagConstraints.HORIZONTAL;
            fieldGbc.insets = new Insets(3, 0, 3, 0);
            fieldGbc.gridx = 1;
            fieldGbc.weightx = 1.0;
        }

        void addRow(String label, Component field) {
            labelGbc.gridy = row;
            fieldGbc.gridy = row;
            add(new JLabel(label), labelGbc);
            add(field, fieldGbc);
            row++;
        }

        /** A row of components sharing one label. */
        void addRow(String label, Component... fields) {
            JPanel strip = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            strip.setOpaque(false);
            for (Component c : fields) strip.add(c);
            addRow(label, strip);
        }

        /** Pushes everything above it to the top of the panel. */
        void addFiller() {
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = row++;
            gbc.weighty = 1.0;
            gbc.fill = GridBagConstraints.VERTICAL;
            add(Box.createGlue(), gbc);
        }
    }

    static JTextField field(String initial, int columns) {
        JTextField f = new JTextField(initial, columns);
        return f;
    }

    static JCheckBox check(String text, boolean selected, String tooltip) {
        JCheckBox box = new JCheckBox(text, selected);
        box.setToolTipText(tooltip);
        return box;
    }

    static JSpinner spinner(int value, int min, int max) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
        ((JSpinner.DefaultEditor) s.getEditor()).getTextField().setColumns(2);
        return s;
    }

    /** Fires {@code onChange} whenever any of the given widgets is edited. */
    static void onChange(Runnable onChange, JComponent... components) {
        for (JComponent c : components) {
            if (c instanceof JTextComponent text) {
                text.getDocument().addDocumentListener(new DocumentListener() {
                    public void insertUpdate(DocumentEvent e) { onChange.run(); }
                    public void removeUpdate(DocumentEvent e) { onChange.run(); }
                    public void changedUpdate(DocumentEvent e) { onChange.run(); }
                });
            } else if (c instanceof AbstractButton button) {
                button.addActionListener(e -> onChange.run());
            } else if (c instanceof JComboBox<?> combo) {
                combo.addActionListener(e -> onChange.run());
                if (combo.isEditable() && combo.getEditor().getEditorComponent() instanceof JTextComponent editor) {
                    editor.getDocument().addDocumentListener(new DocumentListener() {
                        public void insertUpdate(DocumentEvent e) { onChange.run(); }
                        public void removeUpdate(DocumentEvent e) { onChange.run(); }
                        public void changedUpdate(DocumentEvent e) { onChange.run(); }
                    });
                }
            } else if (c instanceof JSpinner spinner) {
                spinner.addChangeListener(e -> onChange.run());
            }
        }
    }

    static String comboText(JComboBox<String> combo) {
        Object value = combo.isEditable() ? combo.getEditor().getItem() : combo.getSelectedItem();
        return value == null ? "" : value.toString().trim();
    }
}
