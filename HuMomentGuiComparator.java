import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * GUI application for comparing Hu moments exported from ImageJ Results tables.
 *
 * <p>The tool lets users pick a single query CSV and multiple reference CSV files.
 * For each reference file, it matches rows by index, extracts Hu1–Hu7, computes per-row
 * Euclidean distances, and reports the average distance. The closest match is highlighted
 * in the output area.</p>
 */
public class HuMomentGuiComparator extends JFrame {

    private static final int REQUIRED_HU_COLUMNS = 7;
    private static final DecimalFormat DISTANCE_FORMAT = new DecimalFormat("0.#####E0");

    private final JTextField queryField = new JTextField();
    private final JTextArea referencesArea = new JTextArea();
    private final JTextArea outputArea = new JTextArea();

    private Path queryFile;
    private final List<Path> referenceFiles = new ArrayList<>();

    public HuMomentGuiComparator() {
        super("Hu Moment CSV Comparator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        queryField.setEditable(false);
        referencesArea.setEditable(false);
        referencesArea.setLineWrap(true);
        referencesArea.setWrapStyleWord(true);
        referencesArea.setRows(3);
        outputArea.setEditable(false);
        outputArea.setLineWrap(true);
        outputArea.setWrapStyleWord(true);

        setLayout(new BorderLayout(8, 8));
        add(buildSelectionPanel(), BorderLayout.NORTH);
        add(buildOutputArea(), BorderLayout.CENTER);

        setPreferredSize(new Dimension(800, 600));
        pack();
        setLocationRelativeTo(null);
    }

    private JPanel buildSelectionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        JButton queryButton = new JButton("Select Query CSV...");
        queryButton.addActionListener(e -> chooseQueryFile());

        JButton referenceButton = new JButton("Select Reference CSVs...");
        referenceButton.addActionListener(e -> chooseReferenceFiles());

        JButton compareButton = new JButton("Compare");
        compareButton.addActionListener(this::runComparison);

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Query CSV:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(queryField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        panel.add(queryButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(new JLabel("Reference CSVs:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(new JScrollPane(referencesArea), gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        panel.add(referenceButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        panel.add(compareButton, gbc);

        return panel;
    }

    private JScrollPane buildOutputArea() {
        JScrollPane scrollPane = new JScrollPane(outputArea);
        scrollPane.setBorder(javax.swing.BorderFactory.createTitledBorder("Results"));
        return scrollPane;
    }

    private void chooseQueryFile() {
        JFileChooser chooser = buildCsvChooser();
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            queryFile = chooser.getSelectedFile().toPath();
            queryField.setText(queryFile.toAbsolutePath().toString());
        }
    }

    private void chooseReferenceFiles() {
        JFileChooser chooser = buildCsvChooser();
        chooser.setMultiSelectionEnabled(true);
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            referenceFiles.clear();
            Arrays.stream(chooser.getSelectedFiles())
                    .map(java.io.File::toPath)
                    .forEach(referenceFiles::add);
            updateReferenceListDisplay();
        }
    }

    private JFileChooser buildCsvChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("CSV Files", "csv"));
        return chooser;
    }

    private void updateReferenceListDisplay() {
        if (referenceFiles.isEmpty()) {
            referencesArea.setText("");
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (Path path : referenceFiles) {
            builder.append(path.getFileName()).append('\n');
        }
        referencesArea.setText(builder.toString());
        referencesArea.setCaretPosition(0);
    }

    private void runComparison(ActionEvent event) {
        if (queryFile == null) {
            JOptionPane.showMessageDialog(this, "Please select a query CSV file first.",
                    "Missing Query File", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (referenceFiles.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select at least one reference CSV file.",
                    "Missing Reference Files", JOptionPane.WARNING_MESSAGE);
            return;
        }

        outputArea.setText("Running comparison...\n");

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                List<double[]> queryRows = readHuVectors(queryFile);
                if (queryRows.isEmpty()) {
                    return "Query file contains no rows with Hu1–Hu7 values.";
                }

                StringBuilder builder = new StringBuilder();
                builder.append("Query file: ").append(queryFile.getFileName()).append('\n');
                builder.append("References: ").append(referenceFiles.size()).append(" file(s)\n\n");
                List<ReferenceResult> results = new ArrayList<>();

                for (Path reference : referenceFiles) {
                    List<double[]> referenceRows = readHuVectors(reference);
                    int rowsCompared = Math.min(queryRows.size(), referenceRows.size());
                    if (rowsCompared == 0) {
                        builder.append(reference.getFileName())
                                .append(" - no overlapping rows to compare.\n");
                        continue;
                    }

                    double averageDistance = computeAverageDistance(queryRows, referenceRows, rowsCompared);
                    results.add(new ReferenceResult(reference, averageDistance, rowsCompared));

                    builder.append(reference.getFileName())
                            .append(" - rows compared: ")
                            .append(rowsCompared)
                            .append(", average distance: ")
                            .append(formatDistance(averageDistance))
                            .append('\n');
                }

                ReferenceResult best = results.stream()
                        .filter(r -> Double.isFinite(r.averageDistance()))
                        .min(Comparator.comparingDouble(ReferenceResult::averageDistance))
                        .orElse(null);

                if (best != null) {
                    builder.append('\n')
                            .append("Closest match: ")
                            .append(best.path().getFileName())
                            .append(" (average distance ")
                            .append(formatDistance(best.averageDistance()))
                            .append(")");
                } else {
                    builder.append("No valid reference comparisons were completed.");
                }

                return builder.toString();
            }

            @Override
            protected void done() {
                try {
                    outputArea.setText(get());
                    outputArea.setCaretPosition(0);
                } catch (Exception ex) {
                    outputArea.setText("Comparison failed: " + ex.getMessage());
                }
            }
        };

        worker.execute();
    }

    private double computeAverageDistance(List<double[]> queryRows, List<double[]> referenceRows, int rowsCompared) {
        double total = 0.0;
        for (int i = 0; i < rowsCompared; i++) {
            total += euclideanDistance(queryRows.get(i), referenceRows.get(i));
        }
        return total / rowsCompared;
    }

    private double euclideanDistance(double[] query, double[] reference) {
        double sum = 0.0;
        for (int i = 0; i < REQUIRED_HU_COLUMNS; i++) {
            double delta = query[i] - reference[i];
            sum += delta * delta;
        }
        return Math.sqrt(sum);
    }

    private List<double[]> readHuVectors(Path path) throws IOException {
        List<double[]> rows = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IOException("File \"" + path + "\" is empty.");
            }

            String[] headers = splitCsvLine(headerLine);
            int[] huIndexes = locateHuColumns(headers);

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] cells = splitCsvLine(line);

                double[] huValues = new double[REQUIRED_HU_COLUMNS];
                for (int i = 0; i < REQUIRED_HU_COLUMNS; i++) {
                    int columnIndex = huIndexes[i];
                    if (columnIndex >= cells.length) {
                        throw new IOException("Row " + lineNumber + " in \"" + path + "\" is missing Hu column at index " + columnIndex + ".");
                    }
                    try {
                        huValues[i] = Double.parseDouble(cells[columnIndex]);
                    } catch (NumberFormatException ex) {
                        throw new IOException("Invalid number in row " + lineNumber + " (column " + headers[columnIndex] + ") of \"" + path + "\": " + cells[columnIndex]);
                    }
                }
                rows.add(huValues);
            }
        }
        return rows;
    }

    private int[] locateHuColumns(String[] headers) throws IOException {
        int[] indexes = new int[REQUIRED_HU_COLUMNS];
        for (int i = 0; i < REQUIRED_HU_COLUMNS; i++) {
            indexes[i] = -1;
        }

        for (int i = 0; i < headers.length; i++) {
            String header = headers[i].trim().toLowerCase();
            switch (header) {
                case "hu1" -> indexes[0] = i;
                case "hu2" -> indexes[1] = i;
                case "hu3" -> indexes[2] = i;
                case "hu4" -> indexes[3] = i;
                case "hu5" -> indexes[4] = i;
                case "hu6" -> indexes[5] = i;
                case "hu7" -> indexes[6] = i;
                default -> {
                    // Ignore non-Hu columns
                }
            }
        }

        for (int index : indexes) {
            if (index < 0) {
                throw new IOException("CSV file is missing one or more Hu columns (Hu1-Hu7).");
            }
        }
        return indexes;
    }

    private String[] splitCsvLine(String line) {
        return line.split(",", -1);
    }

    private String formatDistance(double distance) {
        if (Double.isNaN(distance) || Double.isInfinite(distance)) {
            return String.valueOf(distance);
        }
        return DISTANCE_FORMAT.format(distance);
    }

    private record ReferenceResult(Path path, double averageDistance, int rowsCompared) { }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Fallback to default look and feel.
            }
            new HuMomentGuiComparator().setVisible(true);
        });
    }
}
