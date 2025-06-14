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

    // Adição de alguns atributos úteis para facilitar o acesso
    private byte[][][] framesEmBytes;
    private final int numeroDeThreads;

    public VideoProcessing(String caminhoDoVideo) {
        // Carrega o vídeo direto no construtor
        this.framesEmBytes = carregarVideo(caminhoDoVideo);
        this.numeroDeThreads = Runtime.getRuntime().availableProcessors();
    }

    // Métodos de acesso as propriedades do vídeo.
    public int getTotalDeFrames() {
        return framesEmBytes.length;
    }

    public int getLargura() {
        return framesEmBytes[0][0].length;
    }

    public int getAltura() {
        return framesEmBytes[0].length;
    }

    /**
     * Aplica um filtro de mediana para remover ruído "sal e pimenta" de todos os frames.
     * O processamento é dividido entre várias threads para acelerar a execução.
     * @param raio O raio da vizinhança a ser considerada para o cálculo da mediana.
     */
    public void removerSalPimenta(int raio) {
        int altura = getAltura();
        int largura = getLargura();
        int linhasPorThread = altura / numeroDeThreads;

        byte[][][] framesProcessados = new byte[getTotalDeFrames()][altura][largura];

        for (int i = 0; i < getTotalDeFrames(); i++) {
            // Adiciona uma borda ao quadro para que o filtro possa processar os pixels da borda original.
            byte[][] quadroComBorda = adicionarBorda(framesEmBytes[i], raio);
            List<ImageProcessor> threads = new ArrayList<>();

            for (int t = 0; t < numeroDeThreads; t++) {
                int linhaInicial = t * linhasPorThread;
                int linhaFinal = (t == numeroDeThreads - 1) ? altura : linhaInicial + linhasPorThread;
                ImageProcessor thread = new ImageProcessor(quadroComBorda, framesProcessados[i], linhaInicial, linhaFinal, raio);
                threads.add(thread);
                thread.start();
            }

            aguardarThreads(threads);
        }
        this.framesEmBytes = framesProcessados;
    }

    /**
     * Remove borrões temporais comparando um quadro com seus vizinhos (anterior e próximo).
     * Ajuda a corrigir borrões que estão deslocados no tempo.
     */
    public void removerBorroesTemporais() {
        int altura = getAltura();
        int linhasPorThread = altura / numeroDeThreads;

        byte[][][] framesProcessados = new byte[getTotalDeFrames()][][];
        for(int i = 0; i < getTotalDeFrames(); i++){
            framesProcessados[i] = this.framesEmBytes[i].clone();
        }

        // O loop ignora o primeiro e os dois últimos frames, pois eles não possuem vizinhos completos para a análise.
        for (int i = 1; i < getTotalDeFrames() - 2; i++) {
            byte[][] quadroAnterior = framesEmBytes[i - 1];
            byte[][] quadroAtual = framesEmBytes[i];
            byte[][] quadroProximo = framesEmBytes[i + 2];
            byte[][] quadroDestino = framesProcessados[i];

            List<ImageProcessor> threads = new ArrayList<>();
            for (int t = 0; t < numeroDeThreads; t++) {
                int linhaInicial = t * linhasPorThread;
                int linhaFinal = (t == numeroDeThreads - 1) ? altura : linhaInicial + linhasPorThread;
                ImageProcessor thread = new ImageProcessor(quadroAnterior, quadroAtual, quadroProximo, quadroDestino, linhaInicial, linhaFinal);
                threads.add(thread);
                thread.start();
            }

            aguardarThreads(threads);
        }
        this.framesEmBytes = framesProcessados;
    }

    /**
     * Cria um novo quadro com bordas adicionais (padding) para evitar verificações de borda (index out of bounds).
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
     * Grava os frames processados em um novo arquivo de vídeo.
     * @param caminhoSaida O caminho do arquivo de vídeo a ser salvo.
     * @param fps A taxa de frames por segundo do vídeo de saída.
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

        for (byte[][] quadro : framesEmBytes) {
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
        quadroMat.release();
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

        List<byte[][]> listaDeFrames = new ArrayList<>();
        Mat quadroColorido = new Mat();
        Mat quadroCinza = new Mat();

        byte[] bufferDeLinha = new byte[largura];

        // Lê cada quadro do vídeo.
        while (captura.read(quadroColorido)) {
            // Converte o quadro de BGR (padrão do OpenCV) para escala de cinza.
            Imgproc.cvtColor(quadroColorido, quadroCinza, Imgproc.COLOR_BGR2GRAY);
            byte[][] pixelsDoQuadro = new byte[altura][largura];

            for (int y = 0; y < altura; y++) {
                quadroCinza.get(y, 0, bufferDeLinha);
                System.arraycopy(bufferDeLinha, 0, pixelsDoQuadro[y], 0, largura);
            }
            listaDeFrames.add(pixelsDoQuadro);
        }
        captura.release();
        quadroColorido.release();
        quadroCinza.release();

        return listaDeFrames.toArray(new byte[0][][]);
    }

    public static void main(String[] args) {
        String caminhoVideo = "video-3s.mp4";
        String caminhoGravar = "video-3s-corrigido.mp4";
        double fps = 24.0;

        System.out.println("Carregando o vídeo: " + caminhoVideo);
        VideoProcessing processador = new VideoProcessing(caminhoVideo);

        if (processador.getTotalDeFrames() == 0) {
            System.err.println("Nenhum quadro foi carregado. Abortando.");
            return;
        }

        System.out.printf("Processamento paralelo iniciado. Frames: %d | Resolução: %d x %d | Threads: %d \n",
            processador.getTotalDeFrames(), processador.getLargura(), processador.getAltura(), processador.numeroDeThreads);

        long tempoInicialTotal = System.currentTimeMillis();

        System.out.println("Processando: removendo borrão temporal...");
        processador.removerBorroesTemporais();

        // Aplicar o filtro de sal e pimenta algumas vezes pode melhorar o resultado.
        System.out.println("Processando: removendo ruído de sal e pimenta...");
        for (int i = 0; i < 3; i++) {
            System.out.printf(" - Processamento %d de 3\n", i + 1);
            // Raio 1 é eficaz para ruído fino.
            processador.removerSalPimenta(1);
        }

        long tempoFinalTotal = System.currentTimeMillis();
        System.out.printf("Correção do vídeo concluída em %d ms\n", (tempoFinalTotal - tempoInicialTotal));

        System.out.println("Salvando vídeo corrigido em: " + caminhoGravar);
        processador.gravarVideo(caminhoGravar, fps);
        System.out.println("Processamento finalizado com sucesso!");
    }
}
