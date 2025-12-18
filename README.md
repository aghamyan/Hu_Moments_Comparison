### Hu Moment CSV Comparator

This repository contains two utilities:

1. `Hu_Moments` (ImageJ plug-in) for computing Hu moments.
2. `HuMomentCsvComparator` (command-line) for comparing two CSV exports that contain Hu moments or similar measurements.

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

#### Sample data

Two small sample files are included under `samples/`:

- `samples/reference.csv`
- `samples/variant.csv`

Example run:
```bash
java HuMomentCsvComparator samples/reference.csv samples/variant.csv 0.0
```
