import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ImageProcessor extends Thread {
    private final int inicio, fim;
    private final byte[][][] original, corrigido;

    public ImageProcessor(int inicio, int fim, byte[][][] original, byte[][][] corrigido) {
        this.inicio = inicio;
        this.fim = fim;
        this.original = original;
        this.corrigido = corrigido;
    }

    @Override
    public void run() {
        for (int i = inicio; i < fim; i++) {
            processarFrame(i);
        }
    }

    private void processarFrame(int f) {
        corrigirBorroes(f);
        corrigirRuido(f);
    }

    private void removerSal(byte[][] frame) {
        int rows = frame.length;
        int cols = frame[0].length;
        byte[][] novoFrame = new byte[rows][cols];
        int LIMIAR = 30; // Limiar para considerar um pixel como outlier

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                // Coletar vizinhos 3x3
                List<Byte> vizinhos = new ArrayList<>();
                for (int di = -1; di <= 1; di++) {
                    for (int dj = -1; dj <= 1; dj++) {
                        int ni = i + di;
                        int nj = j + dj;
                        if (ni >= 0 && ni < rows && nj >= 0 && nj < cols) {
                            vizinhos.add(frame[ni][nj]);
                        }
                    }
                }
                // Calcular mediana
                Collections.sort(vizinhos);
                byte mediana = vizinhos.get(vizinhos.size() / 2);

                // Verificar se pixel é outlier
                if (Math.abs((frame[i][j] & 0xFF) - (mediana & 0xFF)) > LIMIAR) {
                    novoFrame[i][j] = mediana;
                } else {
                    novoFrame[i][j] = frame[i][j];
                }
            }
        }

        // Copiar resultado de volta para o frame original
        for (int i = 0; i < rows; i++) {
            System.arraycopy(novoFrame[i], 0, frame[i], 0, cols);
        }
    }

    private void corrigirBorroes(int f) {
        // 1. Converter array para estrutura de imagem
        byte[][] frame = original[f];
        int altura = frame.length;
        int largura = frame[0].length;

        // 2. Detectar regiões suspeitas (implementação simplificada)
        List<Circulo> borroes = detectarBorroes(frame, 30, 255, 50);

        // 3. Substituir cada região detectada
        for (Circulo circulo : borroes) {
            substituirRegiao(f, circulo.centroX, circulo.centroY, circulo.raio);
        }
    }

    private List<Circulo> detectarBorroes(byte[][] frame, int limiarPreto, int limiarBranco, int raioMinimo) {
        List<Circulo> circulos = new ArrayList<>();

        for (int y = raioMinimo; y < frame.length - raioMinimo; y++) {
            for (int x = raioMinimo; x < frame[y].length - raioMinimo; x++) {
                byte pixel = frame[y][x];

                // Verificar se é região preta/branca
                if ((pixel & 0xFF) < limiarPreto || (pixel & 0xFF) > limiarBranco) {
                    // Verificar padrão circular
                    if (verificarPadraoCircular(frame, x, y, raioMinimo)) {
                        circulos.add(new Circulo(x, y, raioMinimo));
                    }
                }
            }
        }
        return circulos;
    }

    private boolean verificarPadraoCircular(byte[][] frame, int xCentro, int yCentro, int raio) {
        int contagemEscura = 0;
        int totalAmostras = 0;
        byte limiar = 30; // Valor para considerar como preto (0-255)

        // Verificar em 8 direções cardeais
        for (int angulo = 0; angulo < 360; angulo += 45) {
            double radianos = Math.toRadians(angulo);
            int x = xCentro + (int) (raio * Math.cos(radianos));
            int y = yCentro + (int) (raio * Math.sin(radianos));

            // Verificar limites da imagem
            if (y >= 0 && y < frame.length && x >= 0 && x < frame[0].length) {
                totalAmostras++;
                if ((frame[y][x] & 0xFF) < limiar) contagemEscura++;
            }
        }

        // Se pelo menos 6 das 8 direções forem escuras
        return contagemEscura >= 6 && totalAmostras >= 6;
    }

    private void substituirRegiao(int indiceFrame, int centroX, int centroY, int raio) {
        int margem = 2; // Número de frames anteriores/posteriores para análise

        List<byte[][]> candidatos = new ArrayList<>();

        // Coletar frames candidatos (evitando limites do array)
        for (int i = Math.max(0, indiceFrame - margem);
             i <= Math.min(original.length - 1, indiceFrame + margem);
             i++) {
            if (i != indiceFrame) {
                candidatos.add(original[i]);
            }
        }

        // Processar cada pixel na região circular
        for (int y = centroY - raio; y <= centroY + raio; y++) {
            for (int x = centroX - raio; x <= centroX + raio; x++) {
                // Verificar se está dentro do círculo e dos limites da imagem
                if (y >= 0 && y < corrigido[0].length &&
                        x >= 0 && x < corrigido[0][0].length &&
                        distancia(x, y, centroX, centroY) <= raio) {

                    // Calcular novo valor baseado nos candidatos
                    corrigido[indiceFrame][y][x] = calcularValorCorrigido(candidatos, x, y);
                }
            }
        }
    }

    private double distancia(int x1, int y1, int x2, int y2) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private byte calcularValorCorrigido(List<byte[][]> candidatos, int x, int y) {
        List<Integer> valores = new ArrayList<>();

        // Coletar valores dos frames candidatos
        for (byte[][] frame : candidatos) {
            if (y < frame.length && x < frame[y].length) {
                valores.add((int) frame[y][x] & 0xFF);
            }
        }

        // Se não houver candidatos válidos, retorna o original
        if (valores.isEmpty()) return 0; // Ou tratar adequadamente

        // Calcular mediana
        Collections.sort(valores);
        int mediana;
        if (valores.size() % 2 == 0) {
            mediana = (valores.get(valores.size() / 2 - 1) + valores.get(valores.size() / 2)) / 2;
        } else {
            mediana = valores.get(valores.size() / 2);
        }

        return (byte) mediana;
    }

    private void corrigirRuido(int f) {
        byte[][] frame = corrigido[f];
        byte[][] copia = copiarFrame(frame);

        for (int y = 1; y < frame.length - 1; y++) {
            for (int x = 1; x < frame[y].length - 1; x++) {
                // Coletar vizinhança 3x3
                byte[] vizinhanca = new byte[9];
                int i = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        vizinhanca[i++] = copia[y + dy][x + dx];
                    }
                }

                // Ordenar e pegar mediana
                Arrays.sort(vizinhanca);
                byte mediana = vizinhanca[4];

                // Substituir se for outlier
                byte atual = frame[y][x];
                if (Math.abs((atual & 0xFF) - (mediana & 0xFF)) > 40) { // Threshold ajustável
                    frame[y][x] = mediana;
                }
            }
        }
    }

    private byte[][] copiarFrame(byte[][] original) {
        byte[][] copia = new byte[original.length][];
        for (int i = 0; i < original.length; i++) {
            copia[i] = Arrays.copyOf(original[i], original[i].length);
        }
        return copia;
    }


    private static class Circulo {
        int centroX, centroY, raio;

        public Circulo(int centroX, int centroY, int raio) {
            this.centroX = centroX;
            this.centroY = centroY;
            this.raio = raio;
        }
    }
}

/*

    1 - normal
    2 - borrao preto
    3 - borrao preto
    4 - borrao preto
    5 - normal
    6 - normal
    7 - normal

    1 1 1 1
    1 1 1 1
    1 1 1 1
    1 1 1 1

 */