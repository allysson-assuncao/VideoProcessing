import java.util.ArrayList;
import java.util.Arrays;
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
            applySmartBlurFilter();
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
    private int getVizinhancaMediana(byte[][] frame) {
        // Como o frame já é uma vizinhança 3x3, podemos simplificar
        int[] pixels = new int[9];
        int k = 0;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                pixels[k++] = frame[i][j] & 0xFF;
            }
        }
        Arrays.sort(pixels);
        return pixels[4]; // O elemento do meio em um array de 9 posições
    }

    private byte[][] getVizinhanca(byte[][] frame, int cy, int cx) {
        byte[][] vizinhanca = new byte[3][3];
        int height = frame.length;
        int width = frame[0].length;

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int ny = cy + dy;
                int nx = cx + dx;

                // Se o vizinho estiver fora dos limites, repete o pixel da borda (clamp to edge)
                if (ny < 0) ny = 0;
                if (ny >= height) ny = height - 1;
                if (nx < 0) nx = 0;
                if (nx >= width) nx = width - 1;

                vizinhanca[dy + 1][dx + 1] = frame[ny][nx];
            }
        }
        return vizinhanca;
    }

    /**
     * VERSÃO APRIMORADA do filtro de borrões.
     * Agora verifica a estabilidade da vizinhança antes de corrigir um pixel.
     */
    private void applySmartBlurFilter() {
        int width = processedFrame[0].length;
        for (int y = startY; y < endY; y++) {
            for (int x = 0; x < width; x++) {
                // Pega o valor do pixel atual
                int v2 = originalFrame[y][x] & 0xFF;

                // Pega a vizinhança 3x3 dos frames de referência
                byte[][] vizinhancaAnterior = getVizinhanca(previousFrame, y, x);
                byte[][] vizinhancaProximo = getVizinhanca(nextFrame, y, x);

                // Calcula a mediana (o valor "típico") de cada vizinhança
                int medianaAnterior = getVizinhancaMediana(vizinhancaAnterior);
                int medianaProximo = getVizinhancaMediana(vizinhancaProximo);

                // Condição 1: A REGIÃO é estável no tempo?
                // Verificamos se os valores típicos da vizinhança são parecidos.
                // Usamos um limiar mais rígido (ex: 25) para a região.
                if (Math.abs(medianaAnterior - medianaProximo) < 22) {

                    // A média das medianas é o nosso valor "ideal" para a região.
                    int mediaDasMedianas = (medianaAnterior + medianaProximo) / 2;

                    // Condição 2: O PIXEL ATUAL é uma anomalia em relação à sua região estável?
                    // Usamos o limiar original (ex: 40).
                    if (Math.abs(mediaDasMedianas - v2) > 13) {
                        // SIM, a região é estável e este pixel destoa muito. Corrija-o.
                        processedFrame[y][x] = (byte) mediaDasMedianas;
                    } else {
                        // NÃO, o pixel está consistente com o valor estável da região. Mantenha-o.
                        processedFrame[y][x] = (byte) v2;
                    }

                } else {
                    // A região em si é instável (provavelmente há movimento),
                    // então não fazemos a correção para evitar criar artefatos.
                    processedFrame[y][x] = (byte) v2;
                }
            }
        }
    }
}
