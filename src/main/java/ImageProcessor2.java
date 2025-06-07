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

    private void applyTemporalBlurOnTile() {
        // As dimensões do tile COM padding
        int paddedWidth = sourceTileWithPadding[0].length;

        // Frames de referência para o borrão temporal
        byte[][] anterior = originalFrames[frameIndex - 1];
        byte[][] proximo = originalFrames[frameIndex + 2];

        // Itera apenas sobre os pixels que correspondem à tira original, usando o padding para vizinhança
        for (int y = padding; y < tileOriginalHeight + padding; y++) {
            for (int x = padding; x < paddedWidth - padding; x++) {
                // Coordenadas correspondentes no frame completo
                int fullFrameY = tileOriginalY + (y - padding);
                int fullFrameX = x - padding;

                int v1 = anterior[fullFrameY][fullFrameX] & 0xFF;
                int v2 = sourceTileWithPadding[y][x] & 0xFF; // Pixel original do frame atual (está no tile)
                int v3 = proximo[fullFrameY][fullFrameX] & 0xFF;

                int media = (v1 + v3) / 2;
                byte finalPixel;
                if (Math.abs(v1 - v3) < 50 && Math.abs(media - v2) > 40) {
                    finalPixel = (byte) media;
                } else {
                    finalPixel = (byte) v2;
                }
                // Escreve o resultado na matriz de saída na posição correta
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
