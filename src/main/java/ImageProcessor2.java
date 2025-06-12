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

    /*private void applyTemporalBlurOnTile() {
        int paddedWidth = sourceTileWithPadding[0].length;

        byte[][] anterior = originalFrames[frameIndex - 1];
        byte[][] proximo = originalFrames[frameIndex + 2];

        // Itera sobre os pixels da tira original
        for (int y = padding; y < tileOriginalHeight + padding; y++) {
            for (int x = padding; x < paddedWidth - padding; x++) {
                int fullFrameY = tileOriginalY + (y - padding);
                int fullFrameX = x - padding;

                // 1. Pega o valor do pixel central nos frames de referência e atual
                int v1 = anterior[fullFrameY][fullFrameX] & 0xFF;
                int v2 = sourceTileWithPadding[y][x] & 0xFF;
                int v3 = proximo[fullFrameY][fullFrameX] & 0xFF;

                // 2. Lógica de decisão (ajuste os limiares conforme necessário)
                int media = (v1 + v3) / 2;
                byte finalPixel;

                // Condição: A diferença entre v1 e v3 é pequena e v2 é um outlier?
                if (Math.abs(v1 - v3) < 35 && Math.abs(media - v2) > 25) {
                    finalPixel = (byte) media;
                } else {
                    finalPixel = (byte) v2;
                }

                outputFrame[fullFrameY][fullFrameX] = finalPixel;
            }
        }
    }*/

    /*private double[] getNeighborhoodStats(byte[][] frame, int centerY, int centerX) {
        int frameHeight = frame.length;
        int frameWidth = frame[0].length;
        ArrayList<Integer> values = new ArrayList<>();
        double sum = 0;

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int ny = centerY + dy;
                int nx = centerX + dx;
                if (ny >= 0 && ny < frameHeight && nx >= 0 && nx < frameWidth) {
                    int val = frame[ny][nx] & 0xFF;
                    values.add(val);
                    sum += val;
                }
            }
        }

        if (values.isEmpty()) {
            return new double[]{0, 0};
        }

        double mean = sum / values.size();
        double stdDev = 0;
        for (int val : values) {
            stdDev += Math.pow(val - mean, 2);
        }
        stdDev = Math.sqrt(stdDev / values.size());

        return new double[]{mean, stdDev};
    }

    *//**
     * MÉTODO DE CORREÇÃO DE BLUR TOTALMENTE REFEITO
     * Usa limiares adaptativos e mesclagem suave (alpha blending) para um resultado superior.
     *//*
    private void applyTemporalBlurOnTile() {
        int paddedWidth = sourceTileWithPadding[0].length;

        byte[][] anterior = originalFrames[frameIndex - 1];
        byte[][] proximo = originalFrames[frameIndex + 2];

        // Parâmetros da nova lógica
        final double SENSITIVITY = 1.2; // Quão sensível o detector é. Maior = mais sensível.
        final double TEXTURE_DAMPENING = 0.5; // Quão fortemente a textura local reduz a correção.

        for (int y = padding; y < tileOriginalHeight + padding; y++) {
            for (int x = padding; x < paddedWidth - padding; x++) {
                int fullFrameY = tileOriginalY + (y - padding);
                int fullFrameX = x - padding;

                // 1. Analisa os frames de referência (como antes, mas usando a média)
                double[] statsAnterior = getNeighborhoodStats(anterior, fullFrameY, fullFrameX);
                double[] statsProximo = getNeighborhoodStats(proximo, fullFrameY, fullFrameX);
                double meanAnterior = statsAnterior[0];
                double meanProximo = statsProximo[0];

                // Só continua se a região temporal for relativamente estável
                if (Math.abs(meanAnterior - meanProximo) < 30) {
                    double expectedValue = (meanAnterior + meanProximo) / 2.0;
                    int currentValue = sourceTileWithPadding[y][x] & 0xFF;

                    // 2. Analisa a textura do frame ATUAL para criar um limiar dinâmico
                    double[] statsAtual = getNeighborhoodStats(sourceTileWithPadding, y, x); // Usa o tile com padding, que é mais rápido
                    double localStdDev = statsAtual[1]; // Desvio padrão local = indicador de textura

                    double difference = Math.abs(currentValue - expectedValue);

                    // O limiar aumenta em áreas com mais textura, para preservar detalhes
                    double dynamicThreshold = 15 + localStdDev * TEXTURE_DAMPENING;

                    // 3. Aplica correção com mesclagem suave (Alpha Blending)
                    if (difference > dynamicThreshold) {
                        // Calcula a força da correção (alpha)
                        // Se a diferença for muito maior que o limiar, alpha se aproxima de 1.0
                        // Se for pouco maior, alpha é pequeno, resultando em uma correção sutil.
                        double alpha = Math.min(1.0, (difference - dynamicThreshold) / (40.0 * SENSITIVITY));

                        // Mescla o valor original com o valor esperado
                        int blendedValue = (int) Math.round((1.0 - alpha) * currentValue + alpha * expectedValue);
                        outputFrame[fullFrameY][fullFrameX] = (byte) blendedValue;
                    } else {
                        // Nenhuma correção necessária
                        outputFrame[fullFrameY][fullFrameX] = (byte) currentValue;
                    }
                } else {
                    // Região temporal instável, não faz nada para evitar criar novos artefatos
                    outputFrame[fullFrameY][fullFrameX] = sourceTileWithPadding[y][x];
                }
            }
        }
    }*/

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
