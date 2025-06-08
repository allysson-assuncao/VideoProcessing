import java.util.ArrayList;
import java.util.Collections;

class ImageProcessor2 extends Thread {
    // Enum para definir qual tarefa a thread deve executar
    public enum Task {
        TEMPORAL_BLUR,
        SALT_PEPPER
    }

    private final byte[][] sourceTileWithPadding;
    private final int tileOriginalY;
    private final int tileOriginalHeight;
    private final int frameIndex;

    private final byte[][][] originalFrames;
    private final byte[][] outputFrame;

    private final int padding;
    private final Task task;
    private final int raioSalPimenta;

    public ImageProcessor2(
            byte[][] sourceTileWithPadding, int tileOriginalY, int tileOriginalHeight, int frameIndex,
            byte[][][] originalFrames, byte[][] outputFrame, int padding,
            int raioSalPimenta, Task task) {
        this.sourceTileWithPadding = sourceTileWithPadding;
        this.tileOriginalY = tileOriginalY;
        this.tileOriginalHeight = tileOriginalHeight;
        this.frameIndex = frameIndex;
        this.originalFrames = originalFrames;
        this.outputFrame = outputFrame;
        this.padding = padding;
        this.raioSalPimenta = raioSalPimenta;
        this.task = task;
    }

    @Override
    public void run() {
        switch (task) {
            case TEMPORAL_BLUR:
                applyTemporalBlurOnTile();
                break;
            case SALT_PEPPER:
                applySaltAndPepperOnTile();
                break;
        }
    }

    private int getNeighborhoodAverage(byte[][] frame, int centerY, int centerX) {
        int sum = 0;
        int count = 0;
        int frameHeight = frame.length;
        int frameWidth = frame[0].length;

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int ny = centerY + dy;
                int nx = centerX + dx;

                // Garante que o vizinho está dentro dos limites do frame
                if (ny >= 0 && ny < frameHeight && nx >= 0 && nx < frameWidth) {
                    sum += frame[ny][nx] & 0xFF;
                    count++;
                }
            }
        }
        return (count == 0) ? 0 : sum / count;
    }

    private void applyTemporalBlurOnTile() {
        int paddedWidth = sourceTileWithPadding[0].length;

        byte[][] anterior = originalFrames[frameIndex - 1];
        byte[][] proximo = originalFrames[frameIndex + 2];

        // Itera sobre os pixels da tira original
        for (int y = padding; y < tileOriginalHeight + padding; y++) {
            for (int x = padding; x < paddedWidth - padding; x++) {
                int fullFrameY = tileOriginalY + (y - padding);
                int fullFrameX = x - padding;

                // 1. Calcula a média da vizinhança nos frames de referência
                int avg_v1 = getNeighborhoodAverage(anterior, fullFrameY, fullFrameX);
                int avg_v3 = getNeighborhoodAverage(proximo, fullFrameY, fullFrameX);

                // 2. Pega o valor do pixel central do frame atual
                int v2_center = sourceTileWithPadding[y][x] & 0xFF;

                // 3. Lógica de decisão aprimorada
                int mediaRegional = (avg_v1 + avg_v3) / 2;
                byte finalPixel;

                // Condição 1: A região 3x3 é estável entre os frames de referência?
                // Condição 2: O pixel central atual é um "outlier" em relação à sua região?
                // Ajustamos os limiares, pois médias são menos voláteis que pixels únicos.
                if (Math.abs(avg_v1 - avg_v3) < 35 && Math.abs(mediaRegional - v2_center) > 25) {
                    // Se sim, corrige o pixel usando a média da região temporal
                    finalPixel = (byte) mediaRegional;
                } else {
                    // Senão, mantém o pixel original
                    finalPixel = (byte) v2_center;
                }

                outputFrame[fullFrameY][fullFrameX] = finalPixel;
            }
        }
    }

    private void applySaltAndPepperOnTile() {
        // As dimensões do tile COM padding
        int paddedWidth = sourceTileWithPadding[0].length;

        ArrayList<Integer> vizinhos = new ArrayList<>();

        // Itera apenas sobre os pixels que correspondem à tira original
        for (int y = padding; y < tileOriginalHeight + padding; y++) {
            for (int x = padding; x < paddedWidth - padding; x++) {
                vizinhos.clear();
                // Acessa a vizinhança dentro do tile com padding, sem precisar de verificações de limite
                for (int dy = -raioSalPimenta; dy <= raioSalPimenta; dy++) {
                    for (int dx = -raioSalPimenta; dx <= raioSalPimenta; dx++) {
                        vizinhos.add(sourceTileWithPadding[y + dy][x + dx] & 0xFF);
                    }
                }
                Collections.sort(vizinhos);
                byte medianPixel = (byte) (int) vizinhos.get(vizinhos.size() / 2);

                // Coordenadas correspondentes no frame completo
                int fullFrameY = tileOriginalY + (y - padding);
                int fullFrameX = x - padding;

                // Escreve o resultado na matriz de saída
                outputFrame[fullFrameY][fullFrameX] = medianPixel;
            }
        }
    }
}
