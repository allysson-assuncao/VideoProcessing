import java.util.ArrayList;
import java.util.Collections;

/**
 * Processa uma "tira" de um frame de vídeo aplicando os filtros de correção.
 * Cada instância desta classe é uma thread que opera em uma parte da imagem.
 */
public class ImageProcessor extends Thread {

    private final byte[][] originalFrame; // Frame original com margem
    private final byte[][] processedFrame; // Frame de destino onde o resultado é escrito
    private final int startY;
    private final int endY;
    private final int radius;
    private final String mode; // "saltAndPepper" ou "blur"

    // Campos para remoção de borrões
    private final byte[][] previousFrame;
    private final byte[][] nextFrame;

    /**
     * Construtor para o filtro de Sal e Pimenta.
     */
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

    /**
     * Construtor para o filtro de Borrões.
     */
    public ImageProcessor(byte[][] previousFrame, byte[][] originalFrame, byte[][] nextFrame, byte[][] processedFrame, int startY, int endY) {
        this.originalFrame = originalFrame;
        this.processedFrame = processedFrame;
        this.startY = startY;
        this.endY = endY;
        this.previousFrame = previousFrame;
        this.nextFrame = nextFrame;
        this.mode = "blur";
        this.radius = 0; // Não usado neste modo
    }


    @Override
    public void run() {
        if ("saltAndPepper".equals(mode)) {
            applySaltAndPepperFilter();
        } else if ("blur".equals(mode)) {
            applyBlurFilter();
        }
    }

    /**
     * Aplica o filtro da mediana para remover ruído "sal e pimenta".
     * Lê do frame original com margem e escreve no frame de destino.
     */
    private void applySaltAndPepperFilter() {
        int width = processedFrame[0].length;
        for (int y = startY; y < endY; y++) {
            for (int x = 0; x < width; x++) {
                ArrayList<Integer> neighbors = new ArrayList<>();
                // O acesso é feito no frame com margem, por isso (y+radius) e (x+radius)
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

    /**
     * Aplica o filtro temporal para remover borrões.
     */
    private void applyBlurFilter() {
        int width = processedFrame[0].length;
        for (int y = startY; y < endY; y++) {
            for (int x = 0; x < width; x++) {
                // Acesso direto aos frames, pois não há vizinhança espacial aqui
                int v1 = previousFrame[y][x] & 0xFF;
                int v2 = originalFrame[y][x] & 0xFF;
                int v3 = nextFrame[y][x] & 0xFF;

                int media = (v1 + v3) / 2;
                if (Math.abs(v1 - v3) < 50 && Math.abs(media - v2) > 40) {
                    processedFrame[y][x] = (byte) media;
                } else {
                    processedFrame[y][x] = (byte) v2;
                }
            }
        }
    }
}