import org.opencv.core.Mat;

import java.util.Arrays;

/**
 * Thread de trabalho que processa uma "tira" de um frame de vídeo.
 * Contém a lógica de correção para Sal e Pimenta e para Borrões com Compensação de Movimento.
 * Renomeada para ImageProcessor2.
 */
public class ImageProcessor2 extends Thread {

    // Campos para todos os modos
    private final byte[][] processedFrame;
    private final int startY;
    private final int endY;
    private final String mode;

    // Campos para remoção de Sal e Pimenta
    private final byte[][] originalFramePadded; // Frame com margem
    private final int radius;

    // Campos para remoção de Borrões com Movimento
    private final byte[][] previousFrame;
    private final byte[][] originalFrame;
    private final byte[][] nextFrame;
    private final Mat flowBackward;
    private final Mat flowForward;


    /**
     * Construtor para o filtro de Sal e Pimenta.
     */
    public ImageProcessor2(byte[][] originalFramePadded, byte[][] processedFrame, int startY, int endY, int radius) {
        this.originalFramePadded = originalFramePadded;
        this.processedFrame = processedFrame;
        this.startY = startY;
        this.endY = endY;
        this.radius = radius;
        this.mode = "saltAndPepper";
        // Inicializa campos não utilizados como null
        this.previousFrame = null;
        this.originalFrame = null;
        this.nextFrame = null;
        this.flowBackward = null;
        this.flowForward = null;
    }

    /**
     * Construtor para o filtro de Borrão com Compensação de Movimento.
     */
    public ImageProcessor2(byte[][] previousFrame, byte[][] originalFrame, byte[][] nextFrame, byte[][] processedFrame,
                           int startY, int endY, Mat flowBackward, Mat flowForward) {
        this.previousFrame = previousFrame;
        this.originalFrame = originalFrame;
        this.nextFrame = nextFrame;
        this.processedFrame = processedFrame;
        this.startY = startY;
        this.endY = endY;
        this.flowBackward = flowBackward;
        this.flowForward = flowForward;
        this.mode = "motionBlur";
        // Inicializa campos não utilizados
        this.originalFramePadded = null;
        this.radius = 0;
    }

    @Override
    public void run() {
        if ("saltAndPepper".equals(mode)) {
            applySaltAndPepperFilter();
        } else if ("motionBlur".equals(mode)) {
            applyMotionAwareBlurFilter();
        }
    }

    /**
     * Obtém o valor de um pixel em coordenadas não inteiras usando interpolação bilinear.
     */
    private double getPixelInterpolado(byte[][] frame, double y, double x) {
        int height = frame.length;
        int width = frame[0].length;

        int x1 = (int) Math.floor(x);
        int y1 = (int) Math.floor(y);
        int x2 = x1 + 1;
        int y2 = y1 + 1;

        if (x1 < 0 || x2 >= width || y1 < 0 || y2 >= height) {
            int clampedX = Math.max(0, Math.min(width - 1, (int) x));
            int clampedY = Math.max(0, Math.min(height - 1, (int) y));
            return frame[clampedY][clampedX] & 0xFF;
        }

        double dx = x - x1;
        double dy = y - y1;

        double v11 = frame[y1][x1] & 0xFF;
        double v12 = frame[y2][x1] & 0xFF;
        double v21 = frame[y1][x2] & 0xFF;
        double v22 = frame[y2][x2] & 0xFF;

        double r1 = v11 * (1 - dx) + v21 * dx;
        double r2 = v12 * (1 - dx) + v22 * dx;

        return r1 * (1 - dy) + r2 * dy;
    }

    /**
     * Aplica o filtro de borrão usando os vetores de Fluxo Óptico para compensar o movimento.
     */
    private void applyMotionAwareBlurFilter() {
        int width = processedFrame[0].length;

        for (int y = startY; y < endY; y++) {
            for (int x = 0; x < width; x++) {
                double[] flowB = flowBackward.get(y, x);
                double[] flowF = flowForward.get(y, x);

                if (flowB == null || flowF == null) {
                    processedFrame[y][x] = originalFrame[y][x];
                    continue;
                }

                double prev_x = x + flowB[0];
                double prev_y = y + flowB[1];
                double next_x = x + flowF[0];
                double next_y = y + flowF[1];

                double v_anterior_compensado = getPixelInterpolado(previousFrame, prev_y, prev_x);
                double v_proximo_compensado = getPixelInterpolado(nextFrame, next_y, next_x);
                int v_atual = originalFrame[y][x] & 0xFF;

                if (Math.abs(v_anterior_compensado - v_proximo_compensado) < 30) {
                    double media_compensada = (v_anterior_compensado + v_proximo_compensado) / 2;
                    if (Math.abs(media_compensada - v_atual) > 35) {
                        processedFrame[y][x] = (byte) media_compensada;
                    } else {
                        processedFrame[y][x] = (byte) v_atual;
                    }
                } else {
                    processedFrame[y][x] = (byte) v_atual;
                }
            }
        }
    }

    /**
     * Aplica o filtro de Sal e Pimenta (mediana).
     */
    private void applySaltAndPepperFilter() {
        int width = processedFrame[0].length;
        int vizinhancaSize = (2 * radius + 1) * (2 * radius + 1);
        int[] vizinhos = new int[vizinhancaSize];

        for (int y = startY; y < endY; y++) {
            for (int x = 0; x < width; x++) {
                int k = 0;
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        vizinhos[k++] = originalFramePadded[y + radius + dy][x + radius + dx] & 0xFF;
                    }
                }
                Arrays.sort(vizinhos);
                processedFrame[y][x] = (byte) vizinhos[vizinhancaSize / 2];
            }
        }
    }
}