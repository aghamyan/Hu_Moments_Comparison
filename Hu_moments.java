import ij.*;
import ij.plugin.filter.PlugInFilter;
import ij.process.*;
import ij.measure.ResultsTable;
import ij.gui.Roi;

public class Hu_Moments implements PlugInFilter {

    ImagePlus imp;

    public int setup(String arg, ImagePlus imp) {
        this.imp = imp;
        return DOES_8G + ROI_REQUIRED;
    }

    public void run(ImageProcessor ip) {

        int width = ip.getWidth();
        int height = ip.getHeight();

        double m00 = 0, m10 = 0, m01 = 0;
        double m11 = 0, m20 = 0, m02 = 0;
        double m30 = 0, m03 = 0, m12 = 0, m21 = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int val = ip.getPixel(x, y);
                if (val > 0) {
                    m00 += 1;
                    m10 += x;
                    m01 += y;
                    m11 += x * y;
                    m20 += x * x;
                    m02 += y * y;
                    m30 += x * x * x;
                    m03 += y * y * y;
                    m12 += x * y * y;
                    m21 += x * x * y;
                }
            }
        }

        if (m00 == 0) return;

        double xBar = m10 / m00;
        double yBar = m01 / m00;

        double mu11 = m11 - xBar * m01;
        double mu20 = m20 - xBar * m10;
        double mu02 = m02 - yBar * m01;
        double mu30 = m30 - 3 * xBar * m20 + 2 * xBar * xBar * m10;
        double mu03 = m03 - 3 * yBar * m02 + 2 * yBar * yBar * m01;
        double mu12 = m12 - 2 * yBar * m11 - xBar * m02 + 2 * yBar * yBar * m10;
        double mu21 = m21 - 2 * xBar * m11 - yBar * m20 + 2 * xBar * xBar * m01;

        double n20 = mu20 / Math.pow(m00, 2);
        double n02 = mu02 / Math.pow(m00, 2);
        double n11 = mu11 / Math.pow(m00, 2);
        double n30 = mu30 / Math.pow(m00, 2.5);
        double n03 = mu03 / Math.pow(m00, 2.5);
        double n12 = mu12 / Math.pow(m00, 2.5);
        double n21 = mu21 / Math.pow(m00, 2.5);

        double hu1 = n20 + n02;
        double hu2 = Math.pow(n20 - n02, 2) + 4 * Math.pow(n11, 2);
        double hu3 = Math.pow(n30 - 3 * n12, 2) + Math.pow(3 * n21 - n03, 2);
        double hu4 = Math.pow(n30 + n12, 2) + Math.pow(n21 + n03, 2);
        double hu5 = (n30 - 3 * n12) * (n30 + n12) *
                     (Math.pow(n30 + n12, 2) - 3 * Math.pow(n21 + n03, 2)) +
                     (3 * n21 - n03) * (n21 + n03) *
                     (3 * Math.pow(n30 + n12, 2) - Math.pow(n21 + n03, 2));
        double hu6 = (n20 - n02) *
                     (Math.pow(n30 + n12, 2) - Math.pow(n21 + n03, 2)) +
                     4 * n11 * (n30 + n12) * (n21 + n03);
        double hu7 = (3 * n21 - n03) * (n30 + n12) *
                     (Math.pow(n30 + n12, 2) - 3 * Math.pow(n21 + n03, 2)) -
                     (n30 - 3 * n12) * (n21 + n03) *
                     (3 * Math.pow(n30 + n12, 2) - Math.pow(n21 + n03, 2));

        ResultsTable rt = ResultsTable.getResultsTable();
        rt.incrementCounter();
        rt.addValue("Hu1", hu1);
        rt.addValue("Hu2", hu2);
        rt.addValue("Hu3", hu3);
        rt.addValue("Hu4", hu4);
        rt.addValue("Hu5", hu5);
        rt.addValue("Hu6", hu6);
        rt.addValue("Hu7", hu7);
        rt.show("Results");
    }
}