import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Command-line utility to compare two Hu moments CSV exports.
 *
 * <p>Usage:
 * <pre>java HuMomentCsvComparator <fileA.csv> <fileB.csv> [tolerance]</pre>
 *
 * <p>Both files are expected to share the same header. The first column is treated
 * as the row identifier (often the measurement index). The comparator reports:
 * <ul>
 *     <li>Header mismatches</li>
 *     <li>Rows missing from either file</li>
 *     <li>Per-column numeric deviations beyond the supplied tolerance</li>
 * </ul>
 */
public class HuMomentCsvComparator {

    private static final int KEY_COLUMN_INDEX = 0;
    private static final int MAX_DIFFERENCE_OUTPUT = 50;

    public static void main(String[] args) {
        if (args.length < 2 || args.length > 3) {
            System.err.println("Usage: java HuMomentCsvComparator <fileA.csv> <fileB.csv> [tolerance]");
            System.err.println("Default tolerance is 0.0 (exact match).");
            System.exit(1);
        }

        Path firstPath = Paths.get(args[0]);
        Path secondPath = Paths.get(args[1]);
        double tolerance = args.length == 3 ? parseTolerance(args[2]) : 0.0;

        try {
            CsvTable first = CsvTable.read(firstPath);
            CsvTable second = CsvTable.read(secondPath);
            ComparisonResult result = compare(first, second, tolerance);
            result.print();
        } catch (IOException ex) {
            System.err.println("Failed to read input files: " + ex.getMessage());
            System.exit(2);
        }
    }

    private static double parseTolerance(String rawTolerance) {
        try {
            return Double.parseDouble(rawTolerance);
        } catch (NumberFormatException ex) {
            System.err.println("Invalid tolerance value \"" + rawTolerance + "\". Falling back to 0.0.");
            return 0.0;
        }
    }

    private static ComparisonResult compare(CsvTable first, CsvTable second, double tolerance) {
        boolean headersMatch = first.headers.equals(second.headers);

        Map<String, CsvRow> firstRows = toRowMap(first);
        Map<String, CsvRow> secondRows = toRowMap(second);

        Set<String> allKeys = new TreeSet<>();
        allKeys.addAll(firstRows.keySet());
        allKeys.addAll(secondRows.keySet());

        List<String> onlyInFirst = new ArrayList<>();
        List<String> onlyInSecond = new ArrayList<>();
        List<ValueDifference> valueDifferences = new ArrayList<>();

        for (String key : allKeys) {
            CsvRow left = firstRows.get(key);
            CsvRow right = secondRows.get(key);

            if (left == null) {
                onlyInSecond.add(key);
                continue;
            }
            if (right == null) {
                onlyInFirst.add(key);
                continue;
            }

            int columnCount = Math.min(left.values.size(), right.values.size());
            for (int column = 1; column < columnCount; column++) {
                String columnName = safeHeader(first.headers, column);
                ValueDifference difference = ValueDifference.of(
                        key,
                        columnName,
                        left.values.get(column),
                        right.values.get(column),
                        tolerance
                );
                if (difference != null) {
                    valueDifferences.add(difference);
                }
            }
        }

        return new ComparisonResult(
                first.headers,
                second.headers,
                headersMatch,
                firstRows.size(),
                secondRows.size(),
                onlyInFirst,
                onlyInSecond,
                valueDifferences,
                tolerance
        );
    }

    private static Map<String, CsvRow> toRowMap(CsvTable table) {
        Map<String, CsvRow> map = new LinkedHashMap<>();
        for (CsvRow row : table.rows) {
            String key = row.values.get(KEY_COLUMN_INDEX);
            map.put(key, row);
        }
        return map;
    }

    private static String safeHeader(List<String> headers, int index) {
        if (index >= 0 && index < headers.size()) {
            String header = headers.get(index);
            return header.isEmpty() ? "Column " + index : header;
        }
        return "Column " + index;
    }

    private record CsvTable(List<String> headers, List<CsvRow> rows) {

        static CsvTable read(Path path) throws IOException {
            try (BufferedReader reader = Files.newBufferedReader(path)) {
                String headerLine = reader.readLine();
                if (headerLine == null) {
                    throw new IOException("File \"" + path + "\" is empty.");
                }
                List<String> headers = parseLine(headerLine);

                List<CsvRow> rows = new ArrayList<>();
                String line;
                int lineNumber = 1;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    List<String> cells = parseLine(line);
                    if (cells.size() != headers.size()) {
                        throw new IOException(
                                "Row " + lineNumber + " in \"" + path + "\" has " + cells.size() +
                                        " columns but header has " + headers.size() + "."
                        );
                    }
                    rows.add(new CsvRow(cells));
                }
                return new CsvTable(Collections.unmodifiableList(headers), List.copyOf(rows));
            }
        }

        private static List<String> parseLine(String line) {
            // The exports from ImageJ are simple comma-separated values without quoting.
            String[] parts = line.split(",", -1);
            List<String> cells = new ArrayList<>(parts.length);
            for (String part : parts) {
                cells.add(part.trim());
            }
            return cells;
        }
    }

    private record CsvRow(List<String> values) {
    }

    private record ValueDifference(String key, String column, String left, String right, double delta) {

        static ValueDifference of(String key, String column, String left, String right, double tolerance) {
            Double leftNumber = parseNumber(left);
            Double rightNumber = parseNumber(right);

            if (leftNumber != null && rightNumber != null) {
                double delta = Math.abs(leftNumber - rightNumber);
                return delta > tolerance ? new ValueDifference(key, column, left, right, delta) : null;
            }

            if (!left.equals(right)) {
                return new ValueDifference(key, column, left, right, Double.NaN);
            }
            return null;
        }

        private static Double parseNumber(String value) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    private record ComparisonResult(
            List<String> firstHeader,
            List<String> secondHeader,
            boolean headersMatch,
            int firstRowCount,
            int secondRowCount,
            List<String> onlyInFirst,
            List<String> onlyInSecond,
            List<ValueDifference> differences,
            double tolerance
    ) {

        void print() {
            System.out.println("=== Hu Moment CSV Comparison ===");
            System.out.println("Tolerance: " + tolerance);
            System.out.println("Rows - first file: " + firstRowCount + ", second file: " + secondRowCount);

            if (!headersMatch) {
                System.out.println();
                System.out.println("Header mismatch detected.");
                System.out.println("First : " + String.join(", ", firstHeader));
                System.out.println("Second: " + String.join(", ", secondHeader));
            } else {
                System.out.println("Headers match (" + firstHeader.size() + " columns).");
            }

            printMissing("Only in first file", onlyInFirst);
            printMissing("Only in second file", onlyInSecond);
            printDifferences();
        }

        private void printMissing(String label, List<String> missing) {
            if (missing.isEmpty()) {
                return;
            }
            System.out.println();
            System.out.println(label + " (" + missing.size() + "):");
            for (String key : missing) {
                System.out.println("  - " + key);
            }
        }

        private void printDifferences() {
            if (differences.isEmpty()) {
                System.out.println();
                System.out.println("No value differences beyond tolerance.");
                return;
            }

            System.out.println();
            System.out.println("Value differences (" + differences.size() + "):");
            int limit = Math.min(MAX_DIFFERENCE_OUTPUT, differences.size());
            for (int i = 0; i < limit; i++) {
                ValueDifference diff = differences.get(i);
                String deltaText = Double.isNaN(diff.delta) ? "(non-numeric)" : ("Δ=" + diff.delta);
                System.out.println("  Row " + diff.key + " column \"" + diff.column + "\": "
                        + diff.left + " vs " + diff.right + " " + deltaText);
            }

            if (differences.size() > limit) {
                System.out.println("  ...and " + (differences.size() - limit) + " more differences.");
            }
        }
    }
}
