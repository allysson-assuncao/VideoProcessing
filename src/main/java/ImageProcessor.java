import java.util.Arrays;

public class ImageProcessor extends Thread {

    // Enum para definir o modo de operação da thread de forma segura.
    private enum ModoFiltro {
        SAL_E_PIMENTA,
        BORRAO_TEMPORAL
    }

    private final ModoFiltro modo;
    private final byte[][] quadroOriginal; // Pode ser o quadro com borda ou o quadro atual
    private final byte[][] quadroProcessado;
    private final int linhaInicial;
    private final int linhaFinal;

    // Variáveis para o filtro de sal e pimenta
    private final int raio;

    // Variáveis para o filtro de borrão temporal
    private final byte[][] quadroAnterior;
    private final byte[][] quadroProximo;

    // Construtor para o filtro de Sal e Pimenta (Mediana)
    public ImageProcessor(byte[][] quadroComBorda, byte[][] quadroProcessado, int linhaInicial, int linhaFinal, int raio) {
        this.modo = ModoFiltro.SAL_E_PIMENTA;
        this.quadroOriginal = quadroComBorda;
        this.quadroProcessado = quadroProcessado;
        this.linhaInicial = linhaInicial;
        this.linhaFinal = linhaFinal;
        this.raio = raio;
        this.quadroAnterior = null;
        this.quadroProximo = null;
    }

    // Construtor para o filtro de Borrão Temporal
    public ImageProcessor(byte[][] quadroAnterior, byte[][] quadroAtual, byte[][] quadroProximo, byte[][] quadroProcessado, int linhaInicial, int linhaFinal) {
        this.modo = ModoFiltro.BORRAO_TEMPORAL;
        this.quadroOriginal = quadroAtual;
        this.quadroProcessado = quadroProcessado;
        this.linhaInicial = linhaInicial;
        this.linhaFinal = linhaFinal;
        this.quadroAnterior = quadroAnterior;
        this.quadroProximo = quadroProximo;
        this.raio = 0; // Não utilizado neste modo
    }

    @Override
    public void run() {
        if (modo == ModoFiltro.SAL_E_PIMENTA) {
            aplicarFiltroMediana();
        } else if (modo == ModoFiltro.BORRAO_TEMPORAL) {
            aplicarFiltroBorraoTemporal();
        }
    }

    /**
     * Aplica o filtro da mediana. Eficaz contra ruído "sal e pimenta".
     * A mediana é mais robusta a outliers (pixels muito claros ou escuros) do que a média.
     */
    private void aplicarFiltroMediana() {
        int largura = quadroProcessado[0].length;
        int tamanhoVizinhanca = (2 * raio + 1) * (2 * raio + 1);
        int[] vizinhos = new int[tamanhoVizinhanca];

        for (int y = linhaInicial; y < linhaFinal; y++) {
            for (int x = 0; x < largura; x++) {
                int k = 0;
                // Coleta os valores dos pixels na vizinhança.
                for (int dy = -raio; dy <= raio; dy++) {
                    for (int dx = -raio; dx <= raio; dx++) {
                        // O quadroOriginal aqui é o quadro com borda, por isso o offset (y + raio).
                        vizinhos[k++] = quadroOriginal[y + raio + dy][x + raio + dx] & 0xFF;
                    }
                }
                // Ordena os valores para encontrar a mediana.
                Arrays.sort(vizinhos);
                quadroProcessado[y][x] = (byte) vizinhos[tamanhoVizinhanca / 2];
            }
        }
    }

    /**
     * Aplica um filtro "inteligente" que corrige um pixel se ele for muito diferente
     * da média das medianas das suas vizinhanças nos quadros anterior e próximo.
     */
    private void aplicarFiltroBorraoTemporal() {
        int largura = quadroProcessado[0].length;

        for (int y = linhaInicial; y < linhaFinal; y++) {
            for (int x = 0; x < largura; x++) {
                int pixelAtual = quadroOriginal[y][x] & 0xFF;

                int medianaAnterior = calcularMedianaDaVizinhanca3x3(quadroAnterior, y, x);
                int medianaProximo = calcularMedianaDaVizinhanca3x3(quadroProximo, y, x);

                // Se as medianas dos quadros vizinhos são parecidas, é provável que a região seja estável.
                if (Math.abs(medianaAnterior - medianaProximo) < 22) {
                    int mediaDasMedianas = (medianaAnterior + medianaProximo) / 2;

                    // Se o pixel atual difere muito da média das medianas, ele pode ser um erro temporal.
                    if (Math.abs(mediaDasMedianas - pixelAtual) > 15) {
                        quadroProcessado[y][x] = (byte) mediaDasMedianas; // Corrige o pixel.
                    } else {
                        quadroProcessado[y][x] = (byte) pixelAtual; // Mantém o pixel original.
                    }
                } else {
                    quadroProcessado[y][x] = (byte) pixelAtual; // Mantém se a região está em movimento.
                }
            }
        }
    }

    /**
     * Calcula o valor mediano de uma vizinhança 3x3 de um pixel em um determinado quadro.
     * Este método é mais eficiente pois não cria um novo array 2D a cada chamada.
     * @return O valor mediano (0-255).
     */
    private int calcularMedianaDaVizinhanca3x3(byte[][] quadro, int cy, int cx) {
        int[] vizinhos = new int[9];
        int k = 0;
        int altura = quadro.length;
        int largura = quadro[0].length;

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int ny = cy + dy;
                int nx = cx + dx;

                // Trata os pixels na borda do quadro para não estourar os limites do array.
                if (ny < 0) ny = 0;
                if (ny >= altura) ny = altura - 1;
                if (nx < 0) nx = 0;
                if (nx >= largura) nx = largura - 1;

                vizinhos[k++] = quadro[ny][nx] & 0xFF;
            }
        }
        Arrays.sort(vizinhos);
        return vizinhos[4]; // A mediana em um array de 9 elementos é o 5º (índice 4).
    }
}
