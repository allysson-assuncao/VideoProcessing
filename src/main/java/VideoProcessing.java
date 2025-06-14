import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;

import java.util.ArrayList;
import java.util.List;

public class VideoProcessing {

    // Carrega a biblioteca OpenCV nativa ao iniciar a classe.
    static {
        nu.pattern.OpenCV.loadLocally();
    }

    private byte[][][] quadrosEmBytes;
    private final int numeroDeThreads;

    public VideoProcessing(String caminhoDoVideo) {
        this.quadrosEmBytes = carregarVideo(caminhoDoVideo);
        this.numeroDeThreads = Runtime.getRuntime().availableProcessors();
    }

    // Métodos getters para as propriedades do vídeo.
    public int getTotalDeQuadros() {
        return quadrosEmBytes.length;
    }

    public int getLargura() {
        return quadrosEmBytes[0][0].length;
    }

    public int getAltura() {
        return quadrosEmBytes[0].length;
    }

    /**
     * Aplica um filtro de mediana para remover ruído "sal e pimenta" de todos os quadros.
     * O processamento é dividido entre várias threads para acelerar a execução.
     * @param raio O raio da vizinhança a ser considerada para o cálculo da mediana.
     */
    public void removerSalPimenta(int raio) {
        int altura = getAltura();
        int largura = getLargura();
        int linhasPorThread = altura / numeroDeThreads;

        for (int i = 0; i < getTotalDeQuadros(); i++) {
            byte[][] quadroAtual = quadrosEmBytes[i];
            byte[][] quadroProcessado = new byte[altura][largura];
            // Adiciona uma borda ao quadro para que o filtro possa processar os pixels da borda original.
            byte[][] quadroComBorda = adicionarBorda(quadroAtual, raio);
            List<ImageProcessor> threads = new ArrayList<>();

            for (int t = 0; t < numeroDeThreads; t++) {
                int linhaInicial = t * linhasPorThread;
                int linhaFinal = (t == numeroDeThreads - 1) ? altura : linhaInicial + linhasPorThread;
                ImageProcessor thread = new ImageProcessor(quadroComBorda, quadroProcessado, linhaInicial, linhaFinal, raio);
                threads.add(thread);
                thread.start();
            }

            aguardarThreads(threads);
            quadrosEmBytes[i] = quadroProcessado;
        }
    }

    /**
     * Remove borrões temporais comparando um quadro com seus vizinhos (anterior e próximo).
     * Ajuda a corrigir pixels que estão deslocados no tempo.
     */
    public void removerBorroesTemporais() {
        int altura = getAltura();
        int largura = getLargura();
        int linhasPorThread = altura / numeroDeThreads;

        // O loop ignora o primeiro e os dois últimos quadros, pois eles não possuem vizinhos completos.
        for (int i = 1; i < getTotalDeQuadros() - 2; i++) {
            byte[][] quadroAnterior = quadrosEmBytes[i - 1];
            byte[][] quadroAtual = quadrosEmBytes[i];
            byte[][] quadroProximo = quadrosEmBytes[i + 2];
            byte[][] quadroProcessado = new byte[altura][largura];

            List<ImageProcessor> threads = new ArrayList<>();
            for (int t = 0; t < numeroDeThreads; t++) {
                int linhaInicial = t * linhasPorThread;
                int linhaFinal = (t == numeroDeThreads - 1) ? altura : linhaInicial + linhasPorThread;
                ImageProcessor thread = new ImageProcessor(quadroAnterior, quadroAtual, quadroProximo, quadroProcessado, linhaInicial, linhaFinal);
                threads.add(thread);
                thread.start();
            }

            aguardarThreads(threads);
            quadrosEmBytes[i] = quadroProcessado;
        }
    }

    /**
     * Cria um novo quadro com bordas adicionais (padding) para evitar problemas no processamento das bordas.
     */
    private byte[][] adicionarBorda(byte[][] quadroOriginal, int raio) {
        int altura = quadroOriginal.length;
        int largura = quadroOriginal[0].length;
        byte[][] quadroComBorda = new byte[altura + 2 * raio][largura + 2 * raio];

        for (int y = 0; y < altura; y++) {
            System.arraycopy(quadroOriginal[y], 0, quadroComBorda[y + raio], raio, largura);
        }
        return quadroComBorda;
    }

    /**
     * Aguarda a finalização de todas as threads de processamento.
     */
    private void aguardarThreads(List<ImageProcessor> threads) {
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Grava os quadros processados em um novo arquivo de vídeo.
     * @param caminhoSaida O caminho do arquivo de vídeo a ser salvo.
     * @param fps A taxa de quadros por segundo do vídeo de saída.
     */
    public void gravarVideo(String caminhoSaida, double fps) {
        int altura = getAltura();
        int largura = getLargura();

        // Define o codec do vídeo. 'avc1' corresponde ao H.264.
        int fourcc = VideoWriter.fourcc('a', 'v', 'c', '1');
        VideoWriter escritor = new VideoWriter(caminhoSaida, fourcc, fps, new Size(largura, altura), true);

        if (!escritor.isOpened()) {
            System.err.println("Erro ao abrir o escritor de vídeo para o caminho: " + caminhoSaida);
            return;
        }

        // Como o vídeo de entrada é em escala de cinza, precisamos converter cada pixel cinza (1 canal)
        // para um pixel BGR (3 canais) repetindo o valor de cinza para B, G e R.
        Mat quadroMat = new Mat(altura, largura, CvType.CV_8UC3);
        byte[] bufferDeLinha = new byte[largura * 3]; // 3 canais (BGR)

        for (byte[][] quadro : quadrosEmBytes) {
            for (int y = 0; y < altura; y++) {
                for (int x = 0; x < largura; x++) {
                    byte valorCinza = quadro[y][x];
                    int indice = x * 3;
                    bufferDeLinha[indice] = valorCinza;     // Canal Azul
                    bufferDeLinha[indice + 1] = valorCinza; // Canal Verde
                    bufferDeLinha[indice + 2] = valorCinza; // Canal Vermelho
                }
                quadroMat.put(y, 0, bufferDeLinha);
            }
            escritor.write(quadroMat);
        }
        escritor.release();
    }

    /**
     * Carrega um vídeo a partir de um caminho, converte para escala de cinza e armazena os pixels.
     */
    private byte[][][] carregarVideo(String caminhoEntrada) {
        VideoCapture captura = new VideoCapture(caminhoEntrada);
        if (!captura.isOpened()) {
            System.err.println("Erro ao abrir o vídeo: " + caminhoEntrada);
            return new byte[0][0][0];
        }

        int largura = (int) captura.get(Videoio.CAP_PROP_FRAME_WIDTH);
        int altura = (int) captura.get(Videoio.CAP_PROP_FRAME_HEIGHT);

        List<byte[][]> listaDeQuadros = new ArrayList<>();
        Mat quadroColorido = new Mat();
        Mat quadroCinza = new Mat();

        // Lê cada quadro do vídeo.
        while (captura.read(quadroColorido)) {
            // Converte o quadro de BGR (padrão do OpenCV) para escala de cinza.
            Imgproc.cvtColor(quadroColorido, quadroCinza, Imgproc.COLOR_BGR2GRAY);
            byte[][] pixelsDoQuadro = new byte[altura][largura];
            /*quadroCinza.get(0, 0, pixelsDoQuadro);*/
            quadroCinza.get(0, 0);
            listaDeQuadros.add(pixelsDoQuadro);
        }
        captura.release();

        return listaDeQuadros.toArray(new byte[0][][]);
    }

    public static void main(String[] args) {
        String caminhoVideo = "video-3s.mp4";
        String caminhoGravar = "video-3s-corrigido.mp4";
        double fps = 24.0;

        System.out.println("Carregando o vídeo: " + caminhoVideo);
        VideoProcessing processador = new VideoProcessing(caminhoVideo);

        System.out.printf("Processamento paralelo iniciado. Quadros: %d | Resolução: %d x %d | Threads: %d \n",
            processador.getTotalDeQuadros(), processador.getLargura(), processador.getAltura(), processador.numeroDeThreads);

        long tempoInicialTotal = System.currentTimeMillis();

        System.out.println("Processando: removendo borrão temporal...");
        processador.removerBorroesTemporais();

        // Aplicar o filtro de sal e pimenta algumas vezes pode melhorar o resultado.
        System.out.println("Processando: removendo ruído de sal e pimenta...");
        for (int i = 0; i < 3; i++) {
            System.out.printf(" - Passada %d de 3\n", i + 1);
            processador.removerSalPimenta(1); // Raio 1 é eficaz para ruído fino.
        }

        long tempoFinalTotal = System.currentTimeMillis();
        System.out.printf("Correção do vídeo concluída em %d ms\n", (tempoFinalTotal - tempoInicialTotal));

        System.out.println("Salvando vídeo corrigido em: " + caminhoGravar);
        processador.gravarVideo(caminhoGravar, fps);
        System.out.println("Processamento finalizado com sucesso!");
    }
}
