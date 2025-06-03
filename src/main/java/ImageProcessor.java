import java.util.ArrayList;
import java.util.Collections;

class ImageProcessor extends Thread {
    private final int startFrame;
    private final int endFrame;
    private final byte[][][] originalFrames; // Para leitura (especialmente para borrão temporal)
    private final byte[][][] processedFrames; // Para escrita e leitura (sal&pimenta lê o resultado do borrão)
    private final int numPassesSalPimenta;
    private final int raioSalPimenta;

    public ImageProcessor(int startFrame, int endFrame, byte[][][] originalFrames, byte[][][] processedFrames, int numPassesSalPimenta, int raioSalPimenta) {
        this.startFrame = startFrame;
        this.endFrame = endFrame;
        this.originalFrames = originalFrames;
        this.processedFrames = processedFrames;
        this.numPassesSalPimenta = numPassesSalPimenta;
        this.raioSalPimenta = raioSalPimenta;
    }

    @Override
    public void run() {
        if (originalFrames.length == 0) return;

        // Etapa 1: Remover Borrões Temporais
        // Lê de originalFrames e escreve em processedFrames
        // Os frames nas extremidades do vídeo (0, N-1, N-2) não são alterados por este filtro.
        // A cópia inicial (deepCopy no main) garante que eles já estão corretos em processedFrames.
        applyTemporalBlur();

        // Etapa 2: Remover Ruído Sal e Pimenta (iterativamente)
        // Lê de processedFrames (que agora contém o resultado do borrão) e escreve de volta em processedFrames
        for (int i = 0; i < numPassesSalPimenta; i++) {
            System.out.println("Thread " + getId() + " aplicando sal e pimenta, passe " + (i + 1));
            applySaltAndPepper();
        }
    }

    private void applyTemporalBlur() {
        int totalVideoFrames = originalFrames.length;
        if (totalVideoFrames < 3) return; // Não é possível aplicar o filtro com menos de 3 frames (f-1, f, f+2)

        int height = originalFrames[0].length;
        int width = originalFrames[0][0].length;

        // Itera apenas sobre os frames que esta thread é responsável
        for (int f = startFrame; f < endFrame; f++) {
            // O filtro original é: for (int f = 1; f < qFrames - 2; f++)
            // Portanto, só processamos se o frame 'f' permitir acesso a f-1 e f+2 dentro dos limites do vídeo original.
            if (f > 0 && f < totalVideoFrames - 2) {
                byte[][] anterior = originalFrames[f - 1];
                byte[][] atualOriginal = originalFrames[f]; // Ler o 'atual' sempre do original para esta lógica de borrão
                byte[][] proximo = originalFrames[f + 2];
                byte[][] novoFrame = new byte[height][width];

                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int v1 = anterior[y][x] & 0xFF;
                        int v2 = atualOriginal[y][x] & 0xFF; // Pixel original do frame atual
                        int v3 = proximo[y][x] & 0xFF;

                        int media = (v1 + v3) / 2;
                        if (Math.abs(v1 - v3) < 50 && Math.abs(media - v2) > 40) {
                            novoFrame[y][x] = (byte) media;
                        } else {
                            novoFrame[y][x] = atualOriginal[y][x]; // Mantém o pixel original do frame atual
                        }
                    }
                }
                processedFrames[f] = novoFrame; // Escreve o resultado no cubo de processamento
            }
            // Se f == 0, ou f >= totalVideoFrames - 2, este filtro não se aplica.
            // Como processedFrames é uma cópia de originalFrames inicialmente,
            // esses frames já estão corretos em processedFrames.
        }
    }

    private void applySaltAndPepper() {
        if (processedFrames.length == 0) return;
        int height = processedFrames[0].length;
        int width = processedFrames[0][0].length;

        // Itera apenas sobre os frames que esta thread é responsável
        for (int f = startFrame; f < endFrame; f++) {
            byte[][] frameAtual = processedFrames[f]; // Lê da versão já processada (pelo borrão ou S&P anterior)
            byte[][] novoFrame = new byte[height][width];
            ArrayList<Integer> vizinhos = new ArrayList<>();

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    vizinhos.clear();
                    for (int dy = -raioSalPimenta; dy <= raioSalPimenta; dy++) {
                        for (int dx = -raioSalPimenta; dx <= raioSalPimenta; dx++) {
                            int ny = y + dy;
                            int nx = x + dx;
                            if (ny >= 0 && ny < height && nx >= 0 && nx < width) {
                                vizinhos.add(frameAtual[ny][nx] & 0xFF);
                            }
                        }
                    }
                    Collections.sort(vizinhos);
                    novoFrame[y][x] = (byte) (int) vizinhos.get(vizinhos.size() / 2);
                }
            }
            processedFrames[f] = novoFrame; // Atualiza o frame no cubo de processamento
        }
    }
}
