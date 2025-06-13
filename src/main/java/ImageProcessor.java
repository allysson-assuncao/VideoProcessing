import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class ImageProcessor extends Thread {

    private final byte[][] originalFrame;
    private final byte[][] processedFrame;
    private final int startY;
    private final int endY;
    private final int radius;
    private final String mode;

    private final byte[][] previousFrame;
    private final byte[][] nextFrame;

    public ImageProcessor(byte[][] originalFrame, byte[][] processedFrame, int startY, int endY, int radius) {
        this.originalFrame = originalFrame;
        this.processedFrame = processedFrame;
        this.startY = startY;
        this.endY = endY;
        this.radius = radius;
        this.mode = "saltAndPepper";
        this.previousFrame = null;
        this.nextFrame = null;
    }

    public ImageProcessor(byte[][] previousFrame, byte[][] originalFrame, byte[][] nextFrame, byte[][] processedFrame, int startY, int endY) {
        this.originalFrame = originalFrame;
        this.processedFrame = processedFrame;
        this.startY = startY;
        this.endY = endY;
        this.previousFrame = previousFrame;
        this.nextFrame = nextFrame;
        this.mode = "blur";
        this.radius = 0;
    }


    @Override
    public void run() {
        if ("saltAndPepper".equals(mode)) {
            applySaltAndPepperFilter();
        } else if ("blur".equals(mode)) {
            applySmartBlurFilter();
        }
    }

    private void applySaltAndPepperFilter() {
        int width = processedFrame[0].length;
        for (int y = startY; y < endY; y++) {
            for (int x = 0; x < width; x++) {
                ArrayList<Integer> neighbors = new ArrayList<>();
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        int ny = y + radius + dy;
                        int nx = x + radius + dx;
                        neighbors.add(originalFrame[ny][nx] & 0xFF);
                    }
                }
                Collections.sort(neighbors);
                int median = neighbors.get(neighbors.size() / 2);
                processedFrame[y][x] = (byte) median;
            }
        }
    }

    private int getVizinhancaMediana(byte[][] frame) {
        int[] pixels = new int[9];
        int k = 0;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                pixels[k++] = frame[i][j] & 0xFF;
            }
        }
        Arrays.sort(pixels);
        return pixels[4];
    }

    private byte[][] getVizinhanca(byte[][] frame, int cy, int cx) {
        byte[][] vizinhanca = new byte[3][3];
        int height = frame.length;
        int width = frame[0].length;

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int ny = cy + dy;
                int nx = cx + dx;

                if (ny < 0) ny = 0;
                if (ny >= height) ny = height - 1;
                if (nx < 0) nx = 0;
                if (nx >= width) nx = width - 1;

                vizinhanca[dy + 1][dx + 1] = frame[ny][nx];
            }
        }
        return vizinhanca;
    }

    private void applySmartBlurFilter() {
        int width = processedFrame[0].length;
        for (int y = startY; y < endY; y++) {
            for (int x = 0; x < width; x++) {
                int v2 = originalFrame[y][x] & 0xFF;

                byte[][] vizinhancaAnterior = getVizinhanca(previousFrame, y, x);
                byte[][] vizinhancaProximo = getVizinhanca(nextFrame, y, x);

                int medianaAnterior = getVizinhancaMediana(vizinhancaAnterior);
                int medianaProximo = getVizinhancaMediana(vizinhancaProximo);

                if (Math.abs(medianaAnterior - medianaProximo) < 22) {
                    int mediaDasMedianas = (medianaAnterior + medianaProximo) / 2;

                    if (Math.abs(mediaDasMedianas - v2) > 15) {
                        processedFrame[y][x] = (byte) mediaDasMedianas;
                    } else {
                        processedFrame[y][x] = (byte) v2;
                    }
                } else {
                    processedFrame[y][x] = (byte) v2;
                }
            }
        }
    }
}
