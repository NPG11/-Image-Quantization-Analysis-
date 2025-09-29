import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.*;

public class ImageDisplay {

    JFrame frame;
    JLabel lbIm1, lbIm2;
    BufferedImage imgOne, imgTwo;

    int width = 352;
    int height = 288;


    private void readImageRGB(int width, int height, String imgPath, BufferedImage img) {
        try {
            int frameLength = width * height * 3;
            RandomAccessFile raf = new RandomAccessFile(new File(imgPath), "r");
            byte[] bytes = new byte[frameLength];
            raf.read(bytes);
            raf.close();

            int ind = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int r = bytes[ind] & 0xff;
                    int g = bytes[ind + height * width] & 0xff;
                    int b = bytes[ind + height * width * 2] & 0xff;
                    int pix = 0xff000000 | (r << 16) | (g << 8) | b;
                    img.setRGB(x, y, pix);
                    ind++;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

  
    private int uniformQuantize(int v, int bits) {
        if (bits >= 8) return v;
        if (bits <= 0) bits = 1;
        int levels = 1 << bits;
        int step = 256 / levels;
        int idx = v / step;
        int center = idx * step + step / 2;
        return Math.min(255, center);
    }

    private BufferedImage quantizeRGB_uniform(BufferedImage src, int q1, int q2, int q3) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;

                int rq = uniformQuantize(r, q1);
                int gq = uniformQuantize(g, q2);
                int bq = uniformQuantize(b, q3);

                int pix = 0xff000000 | (rq << 16) | (gq << 8) | bq;
                out.setRGB(x, y, pix);
            }
        }
        return out;
    }


    private BufferedImage quantizeYUV_uniform(BufferedImage src, int q1, int q2, int q3) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        for (int yPixel = 0; yPixel < h; yPixel++) {
            for (int xPixel = 0; xPixel < w; xPixel++) {
                int rgb = src.getRGB(xPixel, yPixel);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;

                double[] yuv = rgbToYuv(r, g, b);
                double yVal = yuv[0];
                double uVal = yuv[1];
                double vVal = yuv[2];

                double yQ = uniformQuantize((int)Math.round(yVal), q1);
                double uQ = uniformQuantize((int)Math.round(uVal + 128), q2) - 128;
                double vQ = uniformQuantize((int)Math.round(vVal + 128), q3) - 128;

                int[] rgbFinal = yuvToRgb(yQ, uQ, vQ);
                int newPixel = (0xff << 24) | (rgbFinal[0] << 16) | (rgbFinal[1] << 8) | rgbFinal[2];
                out.setRGB(xPixel, yPixel, newPixel);
            }
        }
        return out;
    }


    private int[] computeSmartBins(List<Integer> values, int bits){
        if (bits >= 8) return null;
        if (bits <= 0) bits = 1;
        int levels = 1 << bits;
        Collections.sort(values);
        int n = values.size();
        int binSize = n / levels;

        int[] reps = new int[levels];
        for (int i = 0; i < levels; i++) {
            int start = i * binSize;
            int end = (i == levels - 1) ? n : (i + 1) * binSize;
            int sum = 0;
            for (int j = start; j < end; j++) sum += values.get(j);
            reps[i] = sum / (end - start);
        }
        return reps;
    }

    private BufferedImage quantizeRGB_smart(BufferedImage src, int q1, int q2, int q3) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        List<Integer> rVals = new ArrayList<>(), gVals = new ArrayList<>(), bVals = new ArrayList<>();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                rVals.add((rgb >> 16) & 0xff);
                gVals.add((rgb >> 8) & 0xff);
                bVals.add(rgb & 0xff);
            }

        int[] rBins = computeSmartBins(rVals, q1);
        int[] gBins = computeSmartBins(gVals, q2);
        int[] bBins = computeSmartBins(bVals, q3);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;

                int rq = (rBins == null) ? r : rBins[r * (1 << q1) / 256];
                int gq = (gBins == null) ? g : gBins[g * (1 << q2) / 256];
                int bq = (bBins == null) ? b : bBins[b * (1 << q3) / 256];

                int pix = 0xff000000 | (rq << 16) | (gq << 8) | bq;
                out.setRGB(x, y, pix);
            }
        }
        return out;
    }

    private BufferedImage quantizeYUV_smart(BufferedImage src, int q1, int q2, int q3) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        List<Integer> yVals = new ArrayList<>(), uVals = new ArrayList<>(), vVals = new ArrayList<>();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                double[] yuv = rgbToYuv(r, g, b);
                yVals.add((int)yuv[0]);
                uVals.add((int)(yuv[1] + 128));
                vVals.add((int)(yuv[2] + 128));
            }

        int[] yBins = computeSmartBins(yVals, q1);
        int[] uBins = computeSmartBins(uVals, q2);
        int[] vBins = computeSmartBins(vVals, q3);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                double[] yuv = rgbToYuv(r, g, b);

                int yQ = (yBins == null) ? (int)yuv[0] : yBins[(int)yuv[0] * (1 << q1) / 256];
                int uQ = (uBins == null) ? (int)(yuv[1] + 128) : uBins[(int)(yuv[1] + 128) * (1 << q2) / 256] - 128;
                int vQ = (vBins == null) ? (int)(yuv[2] + 128) : vBins[(int)(yuv[2] + 128) * (1 << q3) / 256] - 128;

                int[] rgbFinal = yuvToRgb(yQ, uQ, vQ);
                int newPixel = (0xff << 24) | (rgbFinal[0] << 16) | (rgbFinal[1] << 8) | rgbFinal[2];
                out.setRGB(x, y, newPixel);
            }
        }
        return out;
    }


    public double[] rgbToYuv(int r, int g, int b) {
        double y = 0.299 * r + 0.587 * g + 0.114 * b;
        double u = -0.147 * r - 0.289 * g + 0.436 * b;
        double v = 0.615 * r - 0.515 * g - 0.100 * b;
        return new double[]{y, u, v};
    }

    public int[] yuvToRgb(double y, double u, double v) {
        int r = (int)Math.round(y + 1.1398 * v);
        int g = (int)Math.round(y - 0.3946 * u - 0.5806 * v);
        int b = (int)Math.round(y + 2.0321 * u);

        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));

        return new int[]{r, g, b};
    }

 
    private double calculateMSE(BufferedImage a, BufferedImage b) {
        int w = a.getWidth(), h = a.getHeight();
        long sum = 0L;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int A = a.getRGB(x, y);
                int B = b.getRGB(x, y);

                int ar = (A >> 16) & 0xff;
                int ag = (A >> 8) & 0xff;
                int ab = A & 0xff;

                int br = (B >> 16) & 0xff;
                int bg = (B >> 8) & 0xff;
                int bb = B & 0xff;

                sum += (ar - br) * (ar - br) +
                       (ag - bg) * (ag - bg) +
                       (ab - bb) * (ab - bb);
            }
        }
        return (double) sum / (w * h * 3); 
    }


    private void runBatch(String imagePath, int C, int M, int N) {
        // Load original image once
        imgOne = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        readImageRGB(width, height, imagePath, imgOne);

        System.out.println("N,C,M,Q1,Q2,Q3,MSE");
        for (int q1 = 1; q1 <= 8; q1++) {
            for (int q2 = 1; q2 <= 8; q2++) {
                int q3 = N - q1 - q2;
                if (q3 < 1 || q3 > 8) continue;

                BufferedImage out;
                if (M == 1 && C == 1) out = quantizeRGB_uniform(imgOne, q1, q2, q3);
                else if (M == 1 && C == 2) out = quantizeYUV_uniform(imgOne, q1, q2, q3);
                else if (M == 2 && C == 1) out = quantizeRGB_smart(imgOne, q1, q2, q3);
                else out = quantizeYUV_smart(imgOne, q1, q2, q3);

                double mse = calculateMSE(imgOne, out);
                System.out.printf("%d,%d,%d,%d,%d,%d,%.2f%n", N, C, M, q1, q2, q3, mse);
            }
        }
    }

    public void showIms(String[] args) {
        String imagePath = args[0];
        int C = Integer.parseInt(args[1]);
        int M = Integer.parseInt(args[2]);
        int Q1 = Integer.parseInt(args[3]);
        int Q2 = Integer.parseInt(args[4]);
        int Q3 = Integer.parseInt(args[5]);

        imgOne = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        readImageRGB(width, height, args[0], imgOne);

        if (M == 1) {
            if (C == 1) imgTwo = quantizeRGB_uniform(imgOne, Q1, Q2, Q3);
            else if (C == 2) imgTwo = quantizeYUV_uniform(imgOne, Q1, Q2, Q3);
        } else if (M == 2) {
            if (C == 1) imgTwo = quantizeRGB_smart(imgOne, Q1, Q2, Q3);
            else if (C == 2) imgTwo = quantizeYUV_smart(imgOne, Q1, Q2, Q3);
        }

        frame = new JFrame("Original (left) vs Quantized (right)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0; c.fill = GridBagConstraints.NONE;

        lbIm1 = new JLabel(new ImageIcon(imgOne));
        c.gridx = 0;
        frame.getContentPane().add(lbIm1, c);

        lbIm2 = new JLabel(new ImageIcon(imgTwo));
        c.gridx = 1;
        frame.getContentPane().add(lbIm2, c);

        frame.pack();
        frame.setVisible(true);

        double mse = calculateMSE(imgOne, imgTwo);
        int N = Q1 + Q2 + Q3;
        System.out.printf("N=%d C=%d M=%d Q=[%d,%d,%d] MSE=%.2f%n",
                N, C, M, Q1, Q2, Q3, mse);
    }


    public static void main(String[] args) {
        ImageDisplay app = new ImageDisplay();

        if (args.length == 5 && args[0].equals("--batch")) {
            String image = args[1];
            int C = Integer.parseInt(args[2]);
            int M = Integer.parseInt(args[3]);
            int N = Integer.parseInt(args[4]);
            app.runBatch(image, C, M, N);
            return;
        }

        if (args.length != 6) {
            System.out.println("Usage (GUI): java ImageDisplay <imagePath> <C> <M> <Q1> <Q2> <Q3>");
            System.out.println("Usage (Batch): java ImageDisplay --batch <imagePath> <C> <M> <N>");
            return;
        }

        app.showIms(args);
    }
}
