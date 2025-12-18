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
                HuFileData queryData = readHuData(queryFile);
                if (queryData.rows().isEmpty()) {
                    return "Query file contains no rows with Hu1–Hu7 values.";
                }

                List<ReferenceOutcome> outcomes = new ArrayList<>();
                double bestDistance = Double.POSITIVE_INFINITY;
                ReferenceOutcome bestOutcome = null;

                for (Path reference : referenceFiles) {
                    HuFileData referenceData = readHuData(reference);
                    int rowsCompared = Math.min(queryData.rows().size(), referenceData.rows().size());
                    Double averageDistance = rowsCompared > 0
                            ? computeAverageDistance(queryData.rows(), referenceData.rows(), rowsCompared)
                            : null;

                    boolean huMomentsOk = rowsCompared > 0 && queryData.hasHuColumns() && referenceData.hasHuColumns();
                    String segmentationStatus = segmentationStatus(queryData.rows().size(), rowsCompared);

                    ReferenceOutcome outcome = new ReferenceOutcome(
                            reference,
                            rowsCompared,
                            averageDistance,
                            segmentationStatus,
                            huMomentsOk
                    );
                    outcomes.add(outcome);

                    if (averageDistance != null && averageDistance < bestDistance) {
                        bestDistance = averageDistance;
                        bestOutcome = outcome;
                    }
                }

                finalizeOutcomes(outcomes, bestOutcome);

                return buildSummaryOutput(outcomes);
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

    private HuFileData readHuData(Path path) throws IOException {
        List<double[]> rows = new ArrayList<>();
        boolean hasHuColumns = false;
        int[] huIndexes = null;

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IOException("File \"" + path + "\" is empty.");
            }

            String[] headers = splitCsvLine(headerLine);
            huIndexes = locateHuColumns(headers);
            hasHuColumns = huIndexes != null;

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] cells = splitCsvLine(line);
                if (!hasHuColumns) {
                    continue;
                }

                double[] huValues = new double[REQUIRED_HU_COLUMNS];
                boolean rowValid = true;
                for (int i = 0; i < REQUIRED_HU_COLUMNS; i++) {
                    int columnIndex = huIndexes[i];
                    if (columnIndex < 0 || columnIndex >= cells.length) {
                        rowValid = false;
                        break;
                    }
                    try {
                        huValues[i] = Double.parseDouble(cells[columnIndex]);
                    } catch (NumberFormatException ex) {
                        rowValid = false;
                        break;
                    }
                }

                if (rowValid) {
                    rows.add(huValues);
                }
            }
        }
        return new HuFileData(List.copyOf(rows), hasHuColumns);
    }

    private int[] locateHuColumns(String[] headers) {
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
                return null;
            }
        }
        return indexes;
    }

    private String buildSummaryOutput(List<ReferenceOutcome> outcomes) {
        StringBuilder builder = new StringBuilder();
        builder.append("Hu-moment comparison success summary (lower average distance = closer match)\n\n");
        builder.append(renderSummaryTable(outcomes));
        return builder.toString();
    }

    private String renderSummaryTable(List<ReferenceOutcome> outcomes) {
        String[][] rows = new String[outcomes.size() + 1][6];
        rows[0] = new String[]{
                "Reference CSV",
                "Average Hu distance",
                "Closest Match",
                "Segmentation OK",
                "Hu Moments OK",
                "Overall Result"
        };

        for (int i = 0; i < outcomes.size(); i++) {
            ReferenceOutcome outcome = outcomes.get(i);
            rows[i + 1] = new String[]{
                    outcome.reference().getFileName().toString(),
                    outcome.averageDistanceText(),
                    outcome.closestMatch() ? "Yes" : "No",
                    outcome.segmentationStatus(),
                    outcome.huMomentsOk() ? "Yes" : "No",
                    outcome.overallResult()
            };
        }

        int[] widths = new int[rows[0].length];
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                widths[i] = Math.max(widths[i], row[i].length());
            }
        }

        StringBuilder table = new StringBuilder();
        String border = buildBorder(widths);
        table.append(border);
        table.append(formatRow(rows[0], widths));
        table.append(border);
        for (int i = 1; i < rows.length; i++) {
            table.append(formatRow(rows[i], widths));
        }
        table.append(border);
        return table.toString();
    }

    private String buildBorder(int[] widths) {
        StringBuilder builder = new StringBuilder();
        builder.append('+');
        for (int width : widths) {
            builder.append("-").append("-".repeat(width)).append('-').append('+');
        }
        builder.append('\n');
        return builder.toString();
    }

    private String formatRow(String[] row, int[] widths) {
        StringBuilder builder = new StringBuilder();
        builder.append('|');
        for (int i = 0; i < row.length; i++) {
            builder.append(' ').append(padRight(row[i], widths[i])).append(' ').append('|');
        }
        builder.append('\n');
        return builder.toString();
    }

    private String padRight(String value, int width) {
        if (value.length() >= width) {
            return value;
        }
        return value + " ".repeat(width - value.length());
    }

    private String segmentationStatus(int queryRows, int rowsCompared) {
        if (rowsCompared == 0) {
            return "No";
        }
        if (rowsCompared == queryRows) {
            return "Yes";
        }
        return "Partial";
    }

    private void finalizeOutcomes(List<ReferenceOutcome> outcomes, ReferenceOutcome bestOutcome) {
        for (ReferenceOutcome outcome : outcomes) {
            boolean isClosest = outcome == bestOutcome;
            outcome.setClosestMatch(isClosest);
            boolean success = isClosest && !"No".equals(outcome.segmentationStatus()) && outcome.huMomentsOk();
            outcome.setOverallResult(success ? "Success" : "Failure");
        }
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

    private record HuFileData(List<double[]> rows, boolean hasHuColumns) {
    }

    private class ReferenceOutcome {
        private final Path reference;
        private final int rowsCompared;
        private final Double averageDistance;
        private final String segmentationStatus;
        private final boolean huMomentsOk;
        private boolean closestMatch;
        private String overallResult = "Failure";

        ReferenceOutcome(Path reference, int rowsCompared, Double averageDistance, String segmentationStatus, boolean huMomentsOk) {
            this.reference = reference;
            this.rowsCompared = rowsCompared;
            this.averageDistance = averageDistance;
            this.segmentationStatus = segmentationStatus;
            this.huMomentsOk = huMomentsOk;
        }

        Path reference() {
            return reference;
        }

        String segmentationStatus() {
            return segmentationStatus;
        }

        boolean huMomentsOk() {
            return huMomentsOk;
        }

        boolean closestMatch() {
            return closestMatch;
        }

        void setClosestMatch(boolean closestMatch) {
            this.closestMatch = closestMatch;
        }

        String overallResult() {
            return overallResult;
        }

        void setOverallResult(String overallResult) {
            this.overallResult = overallResult;
        }

        String averageDistanceText() {
            if (averageDistance == null) {
                return "-";
            }
            return formatDistance(averageDistance);
        }
    }

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
