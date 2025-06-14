import java.util.Arrays;

public class ImageProcessor extends Thread {

    private enum ModoFiltro {
        SAL_E_PIMENTA,
        BORRAO_TEMPORAL
    }

    private final ModoFiltro modo;
    private final byte[][] quadroFonte; // Para Sal-Pimenta, é o quadro com borda. Para Borrão, é o quadro atual.
    private final byte[][] quadroDestino;
    private final int linhaInicial;
    private final int linhaFinal;

    private final int raio;
    private final byte[][] quadroAnterior;
    private final byte[][] quadroProximo;

    // Construtor para Sal e Pimenta
    public ImageProcessor(byte[][] quadroComBorda, byte[][] quadroDestino, int linhaInicial, int linhaFinal, int raio) {
        this.modo = ModoFiltro.SAL_E_PIMENTA;
        this.quadroFonte = quadroComBorda;
        this.quadroDestino = quadroDestino;
        this.linhaInicial = linhaInicial;
        this.linhaFinal = linhaFinal;
        this.raio = raio;
        this.quadroAnterior = null;
        this.quadroProximo = null;
    }

    // Construtor para Borrão Temporal
    public ImageProcessor(byte[][] quadroAnterior, byte[][] quadroAtual, byte[][] quadroProximo, byte[][] quadroDestino, int linhaInicial, int linhaFinal) {
        this.modo = ModoFiltro.BORRAO_TEMPORAL;
        this.quadroFonte = quadroAtual; // A fonte de leitura para o pixel atual é o próprio quadro atual.
        this.quadroDestino = quadroDestino;
        this.linhaInicial = linhaInicial;
        this.linhaFinal = linhaFinal;
        this.quadroAnterior = quadroAnterior;
        this.quadroProximo = quadroProximo;
        this.raio = 0;
    }

    @Override
    public void run() {
        if (modo == ModoFiltro.SAL_E_PIMENTA) {
            aplicarFiltroMediana();
        } else if (modo == ModoFiltro.BORRAO_TEMPORAL) {
            aplicarFiltroBorraoTemporal();
        }
    }

    private void aplicarFiltroMediana() {
        int largura = quadroDestino[0].length;
        int tamanhoVizinhanca = (2 * raio + 1) * (2 * raio + 1);
        int[] vizinhos = new int[tamanhoVizinhanca];

        for (int y = linhaInicial; y < linhaFinal; y++) {
            for (int x = 0; x < largura; x++) {
                int k = 0;
                for (int dy = -raio; dy <= raio; dy++) {
                    for (int dx = -raio; dx <= raio; dx++) {
                        vizinhos[k++] = quadroFonte[y + raio + dy][x + raio + dx] & 0xFF;
                    }
                }
                Arrays.sort(vizinhos);
                quadroDestino[y][x] = (byte) vizinhos[tamanhoVizinhanca / 2];
            }
        }
    }

    private void aplicarFiltroBorraoTemporal() {
        int largura = quadroDestino[0].length;

        for (int y = linhaInicial; y < linhaFinal; y++) {
            for (int x = 0; x < largura; x++) {
                int pixelAtual = quadroFonte[y][x] & 0xFF;

                int medianaAnterior = calcularMedianaDaVizinhanca3x3(quadroAnterior, y, x);
                int medianaProximo = calcularMedianaDaVizinhanca3x3(quadroProximo, y, x);

                if (Math.abs(medianaAnterior - medianaProximo) < 22) {
                    int mediaDasMedianas = (medianaAnterior + medianaProximo) / 2;
                    if (Math.abs(mediaDasMedianas - pixelAtual) > 15) {
                        quadroDestino[y][x] = (byte) mediaDasMedianas;
                    } else {
                        quadroDestino[y][x] = (byte) pixelAtual;
                    }
                } else {
                    quadroDestino[y][x] = (byte) pixelAtual;
                }
            }
        }
    }

    private int calcularMedianaDaVizinhanca3x3(byte[][] quadro, int cy, int cx) {
        int[] vizinhos = new int[9];
        int k = 0;
        int altura = quadro.length;
        int largura = quadro[0].length;

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int ny = cy + dy;
                int nx = cx + dx;

                if (ny < 0) ny = 0;
                if (ny >= altura) ny = altura - 1;
                if (nx < 0) nx = 0;
                if (nx >= largura) nx = largura - 1;

                vizinhos[k++] = quadro[ny][nx] & 0xFF;
            }
        }
        Arrays.sort(vizinhos);
        return vizinhos[4];
    }
}
