import java.util.ArrayList;
import java.util.Collections;

class ImageProcessor2 extends Thread {
    public enum Task {
        TEMPORAL_BLUR,
        SALT_PEPPER
    }

    // Atributos para a tarefa de Sal e Pimenta
    private final byte[][] paddedFrameForSP;

    // Atributos para a tarefa de Borrão Temporal
    private final byte[][] paddedFrameAnterior;
    private final byte[][] paddedFrameAtual;
    private final byte[][] paddedFrameProximo;

    // Atributos comuns
    private final byte[][] outputFrame;
    private final int startY;
    private final int sliceHeight;
    private final int padding;
    private final Task task;
    private final int raioSalPimenta;

    // Construtor unificado para ambas as tarefas
    public ImageProcessor2(Task task, byte[][] paddedFrameForSP, byte[][] paddedFrameAnterior,
                           byte[][] paddedFrameAtual, byte[][] paddedFrameProximo,
                           byte[][] outputFrame, int startY, int sliceHeight, int padding, int raioSalPimenta) {
        this.task = task;
        this.paddedFrameForSP = paddedFrameForSP;
        this.paddedFrameAnterior = paddedFrameAnterior;
        this.paddedFrameAtual = paddedFrameAtual;
        this.paddedFrameProximo = paddedFrameProximo;
        this.outputFrame = outputFrame;
        this.startY = startY;
        this.sliceHeight = sliceHeight;
        this.padding = padding;
        this.raioSalPimenta = raioSalPimenta;
    }


    @Override
    public void run() {
        switch (task) {
            case TEMPORAL_BLUR:
                applyAdvancedTemporalBlurOnSlice();
                break;
            case SALT_PEPPER:
                applySaltAndPepperOnSlice();
                break;
        }
    }

    /**
     * SIMPLIFICADO: Agora opera em um frame já com margem, sem precisar de verificações de limite.
     */
    private double[] getNeighborhoodStats(byte[][] paddedFrame, int paddedCenterY, int paddedCenterX) {
        ArrayList<Integer> values = new ArrayList<>(9);
        double sum = 0;

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int val = paddedFrame[paddedCenterY + dy][paddedCenterX + dx] & 0xFF;
                values.add(val);
                sum += val;
            }
        }

        double mean = sum / 9.0;
        double stdDev = 0;
        for (int val : values) {
            stdDev += Math.pow(val - mean, 2);
        }
        stdDev = Math.sqrt(stdDev / 9.0);

        return new double[]{mean, stdDev};
    }

    /**
     * REFATORADO: Opera sobre uma "fatia" do frame com margem.
     */
    private void applyAdvancedTemporalBlurOnSlice() {
        final int endY = startY + sliceHeight;
        final int paddedWidth = paddedFrameAtual[0].length;
        final double SENSITIVITY = 1.2;
        final double TEXTURE_DAMPENING = 0.5;

        for (int y = startY; y < endY; y++) {
            for (int x = 0; x < paddedWidth - (2 * padding); x++) {
                int paddedY = y + padding;
                int paddedX = x + padding;

                double[] statsAnterior = getNeighborhoodStats(paddedFrameAnterior, paddedY, paddedX);
                double[] statsProximo = getNeighborhoodStats(paddedFrameProximo, paddedY, paddedX);
                double meanAnterior = statsAnterior[0];
                double meanProximo = statsProximo[0];

                if (Math.abs(meanAnterior - meanProximo) < 30) {
                    double expectedValue = (meanAnterior + meanProximo) / 2.0;
                    int currentValue = paddedFrameAtual[paddedY][paddedX] & 0xFF;

                    double[] statsAtual = getNeighborhoodStats(paddedFrameAtual, paddedY, paddedX);
                    double localStdDev = statsAtual[1];
                    double difference = Math.abs(currentValue - expectedValue);
                    double dynamicThreshold = 15 + localStdDev * TEXTURE_DAMPENING;

                    if (difference > dynamicThreshold) {
                        double alpha = Math.min(1.0, (difference - dynamicThreshold) / (40.0 * SENSITIVITY));
                        int blendedValue = (int) Math.round((1.0 - alpha) * currentValue + alpha * expectedValue);
                        outputFrame[y][x] = (byte) blendedValue;
                    } else {
                        outputFrame[y][x] = (byte) currentValue;
                    }
                } else {
                    outputFrame[y][x] = paddedFrameAtual[paddedY][paddedX];
                }
            }
        }
    }

    /**
     * REFATORADO: Opera sobre uma "fatia" do frame com margem.
     */
    private void applySaltAndPepperOnSlice() {
        final int endY = startY + sliceHeight;
        final int paddedWidth = paddedFrameForSP[0].length;
        ArrayList<Integer> vizinhos = new ArrayList<>(raioSalPimenta * 2 + 1);

        for (int y = startY; y < endY; y++) {
            for (int x = 0; x < paddedWidth - (2 * padding); x++) {
                int paddedY = y + padding;
                int paddedX = x + padding;

                vizinhos.clear();
                for (int dy = -raioSalPimenta; dy <= raioSalPimenta; dy++) {
                    for (int dx = -raioSalPimenta; dx <= raioSalPimenta; dx++) {
                        vizinhos.add(paddedFrameForSP[paddedY + dy][paddedX + dx] & 0xFF);
                    }
                }
                Collections.sort(vizinhos);
                outputFrame[y][x] = (byte) (int) vizinhos.get(vizinhos.size() / 2);
            }
        }
    }
}
