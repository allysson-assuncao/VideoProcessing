import org.opencv.core.Mat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * Processa uma "tira" de um frame de vídeo aplicando os filtros de correção.
 * Cada instância desta classe é uma thread que opera em uma parte da imagem.
 */
public class ImageProcessor2 extends Thread {

    private final byte[][] originalFrame; // Frame original com margem
    private final byte[][] processedFrame; // Frame de destino onde o resultado é escrito
    private final int startY;
    private final int endY;
    private final int radius;
    private final String mode; // "saltAndPepper" ou "blur"

    // Campos para remoção de borrões
    private final byte[][] previousFrame;
    private final byte[][] nextFrame;

    private final Mat flowBackward; // Novo
    private final Mat flowForward;

    /**
     * Construtor para o filtro de Sal e Pimenta.
     */
    public ImageProcessor2(byte[][] originalFrame, byte[][] processedFrame, int startY, int endY, int radius, Mat flowBackward, Mat flowForward) {
        this.originalFrame = originalFrame;
        this.processedFrame = processedFrame;
        this.startY = startY;
        this.endY = endY;
        this.radius = radius;
        this.flowBackward = flowBackward;
        this.flowForward = flowForward;
        this.mode = "saltAndPepper";
        this.previousFrame = null;
        this.nextFrame = null;
    }

    /**
     * Construtor para o filtro de Borrões.
     */
    public ImageProcessor2(byte[][] previousFrame, byte[][] originalFrame, byte[][] nextFrame, byte[][] processedFrame, int startY, int endY, Mat flowBackward, Mat flowForward) {
        this.originalFrame = originalFrame;
        this.processedFrame = processedFrame;
        this.startY = startY;
        this.endY = endY;
        this.previousFrame = previousFrame;
        this.nextFrame = nextFrame;
        this.flowBackward = flowBackward;
        this.flowForward = flowForward;
        this.mode = "blur";
        this.radius = 0; // Não usado neste modo
    }


    @Override
    public void run() {
        if ("saltAndPepper".equals(mode)) {
            applySaltAndPepperFilter();
        } else if ("motionBlur".equals(mode)) {
            applyMotionAwareBlurFilter(); // Novo método de correção
        }
        // O modo "blur" antigo pode ser removido ou mantido para comparação
    }

    /**
     * NOVA FUNÇÃO AUXILIAR
     * Obtém o valor de um pixel em coordenadas não inteiras usando interpolação bilinear.
     */
    private double getPixelInterpolado(byte[][] frame, double y, double x) {
        int height = frame.length;
        int width = frame[0].length;

        int x1 = (int) Math.floor(x);
        int y1 = (int) Math.floor(y);
        int x2 = x1 + 1;
        int y2 = y1 + 1;

        // Garante que as coordenadas não saiam dos limites da imagem
        if (x1 < 0 || x2 >= width || y1 < 0 || y2 >= height) {
            // Se estiver fora, retorna o valor do pixel mais próximo dentro da borda
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

        // Interpolação na direção X
        double r1 = v11 * (1 - dx) + v21 * dx;
        double r2 = v12 * (1 - dx) + v22 * dx;

        // Interpolação na direção Y
        return r1 * (1 - dy) + r2 * dy;
    }

    /**
     * NOVO MÉTODO DE CORREÇÃO
     * Aplica o filtro de borrão usando os vetores de Fluxo Óptico para compensar o movimento.
     */
    private void applyMotionAwareBlurFilter() {
        for (int y = startY; y < endY; y++) {
            for (int x = 0; x < width; x++) { // Supondo que width esteja disponível como campo de classe
                // Pega o vetor de movimento para o pixel (x,y)
                double[] flowB = flowBackward.get(y, x);
                double[] flowF = flowForward.get(y, x);

                if (flowB == null || flowF == null) {
                    processedFrame[y][x] = originalFrame[y][x];
                    continue;
                }

                // Calcula as coordenadas compensadas pelo movimento
                double prev_x = x + flowB[0];
                double prev_y = y + flowB[1];
                double next_x = x + flowF[0];
                double next_y = y + flowF[1];

                // Pega o valor dos pixels nessas coordenadas usando interpolação
                double v_anterior_compensado = getPixelInterpolado(previousFrame, prev_y, prev_x);
                double v_proximo_compensado = getPixelInterpolado(nextFrame, next_y, next_x);

                int v_atual = originalFrame[y][x] & 0xFF;

                // Aplica a mesma lógica de decisão, mas com os valores compensados
                // Verificamos se há consistência entre os pixels correspondentes
                if (Math.abs(v_anterior_compensado - v_proximo_compensado) < 30) {
                    double media_compensada = (v_anterior_compensado + v_proximo_compensado) / 2;

                    // E se o pixel atual é uma anomalia em relação a eles
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
}