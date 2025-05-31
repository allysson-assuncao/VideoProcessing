import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ImageProcessor extends Thread {
    private final int startFrame;
    private final int endFrame;
    private final byte[][][] originalFrames; // Para leitura dos pixels originais (vizinhos, temporais)
    private final byte[][][] processedFrames; // Para escrita dos pixels corrigidos

    // --- Parâmetros para ajuste da correção ---
    // Filtro de Mediana (Salt & Pepper)
    private final int medianWindowSize = 3; // Janela 3x3. Pode ser 5 para 5x5, etc. (ímpar)

    // Detecção de Borrões (Frames únicos)
    private final int blobDarkThreshold = 30; // Abaixo deste valor é candidato a borrão escuro
    private final int blobLightThreshold = 225; // Acima deste valor é candidato a borrão claro
    // Diferença mínima para considerar um pixel como parte de um borrão temporal
    private final int temporalDifferenceThreshold = 60;
    // Diferença máxima entre pixels de frames adjacentes para serem considerados 'estáveis'
    private final int temporalStabilityThreshold = 30;
    // --- Fim dos Parâmetros ---


    public ImageProcessor(int startFrame, int endFrame, byte[][][] originalFrames, byte[][][] processedFrames) {
        this.startFrame = startFrame;
        this.endFrame = endFrame; // O índice final é exclusivo
        this.originalFrames = originalFrames;
        this.processedFrames = processedFrames;
    }

    @Override
    public void run() {
        if (originalFrames.length == 0) return;
        int numTotalFrames = originalFrames.length;
        int height = originalFrames[0].length;
        int width = originalFrames[0][0].length;

        // Etapa 1: Aplicar filtro de mediana para ruído Salt & Pepper
        // Lê de originalFrames e escreve em processedFrames
        for (int f = startFrame; f < endFrame; f++) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    processedFrames[f][y][x] = calculateMedian(f, y, x, height, width);
                }
            }
        }

        // Etapa 2: Remover borrões baseados em inconsistência temporal
        // Compara originalFrames[f] com originalFrames[f-1] e originalFrames[f+1]
        // Se um borrão for detectado em originalFrames[f], corrige em processedFrames[f]
        for (int f = startFrame; f < endFrame; f++) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    // Usamos o valor do pixel original para detectar o borrão
                    byte originalPixelByte = originalFrames[f][y][x];
                    int originalPixelValue = originalPixelByte & 0xFF;

                    boolean isDarkBlobCandidate = originalPixelValue < blobDarkThreshold;
                    boolean isLightBlobCandidate = originalPixelValue > blobLightThreshold;

                    if (isDarkBlobCandidate || isLightBlobCandidate) {
                        byte prevFramePixelValue = -1; // Valor sentinela
                        byte nextFramePixelValue = -1; // Valor sentinela
                        boolean prevFrameExists = f > 0;
                        boolean nextFrameExists = f < numTotalFrames - 1;

                        int valPrev = -1, valNext = -1;

                        if (prevFrameExists) {
                            prevFramePixelValue = originalFrames[f - 1][y][x];
                            valPrev = prevFramePixelValue & 0xFF;
                        }
                        if (nextFrameExists) {
                            nextFramePixelValue = originalFrames[f + 1][y][x];
                            valNext = nextFramePixelValue & 0xFF;
                        }

                        // Lógica de correção do borrão:
                        // Se o pixel atual é muito diferente dos vizinhos temporais,
                        // e os vizinhos temporais são semelhantes entre si (estáveis).
                        if (prevFrameExists && nextFrameExists) {
                            int diffCurrPrev = Math.abs(originalPixelValue - valPrev);
                            int diffCurrNext = Math.abs(originalPixelValue - valNext);
                            int diffPrevNext = Math.abs(valPrev - valNext);

                            if (diffCurrPrev > temporalDifferenceThreshold &&
                                diffCurrNext > temporalDifferenceThreshold &&
                                diffPrevNext < temporalStabilityThreshold) {
                                // Borrão detectado, e frames adjacentes são estáveis e diferentes do atual.
                                // Usar a média dos pixels dos frames adjacentes.
                                processedFrames[f][y][x] = (byte) ((valPrev + valNext) / 2);
                            }
                        } else if (prevFrameExists) { // Último frame do segmento ou vídeo, só tem anterior
                            int diffCurrPrev = Math.abs(originalPixelValue - valPrev);
                            if (diffCurrPrev > temporalDifferenceThreshold) {
                                processedFrames[f][y][x] = prevFramePixelValue;
                            }
                        } else if (nextFrameExists) { // Primeiro frame, só tem posterior
                            int diffCurrNext = Math.abs(originalPixelValue - valNext);
                            if (diffCurrNext > temporalDifferenceThreshold) {
                                processedFrames[f][y][x] = nextFramePixelValue;
                            }
                        }
                        // Se não for um borrão claro (temporalmente inconsistente),
                        // o valor do filtro de mediana permanece.
                    }
                }
            }
        }
    }

    private byte calculateMedian(int frameIndex, int y, int x, int height, int width) {
        List<Byte> neighbors = new ArrayList<>();
        int halfWindow = medianWindowSize / 2; // Para uma janela 3x3, halfWindow = 1

        for (int i = -halfWindow; i <= halfWindow; i++) {
            for (int j = -halfWindow; j <= halfWindow; j++) {
                int currentY = y + i;
                int currentX = x + j;

                // Verifica se o vizinho está dentro dos limites do frame
                if (currentY >= 0 && currentY < height && currentX >= 0 && currentX < width) {
                    // Adiciona o pixel do frame ORIGINAL para o cálculo da mediana
                    neighbors.add(originalFrames[frameIndex][currentY][currentX]);
                }
            }
        }

        if (neighbors.isEmpty()) {
            // Caso improvável se halfWindow >= 0, mas como fallback retorna o pixel original
            return originalFrames[frameIndex][y][x];
        }

        Collections.sort(neighbors);
        // Retorna o elemento do meio da lista ordenada
        return neighbors.get(neighbors.size() / 2);
    }
}
