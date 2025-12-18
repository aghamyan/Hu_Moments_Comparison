### Hu Moment CSV Comparator

This repository contains two utilities:

1. `Hu_Moments` (ImageJ plug-in) for computing Hu moments.
2. `HuMomentCsvComparator` (command-line) for comparing two CSV exports that contain Hu moments or similar measurements.
3. `HuMomentGuiComparator` (Swing GUI) for comparing one query CSV against multiple reference CSVs and reporting the closest match based on Hu1–Hu7 distances.

#### Build and run the comparator

1. Compile:
   ```bash
   javac HuMomentCsvComparator.java
   ```
2. Run with two CSV files and an optional tolerance (defaults to exact match):
   ```bash
   java HuMomentCsvComparator <first.csv> <second.csv> [tolerance]
   ```

The comparator treats the first column as the row identifier. It reports header mismatches, rows missing from either file, and per-column numeric differences that exceed the tolerance.

#### Build and run the GUI comparator

1. Compile:
   ```bash
   javac HuMomentGuiComparator.java
   ```
2. Run and open the Swing interface:
   ```bash
   java HuMomentGuiComparator
   ```

Within the GUI, pick a single query CSV file and one or more reference CSVs (multi-select in the file chooser is enabled). The tool compares rows by index, uses only Hu1–Hu7 columns, computes the average Euclidean distance for overlapping rows, and highlights the reference file with the minimum average distance.

#### Sample data

Two small sample files are included under `samples/`:

- `samples/reference.csv`
- `samples/variant.csv`

Example run:
```bash
java HuMomentCsvComparator samples/reference.csv samples/variant.csv 0.0
```
